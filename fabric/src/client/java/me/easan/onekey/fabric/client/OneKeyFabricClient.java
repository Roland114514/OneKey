package me.easan.onekey.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import me.easan.onekey.common.net.SortRequest;
import me.easan.onekey.fabric.OneKeyFabric;
import me.easan.onekey.fabric.client.mixin.AbstractContainerScreenAccessor;
import me.easan.onekey.fabric.net.SortRequestPayload;
import me.easan.onekey.fabric.net.SortResultPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;

/**
 * Fabric 客户端入口：注册热键、监听容器内按键事件、发送整理请求并接收回包。
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
        // 保持默认整理键为 R，其它为 V / B / K
        sortKey = register("key.onekey.sort", InputConstants.KEY_R);
        quickStackKey = register("key.onekey.quick_stack", InputConstants.KEY_V);
        quickStashKey = register("key.onekey.quick_stash", InputConstants.KEY_B);
        settingsKey = register("key.onekey.settings", InputConstants.KEY_K);

        // 1. 注册客户端整理回包接收器
        ClientPlayNetworking.registerGlobalReceiver(SortResultPayload.TYPE, (payload, context) -> {
            ClientSortingHandler.handle(payload.result(), context);
        });

        // 2. 监听容器界面内的按键事件（按 R 整理容器或主背包）
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof AbstractContainerScreen<?> containerScreen) {
                ScreenKeyboardEvents.allowKeyPress(screen).register((scr, event) -> {
                    if (sortKey.matches(event)) {
                        // 正在文本输入框内打字时（如搜索框、配方书），不拦截 R 键
                        if (scr.getFocused() instanceof EditBox editBox && editBox.canConsumeInput()) {
                            return true;
                        }
                        triggerScreenSort(client, containerScreen);
                        return false; // 拦截并消耗按键事件
                    }
                    return true;
                });
            }
        });

        // 3. 客户端 Tick 事件：支持未打开 GUI 时（例如手持物品在世界走动）按 R 整理主背包
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.gui != null && client.gui.screen() == null && client.player != null) {
                while (sortKey.consumeClick()) {
                    triggerSort(client, client.player.inventoryMenu.containerId, SortRequest.MODE_PLAYER);
                }
            }
            while (quickStackKey.consumeClick()) {
                OneKeyFabric.LOGGER.info("[OneKey] 快速堆叠：待实现（M5）");
            }
            while (quickStashKey.consumeClick()) {
                OneKeyFabric.LOGGER.info("[OneKey] 一键存入：待实现（M5）");
            }
            while (settingsKey.consumeClick()) {
                OneKeyFabric.LOGGER.info("[OneKey] 设置界面：待实现（M4）");
            }
        });

        OneKeyFabric.LOGGER.info("[OneKey] 客户端已初始化，注册 4 个热键（默认 R / V / B / K）");
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
