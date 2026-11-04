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
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.InventoryMenu;
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
    private static boolean conflictWarned = false;

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
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ClientLockedSlots.clear();
            ItemConversion.clearCache();
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

                // 鼠标滚轮微调单件物品转移监听
                ScreenMouseEvents.allowMouseScroll(screen).register((scr, mouseX, mouseY, horizontalAmount, verticalAmount) -> {
                    if (verticalAmount == 0) {
                        return true;
                    }
                    if (client.player == null || containerScreen.getMenu() == null) {
                        return true;
                    }
                    if (!ServerSortingHandler.isMenuSortable(ServerSortingHandler.menuTypeOrNull(containerScreen.getMenu()))) {
                        return true;
                    }
                    if (!containerScreen.getMenu().getCarried().isEmpty()) {
                        return true;
                    }
                    if (!(containerScreen instanceof AbstractContainerScreenAccessor accessor)) {
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
                        if (slotIdx < 9 || slotIdx >= 36) {
                            return true; // 快捷栏 0..8 严格不参与
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

                    boolean scrollUp = verticalAmount > 0;
                    ClientPlayNetworking.send(new ScrollItemTransferPayload(containerScreen.getMenu().containerId, hovered.index, scrollUp));
                    client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, scrollUp ? 1.4F : 1.1F));
                    return false;
                });

                // 鼠标点击事件：Alt + 左键锁定；Shift + 双击同类批量转移
                ScreenMouseEvents.allowMouseClick(screen).register((scr, event) -> {
                    if (event.hasAltDown() && event.button() == 0) {
                        if (containerScreen instanceof AbstractContainerScreenAccessor accessor) {
                            Slot hovered = accessor.onekey$findSlotAt(event.x(), event.y());
                            if (hovered == null) {
                                hovered = accessor.onekey$getHoveredSlot();
                            }
                            if (hovered != null) {
                                toggleSlotLock(client, containerScreen, hovered);
                                return false; // 拦截事件，避免原版拾取/移动物品
                            }
                        }
                    }

                    if (event.hasShiftDown() && event.button() == 0) {
                        if (containerScreen instanceof AbstractContainerScreenAccessor accessor) {
                            Slot hovered = accessor.onekey$findSlotAt(event.x(), event.y());
                            if (hovered == null) {
                                hovered = accessor.onekey$getHoveredSlot();
                            }
                            if (hovered != null && !hovered.isFake() && hovered.isActive() && !(hovered instanceof ResultSlot)
                                    && !(hovered.container instanceof CraftingContainer)) {
                                boolean isPlayer = hovered.container == client.player.getInventory();
                                boolean lockedOrHotbar = false;
                                if (isPlayer) {
                                    int slotIdx = hovered.getContainerSlot();
                                    lockedOrHotbar = (slotIdx < 9 || slotIdx >= 36) || ClientLockedSlots.isLocked("player_inventory", slotIdx);
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
                        }
                    } else {
                        lastShiftClickTime = 0L;
                        lastShiftClickSlot = -1;
                        lastShiftClickItem = ItemStack.EMPTY;
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
            // 仅允许主背包 9..35 加锁；快捷栏 0..8 严格不参与整理，默认永久受保护，无需且禁止加锁
            return slotIndex >= 9 && slotIndex < 36;
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
                // 快捷栏 0..8 严格隔离，锁定槽位禁止转移
                if (slotIdx < 9 || slotIdx >= 36) {
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

        List<StackView> views = new ArrayList<>(slotsToSort.size());
        for (Slot slot : slotsToSort) {
            views.add(ItemConversion.toStackView(slot.getItem()));
        }

        int[] permutation = Sorter.permutation(views, lockedPositions, OneKeyFabric.config().sort.toRule());
        int moved = 0;
        for (int i = 0; i < permutation.length; i++) {
            if (permutation[i] != i && !views.get(permutation[i]).isEmpty()) {
                moved++;
            }
        }

        if (moved > 0) {
            List<ItemStack> originals = new ArrayList<>(slotsToSort.size());
            for (Slot slot : slotsToSort) {
                originals.add(slot.getItem().copy());
            }

            for (int target = 0; target < permutation.length; target++) {
                int source = permutation[target];
                Slot slot = slotsToSort.get(target);
                slot.set(originals.get(source));
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
            if (slot.container == player.getInventory()) {
                int slotIndex = slot.getContainerSlot();
                if (slotIndex >= 9 && slotIndex < 36) {
                    result.add(slot);
                }
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

    private static Optional<TargetPos> getCrosshairTarget(Minecraft client, double reachDistance) {
        if (client.player == null) {
            return Optional.empty();
        }
        // 优先使用客户端当前帧已经计算好的 hitResult
        HitResult hit = client.hitResult;
        if (hit != null && hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
            BlockPos pos = blockHit.getBlockPos();
            return Optional.of(TargetPos.of(pos.getX(), pos.getY(), pos.getZ()));
        }
        // 若 hitResult 未命中（例如视距略超出默认生存触及距离但仍在扫描半径内），执行延长射线检测
        double pickRange = Math.clamp(reachDistance, 4.5, 12.0);
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
        Optional<TargetPos> targetPos = getCrosshairTarget(client, radius);
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
