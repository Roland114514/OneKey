package me.easan.onekey.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import me.easan.onekey.common.action.ActionId;
import me.easan.onekey.common.action.ActionRegistry;
import me.easan.onekey.common.net.QuickStackRequest;
import me.easan.onekey.common.net.SortRequest;
import me.easan.onekey.common.net.TargetPos;
import me.easan.onekey.common.sort.StackCondenser;
import me.easan.onekey.common.inventory.InventorySlots;
import me.easan.onekey.fabric.util.PlayerSlotFilter;
import me.easan.onekey.common.sort.Sorter;
import me.easan.onekey.common.sort.StackView;
import me.easan.onekey.fabric.OneKeyFabric;
import me.easan.onekey.fabric.client.compat.ModConflictDetector;
import me.easan.onekey.fabric.client.gui.OneKeySettingsScreen;
import me.easan.onekey.fabric.client.mixin.AbstractContainerScreenAccessor;
import me.easan.onekey.fabric.net.QuickStackRequestPayload;
import me.easan.onekey.fabric.net.QuickStackResultPayload;
import me.easan.onekey.fabric.net.RequestSlotLocksPayload;
import me.easan.onekey.fabric.net.ScrollItemTransferPayload;
import me.easan.onekey.fabric.net.SortRequestPayload;
import me.easan.onekey.fabric.net.SortResultPayload;
import me.easan.onekey.fabric.net.SyncSlotLocksPayload;
import me.easan.onekey.fabric.net.ToggleSlotLockPayload;
import me.easan.onekey.fabric.server.ServerSortingHandler;
import me.easan.onekey.fabric.util.ItemConversion;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.item.ItemStack;

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

    private static long lastShiftClickTime = 0L;
    private static int lastShiftClickSlot = -1;
    private static ItemStack lastShiftClickItem = ItemStack.EMPTY;
    private static long lastScrollTime = 0L;
    private static long lastClientSortTime = 0L;
    private static int lastDraggedSlotIndex = -1;
    private static int lastRmbSlotIndex = -1;
    private static boolean rmbDragActive = false;
    private static boolean conflictWarned = false;

    private static void resetGestureState() {
        lastDraggedSlotIndex = -1;
        lastRmbSlotIndex = -1;
        rmbDragActive = false;
    }

    private static boolean isLeftButton(int button) {
        return button == InputConstants.MOUSE_BUTTON_LEFT || button == 0;
    }

    private static boolean isRightButton(int button) {
        return button == InputConstants.MOUSE_BUTTON_RIGHT || button == 1;
    }

    @Override
    public void onInitializeClient() {
        // 默认键位来自 config/onekey.json 的 hotkeys 段（非法键名自动回退到内置默认值），
        // 真正的按键重绑仍走原版「选项 → 控制 → 按键绑定」，此处只负责首次注册的默认值。
        var hotkeys = OneKeyFabric.config().hotkeys;
        sortKey = register(ActionRegistry.get(ActionId.SORT).orElseThrow().translationKey(),
                configuredKey(hotkeys.sort, InputConstants.KEY_R));
        quickStackKey = register(ActionRegistry.get(ActionId.QUICK_STACK).orElseThrow().translationKey(),
                configuredKey(hotkeys.quickStack, InputConstants.KEY_V));
        quickStashKey = register(ActionRegistry.get(ActionId.QUICK_STASH).orElseThrow().translationKey(),
                configuredKey(hotkeys.quickStash, InputConstants.KEY_B));
        settingsKey = register(ActionRegistry.get(ActionId.SETTINGS).orElseThrow().translationKey(),
                configuredKey(hotkeys.settings, InputConstants.KEY_K));

        // 1. 注册客户端整理结果与锁定槽位网络接收器
        ClientPlayNetworking.registerGlobalReceiver(SortResultPayload.TYPE, (payload, context) -> {
            ClientSortingHandler.handle(payload.result(), context);
        });

        ClientPlayNetworking.registerGlobalReceiver(QuickStackResultPayload.TYPE, (payload, context) -> {
            ClientQuickStackHandler.handle(payload.result(), context);
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncSlotLocksPayload.TYPE, (payload, context) -> {
            ClientLockedSlots.setLocks(payload.containerKey(), payload.lockedSlots());
        });

        // 进出世界或断开连接时，清空客户端锁存与物品分类缓存，防止跨存档泄漏与陈旧数据
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientLockedSlots.clear();
            ItemConversion.clearCache();
            resetGestureState();
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ClientLockedSlots.clear();
            ItemConversion.clearCache();
            resetGestureState();
        });

        // 2. 监听容器界面内的按键与鼠标事件
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            resetGestureState();
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
                        // 正在文本输入框内打字时（如搜索框、配方书），不拦截热键
                        if (scr.getFocused() instanceof EditBox editBox && editBox.canConsumeInput()) {
                            return true;
                        }
                        triggerScreenSort(client, containerScreen);
                        return false;
                    }
                    if (quickStackKey.matches(event)) {
                        if (scr.getFocused() instanceof EditBox editBox && editBox.canConsumeInput()) {
                            return true;
                        }
                        triggerQuickStack(client, false);
                        return false;
                    }
                    if (quickStashKey.matches(event)) {
                        if (scr.getFocused() instanceof EditBox editBox && editBox.canConsumeInput()) {
                            return true;
                        }
                        triggerQuickStack(client, true);
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

                // 鼠标滚轮微调单件物品转移与持物微调监听
                ScreenMouseEvents.allowMouseScroll(screen).register((scr, mouseX, mouseY, horizontalAmount, verticalAmount) -> {
                    if (verticalAmount == 0) {
                        return true;
                    }
                    if (client.player == null || containerScreen.getMenu() == null) {
                        return true;
                    }
                    if (screen instanceof CreativeModeInventoryScreen) {
                        return true;
                    }
                    if (!(containerScreen instanceof AbstractContainerScreenAccessor accessor)) {
                        return true;
                    }

                    boolean isUp = verticalAmount > 0;
                    if (OneKeyFabric.config().gestures.invertWheel) {
                        isUp = !isUp;
                    }

                    ItemStack carried = containerScreen.getMenu().getCarried();
                    // 光标持物时的滚轮微调：向下放入 1 件
                    if (!carried.isEmpty()) {
                        if (OneKeyFabric.config().gestures.wheelCarriedHandling && !isUp) {
                            Slot hovered = accessor.onekey$findSlotAt(mouseX, mouseY);
                            if (hovered == null) {
                                hovered = accessor.onekey$getHoveredSlot();
                            }
                            if (hovered != null && canPlaceRmb(client.player, containerScreen, hovered, carried)) {
                                accessor.onekey$slotClicked(hovered, hovered.index, 1, ContainerInput.PICKUP);
                                client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2F));
                                return false;
                            }
                        }
                        return true;
                    }

                    if (!ServerSortingHandler.isMenuSortable(ServerSortingHandler.menuTypeOrNull(containerScreen.getMenu()))) {
                        return true;
                    }

                    Slot hovered = accessor.onekey$findSlotAt(mouseX, mouseY);
                    if (hovered == null) {
                        hovered = accessor.onekey$getHoveredSlot();
                    }
                    if (hovered == null || hovered.isFake() || !hovered.isActive() || hovered instanceof ResultSlot) {
                        return true;
                    }
                    if (hovered.container instanceof CraftingContainer || hovered.getItem().isEmpty()) {
                        return true;
                    }

                    boolean isPlayer = hovered.container == client.player.getInventory();
                    if (isPlayer) {
                        int slotIdx = hovered.getContainerSlot();
                        if (!PlayerSlotFilter.isPlainPlayerInventorySlot(client.player, hovered,
                                OneKeyFabric.config().gestures.includeHotbar)) {
                            return true; // 盔甲/副手永不参与；快捷栏由「手势影响快捷栏」开关决定
                        }
                        if (ClientLockedSlots.isLocked("player_inventory", slotIdx)) {
                            return true;
                        }
                    } else {
                        String menuKey = ClientLockedSlots.getMenuTypeKey(containerScreen.getMenu());
                        if (ClientLockedSlots.isLocked(menuKey, hovered.index)) {
                            return true;
                        }
                    }

                    if (!ClientPlayNetworking.canSend(ScrollItemTransferPayload.TYPE)) {
                        return true;
                    }

                    long now = System.currentTimeMillis();
                    if (now - lastScrollTime < 40L) {
                        return false;
                    }
                    lastScrollTime = now;

                    ClientPlayNetworking.send(new ScrollItemTransferPayload(containerScreen.getMenu().containerId, hovered.index, isUp));
                    client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, isUp ? 1.4F : 1.1F));
                    return false;
                });

                // 鼠标点击事件：Alt + 左键锁定；Shift + 双击同类批量转移；记录拖拽起始槽位
                ScreenMouseEvents.allowMouseClick(screen).register((scr, event) -> {
                    if (screen instanceof CreativeModeInventoryScreen) {
                        return true;
                    }
                    if (containerScreen instanceof AbstractContainerScreenAccessor accessor) {
                        Slot hovered = accessor.onekey$findSlotAt(event.x(), event.y());
                        if (hovered == null) {
                            hovered = accessor.onekey$getHoveredSlot();
                        }

                        // Alt + 左键：锁定/解锁槽位
                        if (event.hasAltDown() && isLeftButton(event.button())) {
                            if (hovered != null) {
                                toggleSlotLock(client, containerScreen, hovered);
                                return false; // 拦截事件，避免原版拾取/移动物品
                            }
                        }

                        // Shift + 左键：双击同类批量快速移动，同时记录拖拽起始槽位
                        if (event.hasShiftDown() && isLeftButton(event.button())) {
                            lastDraggedSlotIndex = (hovered != null ? hovered.index : -1);
                            if (hovered != null && !hovered.isFake() && hovered.isActive() && !(hovered instanceof ResultSlot)
                                    && !(hovered.container instanceof CraftingContainer)) {
                                boolean isPlayer = hovered.container == client.player.getInventory();
                                boolean lockedOrHotbar = false;
                                if (isPlayer) {
                                    int slotIdx = hovered.getContainerSlot();
                                    lockedOrHotbar = !PlayerSlotFilter.isPlainPlayerInventorySlot(client.player, hovered,
                                            OneKeyFabric.config().gestures.includeHotbar)
                                            || ClientLockedSlots.isLocked("player_inventory", slotIdx);
                                } else {
                                    String menuKey = ClientLockedSlots.getMenuTypeKey(containerScreen.getMenu());
                                    lockedOrHotbar = ClientLockedSlots.isLocked(menuKey, hovered.index);
                                }

                                if (!lockedOrHotbar) {
                                    long now = System.currentTimeMillis();
                                    if (now - lastShiftClickTime < 300L && lastShiftClickSlot == hovered.index && !lastShiftClickItem.isEmpty()) {
                                        ItemStack targetItem = lastShiftClickItem.copy();
                                        lastShiftClickTime = 0L;
                                        lastShiftClickSlot = -1;
                                        lastShiftClickItem = ItemStack.EMPTY;
                                        transferAllMatching(client, containerScreen, accessor, hovered, targetItem);
                                        return false;
                                    } else {
                                        ItemStack current = hovered.getItem();
                                        if (!current.isEmpty()) {
                                            lastShiftClickTime = now;
                                            lastShiftClickSlot = hovered.index;
                                            lastShiftClickItem = current.copy();
                                        } else {
                                            lastShiftClickTime = 0L;
                                            lastShiftClickSlot = -1;
                                            lastShiftClickItem = ItemStack.EMPTY;
                                        }
                                    }
                                } else {
                                    lastShiftClickTime = 0L;
                                    lastShiftClickSlot = -1;
                                    lastShiftClickItem = ItemStack.EMPTY;
                                }
                            }
                        } else {
                            lastShiftClickTime = 0L;
                            lastShiftClickSlot = -1;
                            lastShiftClickItem = ItemStack.EMPTY;
                        }

                        // 右键点击时记录起手槽位与初始状态，保持原版完整状态由拖拽状态机按需接管
                        if (isRightButton(event.button())) {
                            lastRmbSlotIndex = (hovered != null ? hovered.index : -1);
                            rmbDragActive = false;
                        }
                    }

                    return true;
                });

                // 鼠标拖拽事件：Shift + 左键扫掠转移；右键画线重复放置
                ScreenMouseEvents.allowMouseDrag(screen).register((scr, event, deltaX, deltaY) -> {
                    if (screen instanceof CreativeModeInventoryScreen) {
                        return true;
                    }
                    if (client.player == null || containerScreen.getMenu() == null) {
                        return true;
                    }
                    if (!(containerScreen instanceof AbstractContainerScreenAccessor accessor)) {
                        return true;
                    }

                    // 1. Shift + 左键拖拽滑动扫掠连续转移（LMB Drag Sweep）
                    // 仅在光标无持物时允许扫掠，避免与持物放置冲突
                    if (OneKeyFabric.config().gestures.lmbDragSweep && isLeftButton(event.button()) && event.hasShiftDown()
                            && containerScreen.getMenu().getCarried().isEmpty()) {
                        Slot hovered = accessor.onekey$findSlotAt(event.x(), event.y());
                        if (hovered == null) {
                            hovered = accessor.onekey$getHoveredSlot();
                        }
                        // 关键防抖：hovered == null 严禁重置 lastDraggedSlotIndex，防止槽位边缘 2px 间隙触发重复点击发包风暴
                        if (hovered != null && hovered.index != lastDraggedSlotIndex) {
                            lastDraggedSlotIndex = hovered.index;
                            if (isSafeForQuickMove(client.player, containerScreen, hovered)) {
                                accessor.onekey$slotClicked(hovered, hovered.index, 0, ContainerInput.QUICK_MOVE);
                            }
                        }
                        return true;
                    }

                    // 2. 右键划动重复投放（RMB Drag Repeated Placement）
                    if (OneKeyFabric.config().gestures.rmbDragRepeat && isRightButton(event.button()) && !event.hasShiftDown()) {
                        ItemStack carried = containerScreen.getMenu().getCarried();
                        if (carried.isEmpty()) {
                            return true;
                        }

                        Slot hovered = accessor.onekey$findSlotAt(event.x(), event.y());
                        if (hovered == null) {
                            hovered = accessor.onekey$getHoveredSlot();
                        }

                        // 惰性激活：鼠标未移出起手槽位或未进入新槽位前，放行给原版处理（保障单点容错）
                        if (!rmbDragActive) {
                            if (hovered != null && hovered.index != lastRmbSlotIndex) {
                                rmbDragActive = true;
                                // 起手槽补偿：若起手槽在有效索引内且可放置，补充投放起手槽的 1 件物品
                                if (lastRmbSlotIndex >= 0 && lastRmbSlotIndex < containerScreen.getMenu().slots.size()) {
                                    Slot startSlot = containerScreen.getMenu().slots.get(lastRmbSlotIndex);
                                    if (canPlaceRmb(client.player, containerScreen, startSlot, carried)) {
                                        accessor.onekey$slotClicked(startSlot, startSlot.index, 1, ContainerInput.PICKUP);
                                        carried = containerScreen.getMenu().getCarried();
                                    }
                                }
                                // 清空并流产原版 QuickCraft
                                accessor.onekey$getQuickCraftSlots().clear();
                                accessor.onekey$setIsQuickCrafting(false);

                                // 为新滑入的槽位投放 1 件物品
                                lastRmbSlotIndex = hovered.index;
                                if (!carried.isEmpty() && canPlaceRmb(client.player, containerScreen, hovered, carried)) {
                                    accessor.onekey$slotClicked(hovered, hovered.index, 1, ContainerInput.PICKUP);
                                }
                                return false; // 拦截拖拽，阻止原版 quickCraft 累积
                            }
                        } else {
                            // 已处于划动投放状态：持续流产原版 QuickCraft
                            accessor.onekey$getQuickCraftSlots().clear();
                            accessor.onekey$setIsQuickCrafting(false);

                            // 槽位边缘防抖：hovered == null 严禁重置 lastRmbSlotIndex
                            if (hovered != null && hovered.index != lastRmbSlotIndex) {
                                lastRmbSlotIndex = hovered.index;
                                if (canPlaceRmb(client.player, containerScreen, hovered, carried)) {
                                    accessor.onekey$slotClicked(hovered, hovered.index, 1, ContainerInput.PICKUP);
                                }
                            }
                            return false; // 拦截拖拽
                        }
                    }

                    return true;
                });

                // 鼠标释放事件：清理拖拽与重复放置状态机
                ScreenMouseEvents.allowMouseRelease(screen).register((scr, event) -> {
                    if (isLeftButton(event.button())) {
                        lastDraggedSlotIndex = -1;
                    }
                    if (isRightButton(event.button())) {
                        lastRmbSlotIndex = -1;
                        if (rmbDragActive) {
                            rmbDragActive = false;
                            if (containerScreen instanceof AbstractContainerScreenAccessor accessor) {
                                accessor.onekey$getQuickCraftSlots().clear();
                                accessor.onekey$setIsQuickCrafting(false);
                            }
                            return false; // 彻底拦截释放事件，阻止原版 quickCraft 结算或多余点击
                        }
                    }
                    return true;
                });
            }
        });

        // 3. 客户端 Tick 事件：未开 GUI 时按热键触发整理、快速堆叠、一键存入与设置界面
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!conflictWarned && client.player != null) {
                conflictWarned = true;
                List<String> conflicts = ModConflictDetector.detectConflicts();
                if (!conflicts.isEmpty()) {
                    OneKeyFabric.LOGGER.warn("[OneKey] 检测到共存库存模组: {}", conflicts);
                    client.player.sendSystemMessage(
                            Component.translatable("onekey.compat.conflict_detected", String.join(", ", conflicts)));
                }
            }

            if (client.gui != null && client.gui.screen() == null) {
                resetGestureState();
            }

            if (client.gui != null && client.gui.screen() == null && client.player != null) {
                while (sortKey.consumeClick()) {
                    triggerSort(client, client.player.inventoryMenu.containerId, SortRequest.MODE_PLAYER);
                }
                while (quickStackKey.consumeClick()) {
                    triggerQuickStack(client, false);
                }
                while (quickStashKey.consumeClick()) {
                    triggerQuickStack(client, true);
                }
                while (settingsKey.consumeClick()) {
                    client.setScreenAndShow(new OneKeySettingsScreen(null));
                }
            }
        });


        OneKeyFabric.LOGGER.info("[OneKey] 客户端已初始化，注册 4 个热键（默认 R / V / B / K）与手势系统");
    }

    private static boolean isSlotLockable(Minecraft client, AbstractContainerScreen<?> screen, Slot slot) {
        if (client.player == null || slot == null || screen == null || screen.getMenu() == null) {
            return false;
        }
        if (slot.isFake() || !slot.isActive() || slot instanceof ResultSlot) {
            return false;
        }
        if (slot.container instanceof CraftingContainer) {
            return false;
        }
        if (slot.container == client.player.getInventory()) {
            int slotIndex = slot.getContainerSlot();
            // 0..35 全部可加锁（含快捷栏）；盔甲与副手不参与任何功能，禁止加锁
            return InventorySlots.isPlayerInventorySlot(slotIndex)
                    && PlayerSlotFilter.isPlainPlayerInventorySlot(client.player, slot, true);
        } else {
            // 外部容器：必须属于纯存储白名单类型
            return ServerSortingHandler.isMenuSortable(ServerSortingHandler.menuTypeOrNull(screen.getMenu()));
        }
    }

    private static void toggleSlotLock(Minecraft client, AbstractContainerScreen<?> screen, Slot slot) {
        if (!isSlotLockable(client, screen, slot)) {
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

    private static void transferAllMatching(
            Minecraft client,
            AbstractContainerScreen<?> screen,
            AbstractContainerScreenAccessor accessor,
            Slot clickedSlot,
            ItemStack targetItem) {
        if (client.player == null || screen.getMenu() == null || targetItem.isEmpty()) {
            return;
        }

        boolean isPlayerSide = clickedSlot.container == client.player.getInventory();
        String menuKey = ClientLockedSlots.getMenuTypeKey(screen.getMenu());

        for (Slot slot : screen.getMenu().slots) {
            if (slot.isFake() || !slot.isActive() || slot instanceof ResultSlot) {
                continue;
            }
            if (slot.container instanceof CraftingContainer) {
                continue;
            }

            if (isPlayerSide) {
                if (slot.container != client.player.getInventory()) {
                    continue;
                }
                int slotIdx = slot.getContainerSlot();
                // 快捷栏是否参与由「手势影响快捷栏」开关决定；锁定槽位禁止转移
                if (!PlayerSlotFilter.isPlainPlayerInventorySlot(client.player, slot,
                        OneKeyFabric.config().gestures.includeHotbar)) {
                    continue;
                }
                if (ClientLockedSlots.isLocked("player_inventory", slotIdx)) {
                    continue;
                }
            } else {
                if (slot.container == client.player.getInventory()) {
                    continue;
                }
                if (ClientLockedSlots.isLocked(menuKey, slot.index)) {
                    continue;
                }
            }

            ItemStack item = slot.getItem();
            if (!item.isEmpty() && ItemStack.isSameItemSameComponents(item, targetItem)) {
                accessor.onekey$slotClicked(slot, slot.index, 0, ContainerInput.QUICK_MOVE);
                if (!slot.getItem().isEmpty()) {
                    // 槽位未能完全清空（对侧已无空间），中止后续多余点击
                    break;
                }
            }
        }
    }

    private static boolean isSafeForQuickMove(Player player, AbstractContainerScreen<?> screen, Slot slot) {
        if (player == null || screen == null || slot == null || !slot.isActive() || slot.isFake()) {
            return false;
        }
        // 严格拦截产物槽与交易产物槽，防止引发高频发包合成/交易风暴
        if (slot instanceof ResultSlot || slot instanceof MerchantResultSlot) {
            return false;
        }
        if (!slot.hasItem() || slot.getItem().isEmpty()) {
            return false;
        }
        // 放行合成输入格（工作台 3x3 或背包 2x2），支持扫掠快速收回材料回背包
        if (slot.container instanceof CraftingContainer) {
            return true;
        }
        if (slot.container == player.getInventory()) {
            int slotIdx = slot.getContainerSlot();
            if (!PlayerSlotFilter.isPlainPlayerInventorySlot(player, slot, OneKeyFabric.config().gestures.includeHotbar)) {
                return false;
            }
            return !ClientLockedSlots.isLocked("player_inventory", slotIdx);
        } else {
            String menuKey = ClientLockedSlots.getMenuTypeKey(screen.getMenu());
            return !ClientLockedSlots.isLocked(menuKey, slot.index);
        }
    }

    private static boolean canPlaceRmb(Player player, AbstractContainerScreen<?> screen, Slot slot, ItemStack carried) {
        if (player == null || screen == null || slot == null || !slot.isActive() || slot.isFake()) {
            return false;
        }
        if (slot instanceof ResultSlot || slot instanceof MerchantResultSlot) {
            return false;
        }
        if (carried == null || carried.isEmpty() || !slot.mayPlace(carried)) {
            return false;
        }
        if (slot.container instanceof CraftingContainer) {
            ItemStack slotItem = slot.getItem();
            if (slotItem.isEmpty()) {
                return true;
            }
            int max = Math.min(slot.getMaxStackSize(carried), carried.getMaxStackSize());
            return ItemStack.isSameItemSameComponents(slotItem, carried) && slotItem.getCount() < max;
        }
        if (slot.container == player.getInventory()) {
            int slotIdx = slot.getContainerSlot();
            if (!PlayerSlotFilter.isPlainPlayerInventorySlot(player, slot, OneKeyFabric.config().gestures.includeHotbar)) {
                return false;
            }
            if (ClientLockedSlots.isLocked("player_inventory", slotIdx)) {
                return false;
            }
        } else {
            String menuKey = ClientLockedSlots.getMenuTypeKey(screen.getMenu());
            if (ClientLockedSlots.isLocked(menuKey, slot.index)) {
                return false;
            }
        }
        ItemStack slotItem = slot.getItem();
        if (slotItem.isEmpty()) {
            return true;
        }
        int max = Math.min(slot.getMaxStackSize(carried), carried.getMaxStackSize());
        return ItemStack.isSameItemSameComponents(slotItem, carried) && slotItem.getCount() < max;
    }

    private static void triggerScreenSort(Minecraft client, AbstractContainerScreen<?> screen) {
        if (client.player == null || screen instanceof CreativeModeInventoryScreen) {
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

        // 客户端冷却检查（防连点发包与乐观置换错位）
        long now = System.currentTimeMillis();
        if (now - lastClientSortTime < OneKeyFabric.config().sort.cooldownMs) {
            return;
        }
        lastClientSortTime = now;

        // 乐观 UI 预测：客户端本地即刻执行纯逻辑排序置换与音效
        applyOptimisticSort(client, syncId, mode);

        ClientPlayNetworking.send(new SortRequestPayload(new SortRequest(syncId, mode)));
    }

    /**
     * 合并散落在多个槽位的同类堆叠（同物品 + 同组件），直接修改快照，返回是否发生了数量变化。
     * 必须在排序之前调用：否则「16 个楼梯 + 10 个楼梯」会被当成两叠独立物品分别排序，
     * 玩家看到的仍然是被拆开的两堆。
     */
    private static boolean condenseSnapshot(List<Slot> slots, List<ItemStack> snapshot, Set<Integer> lockedPositions) {
        int size = slots.size();
        int[] counts = new int[size];
        int[] maxCounts = new int[size];
        for (int i = 0; i < size; i++) {
            ItemStack stack = snapshot.get(i);
            counts[i] = stack.getCount();
            maxCounts[i] = stack.isEmpty() ? 64 : Math.min(slots.get(i).getMaxStackSize(stack), stack.getMaxStackSize());
        }

        int[] condensed = StackCondenser.condense(counts, maxCounts, lockedPositions,
                (first, second) -> ItemStack.isSameItemSameComponents(
                        snapshot.get(first), snapshot.get(second)));

        boolean changed = false;
        for (int i = 0; i < size; i++) {
            if (condensed[i] == counts[i]) {
                continue;
            }
            if (condensed[i] <= 0) {
                snapshot.set(i, ItemStack.EMPTY);
            } else {
                snapshot.get(i).setCount(condensed[i]);
            }
            changed = true;
        }
        return changed;
    }

    private static void applyOptimisticSort(Minecraft client, int syncId, int mode) {
        if (client.player == null) {
            return;
        }
        AbstractContainerMenu menu = client.player.containerMenu;
        if (menu == null || menu.containerId != syncId) {
            return;
        }
        if (!menu.getCarried().isEmpty()) {
            return;
        }

        List<Slot> slotsToSort = resolveClientSlotsToSort(menu, client.player, mode);
        if (slotsToSort.isEmpty()) {
            return;
        }

        boolean playerScope = slotsToSort.get(0).container == client.player.getInventory();
        String containerKey = playerScope ? "player_inventory" : ClientLockedSlots.getMenuTypeKey(menu);
        Set<Integer> lockedKeys = ClientLockedSlots.getLocks(containerKey);

        List<Integer> slotLockKeys = new ArrayList<>(slotsToSort.size());
        for (Slot slot : slotsToSort) {
            slotLockKeys.add(playerScope ? slot.getContainerSlot() : slot.index);
        }
        Set<Integer> lockedPositions = Sorter.resolveLockedPositions(slotLockKeys, lockedKeys);

        // 快照原始槽位物品：纯内存快照中合并与重排，避免直接原地脏写导致异常时半污染
        List<ItemStack> snapshot = new ArrayList<>(slotsToSort.size());
        for (Slot slot : slotsToSort) {
            snapshot.add(slot.getItem().copy());
        }

        // 合并同类堆叠：把散在不同槽位的「同物品 + 同组件」先并成满堆，再排序
        boolean condensedChanged = false;
        if (OneKeyFabric.config().sort.mergeStacks) {
            condensedChanged = condenseSnapshot(slotsToSort, snapshot, lockedPositions);
        }

        List<StackView> views = new ArrayList<>(slotsToSort.size());
        for (ItemStack stack : snapshot) {
            views.add(ItemConversion.toStackView(stack));
        }

        int[] permutation = Sorter.permutation(views, lockedPositions, OneKeyFabric.config().sort.toRule());
        int moved = 0;
        for (int i = 0; i < permutation.length; i++) {
            if (permutation[i] != i && !views.get(permutation[i]).isEmpty()) {
                moved++;
            }
        }

        if (moved > 0 || condensedChanged) {
            for (int target = 0; target < permutation.length; target++) {
                int source = permutation[target];
                Slot slot = slotsToSort.get(target);
                slot.set(snapshot.get(source));
                slot.setChanged();
            }

            client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BUNDLE_INSERT, 1.0F));
        }
    }

    private static List<Slot> resolveClientSlotsToSort(AbstractContainerMenu menu, LocalPlayer player, int mode) {
        boolean isPlayerOnly = (mode == SortRequest.MODE_PLAYER) || (menu instanceof InventoryMenu);
        if (isPlayerOnly) {
            return clientPlayerMainInventorySlots(menu, player);
        }

        if (ServerSortingHandler.isMenuSortable(ServerSortingHandler.menuTypeOrNull(menu))) {
            List<Slot> external = new ArrayList<>();
            for (Slot slot : menu.slots) {
                if (slot.container != player.getInventory() && isSortableSlot(slot)) {
                    external.add(slot);
                }
            }
            if (!external.isEmpty()) {
                return external;
            }
        }

        return clientPlayerMainInventorySlots(menu, player);
    }

    private static List<Slot> clientPlayerMainInventorySlots(AbstractContainerMenu menu, LocalPlayer player) {
        List<Slot> result = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (PlayerSlotFilter.isPlainPlayerInventorySlot(player, slot,
                    OneKeyFabric.config().sort.includeHotbar)) {
                result.add(slot);
            }
        }
        return result;
    }

    private static boolean isSortableSlot(Slot slot) {
        if (slot.isFake() || !slot.isActive() || slot instanceof ResultSlot) {
            return false;
        }
        if (slot.container instanceof CraftingContainer) {
            return false;
        }
        return true;
    }

    private static Optional<TargetPos> getCrosshairTarget(Minecraft client) {
        if (client.player == null) {
            return Optional.empty();
        }
        // 优先使用客户端当前帧已经计算好的 hitResult
        HitResult hit = client.hitResult;
        if (hit != null && hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
            BlockPos pos = blockHit.getBlockPos();
            return Optional.of(TargetPos.of(pos.getX(), pos.getY(), pos.getZ()));
        }
        // hitResult 未命中时，按「原版方块交互距离」补一次射线检测（生存 4.5 / 创造 5 格），
        // 绝不使用扫描半径当视距，避免隔着几格凭空锁定远处箱子
        double pickRange = client.player.blockInteractionRange();
        HitResult extendedHit = client.player.pick(pickRange, 1.0F, false);
        if (extendedHit.getType() == HitResult.Type.BLOCK && extendedHit instanceof BlockHitResult blockHit) {
            BlockPos pos = blockHit.getBlockPos();
            return Optional.of(TargetPos.of(pos.getX(), pos.getY(), pos.getZ()));
        }
        return Optional.empty();
    }

    private static void triggerQuickStack(Minecraft client, boolean stash) {
        if (client.player == null) {
            return;
        }

        if (!ClientPlayNetworking.canSend(QuickStackRequestPayload.TYPE)) {
            client.player.sendOverlayMessage(
                    Component.translatable("onekey.quick_stack.server_not_installed"));
            return;
        }

        int syncId = client.player.containerMenu != null ? client.player.containerMenu.containerId : -1;
        int radius = OneKeyFabric.config().quickStack.radius;
        // 「准星瞄准优先」关闭时不再做射线检测，退回纯范围扫描（看哪存哪失效）
        Optional<TargetPos> targetPos = OneKeyFabric.config().quickStack.crosshairPriority
                ? getCrosshairTarget(client)
                : Optional.empty();
        ClientPlayNetworking.send(new QuickStackRequestPayload(new QuickStackRequest(syncId, radius, stash, targetPos)));
    }

    private static KeyMapping register(String translationKey, int defaultKey) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(
                translationKey,
                InputConstants.Type.KEYBOARD,
                defaultKey,
                CATEGORY));
    }

    /**
     * 把配置里的键名（如 {@code key.keyboard.g}）解析成 GLFW 键码；
     * 配置缺失、为空或写了非法键名时回退到内置默认键，绝不让手改配置导致客户端启动失败。
     */
    private static int configuredKey(String configured, int fallback) {
        if (configured == null || configured.isBlank()) {
            return fallback;
        }
        try {
            return InputConstants.getKey(configured).getValue();
        } catch (RuntimeException exception) {
            OneKeyFabric.LOGGER.warn("[OneKey] 无法识别的按键配置 \"{}\"，回退默认键位", configured);
            return fallback;
        }
    }
}
