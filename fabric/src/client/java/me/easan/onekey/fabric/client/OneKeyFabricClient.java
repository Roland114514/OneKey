package me.easan.onekey.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import me.easan.onekey.common.action.ActionId;
import me.easan.onekey.common.action.ActionRegistry;
import me.easan.onekey.common.net.SortRequest;
import me.easan.onekey.fabric.OneKeyFabric;
import me.easan.onekey.fabric.client.gui.OneKeySettingsScreen;
import me.easan.onekey.fabric.client.mixin.AbstractContainerScreenAccessor;
import me.easan.onekey.fabric.net.RequestSlotLocksPayload;
import me.easan.onekey.fabric.net.SortRequestPayload;
import me.easan.onekey.fabric.net.SortResultPayload;
import me.easan.onekey.fabric.net.SyncSlotLocksPayload;
import me.easan.onekey.fabric.net.ToggleSlotLockPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;

/**
 * Fabric 客户端入口：注册热键、监听容器内按键与鼠标锁定事件、发送整理请求并接收回包。
 */
public final class OneKeyFabricClient implements ClientModInitializer {

    /** 26.3 的按键分类是记录类型，需要先注册到原版分类列表（重复注册会抛异常，故只注册一次）。 */
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(OneKeyFabric.MOD_ID, "keys"));

    private static KeyMapping sortKey;
    private static KeyMapping quickStackKey;
    private static KeyMapping quickStashKey;
    private static KeyMapping settingsKey;

    @Override
    public void onInitializeClient() {
        // 基于 ActionRegistry 统一管理默认热键映射
        sortKey = register(ActionRegistry.get(ActionId.SORT).orElseThrow().translationKey(), InputConstants.KEY_R);
        quickStackKey = register(ActionRegistry.get(ActionId.QUICK_STACK).orElseThrow().translationKey(), InputConstants.KEY_V);
        quickStashKey = register(ActionRegistry.get(ActionId.QUICK_STASH).orElseThrow().translationKey(), InputConstants.KEY_B);
        settingsKey = register(ActionRegistry.get(ActionId.SETTINGS).orElseThrow().translationKey(), InputConstants.KEY_K);

        // 1. 注册客户端整理结果与锁定槽位网络接收器
        ClientPlayNetworking.registerGlobalReceiver(SortResultPayload.TYPE, (payload, context) -> {
            ClientSortingHandler.handle(payload.result(), context);
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncSlotLocksPayload.TYPE, (payload, context) -> {
            ClientLockedSlots.setLocks(payload.containerKey(), payload.lockedSlots());
        });

        // 2. 监听容器界面内的按键与鼠标事件
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof AbstractContainerScreen<?> containerScreen) {
                // 打开容器界面时向服务端请求锁定槽位
                if (ClientPlayNetworking.canSend(RequestSlotLocksPayload.TYPE)) {
                    ClientPlayNetworking.send(new RequestSlotLocksPayload("player_inventory"));
                    if (containerScreen.getMenu() != null) {
                        String menuKey = ClientLockedSlots.getMenuTypeKey(containerScreen.getMenu());
                        if (!"player_inventory".equals(menuKey)) {
                            ClientPlayNetworking.send(new RequestSlotLocksPayload(menuKey));
                        }
                    }
                }

                // 键盘快捷键监听
                ScreenKeyboardEvents.allowKeyPress(screen).register((scr, event) -> {
                    if (sortKey.matches(event)) {
                        // 正在文本输入框内打字时（如搜索框、配方书），不拦截 R 键
                        if (scr.getFocused() instanceof EditBox editBox && editBox.canConsumeInput()) {
                            return true;
                        }
                        triggerScreenSort(client, containerScreen);
                        return false;
                    }
                    if (settingsKey.matches(event)) {
                        if (scr.getFocused() instanceof EditBox editBox && editBox.canConsumeInput()) {
                            return true;
                        }
                        client.setScreenAndShow(new OneKeySettingsScreen(screen));
                        return false;
                    }
                    return true;
                });

                // 鼠标 Alt + 左键 槽位锁定切换
                ScreenMouseEvents.allowMouseClick(screen).register((scr, event) -> {
                    if (event.hasAltDown() && event.button() == 0) {
                        if (containerScreen instanceof AbstractContainerScreenAccessor accessor) {
                            Slot hovered = accessor.onekey$getHoveredSlot();
                            if (hovered != null) {
                                toggleSlotLock(client, containerScreen, hovered);
                                return false; // 拦截事件，避免原版拾取/移动物品
                            }
                        }
                    }
                    return true;
                });
            }
        });

        // 3. 客户端 Tick 事件：未开 GUI 时按 R 整理主背包、按 K 打开设置界面
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.gui != null && client.gui.screen() == null && client.player != null) {
                while (sortKey.consumeClick()) {
                    triggerSort(client, client.player.inventoryMenu.containerId, SortRequest.MODE_PLAYER);
                }
                while (settingsKey.consumeClick()) {
                    client.setScreenAndShow(new OneKeySettingsScreen(null));
                }
            }
            while (quickStackKey.consumeClick()) {
                OneKeyFabric.LOGGER.info("[OneKey] 快速堆叠：待实现（M5）");
            }
            while (quickStashKey.consumeClick()) {
                OneKeyFabric.LOGGER.info("[OneKey] 一键存入：待实现（M5）");
            }
        });

        OneKeyFabric.LOGGER.info("[OneKey] 客户端已初始化，注册 4 个热键（默认 R / V / B / K）");
    }

    private static void toggleSlotLock(Minecraft client, AbstractContainerScreen<?> screen, Slot slot) {
        if (client.player == null) {
            return;
        }

        String containerKey;
        int slotIndex;
        if (slot.container == client.player.getInventory()) {
            containerKey = "player_inventory";
            slotIndex = slot.getContainerSlot();
        } else {
            containerKey = ClientLockedSlots.getMenuTypeKey(screen.getMenu());
            slotIndex = slot.index;
        }

        // 本地立即切换锁定，获得即时视觉响应
        boolean nowLocked = ClientLockedSlots.toggle(containerKey, slotIndex);

        // 播放轻微点击反馈音效（锁定音调偏高，解锁音调偏低）
        client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, nowLocked ? 1.4F : 0.9F));

        // 发送给服务端持久化到存档
        if (ClientPlayNetworking.canSend(ToggleSlotLockPayload.TYPE)) {
            ClientPlayNetworking.send(new ToggleSlotLockPayload(containerKey, slotIndex));
        }
    }

    private static void triggerScreenSort(Minecraft client, AbstractContainerScreen<?> screen) {
        if (client.player == null) {
            return;
        }

        int syncId = screen.getMenu().containerId;
        int mode = SortRequest.MODE_CONTAINER;

        if (screen instanceof InventoryScreen || screen.getMenu() instanceof InventoryMenu) {
            mode = SortRequest.MODE_PLAYER;
        } else if (screen instanceof AbstractContainerScreenAccessor accessor) {
            Slot hovered = accessor.onekey$getHoveredSlot();
            if (hovered != null && hovered.container == client.player.getInventory()) {
                mode = SortRequest.MODE_PLAYER;
            }
        }

        triggerSort(client, syncId, mode);
    }

    private static void triggerSort(Minecraft client, int syncId, int mode) {
        if (client.player == null) {
            return;
        }

        if (!OneKeyFabric.config().sort.enabled) {
            return;
        }

        // 服务端兼容性检测：若服务端未安装 OneKey，平滑降级提示，不崩溃、不本地强改
        if (!ClientPlayNetworking.canSend(SortRequestPayload.TYPE)) {
            client.player.sendOverlayMessage(
                    Component.translatable("onekey.sort.server_not_installed"));
            return;
        }

        ClientPlayNetworking.send(new SortRequestPayload(new SortRequest(syncId, mode)));
    }

    private static KeyMapping register(String translationKey, int defaultKey) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(
                translationKey,
                InputConstants.Type.KEYBOARD,
                defaultKey,
                CATEGORY));
    }
}
