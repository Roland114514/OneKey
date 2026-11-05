package me.easan.onekey.neoforge.client;

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
import me.easan.onekey.neoforge.util.PlayerSlotFilter;
import me.easan.onekey.common.sort.Sorter;
import me.easan.onekey.common.sort.StackView;
import me.easan.onekey.neoforge.OneKeyNeoForge;
import me.easan.onekey.neoforge.client.compat.ModConflictDetector;
import me.easan.onekey.neoforge.client.gui.OneKeySettingsScreen;
import me.easan.onekey.neoforge.client.mixin.AbstractContainerScreenAccessor;
import me.easan.onekey.neoforge.net.QuickStackRequestPayload;
import me.easan.onekey.neoforge.net.RequestSlotLocksPayload;
import me.easan.onekey.neoforge.net.ScrollItemTransferPayload;
import me.easan.onekey.neoforge.net.SortRequestPayload;
import me.easan.onekey.neoforge.net.ToggleSlotLockPayload;
import me.easan.onekey.neoforge.server.ServerSortingHandler;
import me.easan.onekey.neoforge.util.ItemConversion;
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
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge 客户端事件与手势交互中心。
 */
public final class OneKeyNeoForgeClient {

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(OneKeyNeoForge.MOD_ID, "keys"));

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

    private OneKeyNeoForgeClient() {
    }

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(OneKeyNeoForgeClient::onRegisterKeyMappings);
        NeoForge.EVENT_BUS.register(OneKeyNeoForgeClient.class);
        OneKeyNeoForge.LOGGER.info("[OneKey-NeoForge] 客户端已初始化，注册热键与手势系统");
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        // 默认键位来自 config/onekey.json 的 hotkeys 段（非法键名自动回退内置默认值），
        // 真正的按键重绑仍在原版「选项 → 控制 → 按键绑定」中完成。
        var hotkeys = OneKeyNeoForge.config().hotkeys;
        sortKey = new KeyMapping(ActionRegistry.get(ActionId.SORT).orElseThrow().translationKey(),
                InputConstants.Type.KEYBOARD, configuredKey(hotkeys.sort, InputConstants.KEY_R), CATEGORY);
        quickStackKey = new KeyMapping(ActionRegistry.get(ActionId.QUICK_STACK).orElseThrow().translationKey(),
                InputConstants.Type.KEYBOARD, configuredKey(hotkeys.quickStack, InputConstants.KEY_V), CATEGORY);
        quickStashKey = new KeyMapping(ActionRegistry.get(ActionId.QUICK_STASH).orElseThrow().translationKey(),
                InputConstants.Type.KEYBOARD, configuredKey(hotkeys.quickStash, InputConstants.KEY_B), CATEGORY);
        settingsKey = new KeyMapping(ActionRegistry.get(ActionId.SETTINGS).orElseThrow().translationKey(),
                InputConstants.Type.KEYBOARD, configuredKey(hotkeys.settings, InputConstants.KEY_K), CATEGORY);

        event.register(sortKey);
        event.register(quickStackKey);
        event.register(quickStashKey);
        event.register(settingsKey);
    }

    /**
     * 把配置里的键名（如 {@code key.keyboard.g}）解析成 GLFW 键码；非法值回退内置默认键，
     * 不让手改配置导致按键注册阶段抛异常。
     */
    private static int configuredKey(String configured, int fallback) {
        if (configured == null || configured.isBlank()) {
            return fallback;
        }
        try {
            return InputConstants.getKey(configured).getValue();
        } catch (RuntimeException exception) {
            OneKeyNeoForge.LOGGER.warn("[OneKey-NeoForge] 无法识别的按键配置 \"{}\"，回退默认键位", configured);
            return fallback;
        }
    }

    /**
     * 判断当前连接是否与服务端协商过某个 OneKey 通道。
     * 服务端未安装模组时该通道不会被协商，据此做友好降级提示（与 Fabric 端 canSend 行为对齐）。
     */
    private static boolean serverSupports(CustomPacketPayload.Type<?> type) {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(type);
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> containerScreen) {
            if (!serverSupports(RequestSlotLocksPayload.TYPE)) {
                return; // 服务端未装 OneKey：不发锁定请求，避免无意义的未知通道包
            }
            ClientPacketDistributor.sendToServer(new RequestSlotLocksPayload("player_inventory"));
            if (containerScreen.getMenu() != null) {
                String menuKey = ClientLockedSlots.getMenuTypeKey(containerScreen.getMenu());
                if (!"player_inventory".equals(menuKey)) {
                    ClientPacketDistributor.sendToServer(new RequestSlotLocksPayload(menuKey));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        Minecraft client = Minecraft.getInstance();
        if (event.getScreen() instanceof AbstractContainerScreen<?> containerScreen) {
            if (event.getScreen().getFocused() instanceof EditBox editBox && editBox.canConsumeInput()) {
                return;
            }

            if (sortKey.matches(event.getKeyEvent())) {
                triggerScreenSort(client, containerScreen);
                event.setCanceled(true);
                return;
            }
            if (quickStackKey.matches(event.getKeyEvent())) {
                triggerQuickStack(client, false);
                event.setCanceled(true);
                return;
            }
            if (quickStashKey.matches(event.getKeyEvent())) {
                triggerQuickStack(client, true);
                event.setCanceled(true);
                return;
            }
            if (settingsKey.matches(event.getKeyEvent())) {
                client.setScreenAndShow(new OneKeySettingsScreen(containerScreen));
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        double vertical = event.getScrollDeltaY();
        if (vertical == 0) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> containerScreen) || client.player == null) {
            return;
        }
        if (containerScreen.getMenu() == null
                || !ServerSortingHandler.isMenuSortable(ServerSortingHandler.menuTypeOrNull(containerScreen.getMenu()))) {
            return;
        }
        if (!containerScreen.getMenu().getCarried().isEmpty()) {
            return;
        }
        if (!(containerScreen instanceof AbstractContainerScreenAccessor accessor)) {
            return;
        }

        Slot hovered = accessor.onekey$findSlotAt(event.getMouseX(), event.getMouseY());
        if (hovered == null) {
            hovered = accessor.onekey$getHoveredSlot();
        }
        if (hovered == null || hovered.isFake() || !hovered.isActive() || hovered instanceof ResultSlot) {
            return;
        }
        if (hovered.container instanceof CraftingContainer || hovered.getItem().isEmpty()) {
            return;
        }

        boolean isPlayer = hovered.container == client.player.getInventory();
        if (isPlayer) {
            int slotIdx = hovered.getContainerSlot();
            if (!PlayerSlotFilter.isPlainPlayerInventorySlot(client.player, hovered,
                    OneKeyNeoForge.config().gestures.includeHotbar)
                    || ClientLockedSlots.isLocked("player_inventory", slotIdx)) {
                return;
            }
        } else {
            String menuKey = ClientLockedSlots.getMenuTypeKey(containerScreen.getMenu());
            if (ClientLockedSlots.isLocked(menuKey, hovered.index)) {
                return;
            }
        }

        long now = System.currentTimeMillis();
        if (now - lastScrollTime < 40L) {
            event.setCanceled(true);
            return;
        }
        lastScrollTime = now;

        if (!serverSupports(ScrollItemTransferPayload.TYPE)) {
            return;
        }
        boolean scrollUp = vertical > 0;
        ClientPacketDistributor.sendToServer(new ScrollItemTransferPayload(containerScreen.getMenu().containerId, hovered.index, scrollUp));
        client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, scrollUp ? 1.4F : 1.1F));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMouseButtonPressed(ScreenEvent.MouseButtonPressed.Pre event) {
        Minecraft client = Minecraft.getInstance();
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> containerScreen) || client.player == null) {
            return;
        }
        if (!(containerScreen instanceof AbstractContainerScreenAccessor accessor)) {
            return;
        }

        // Alt + 左键：锁定/解锁槽位
        if (event.getMouseButtonEvent().hasAltDown() && event.getButton() == 0) {
            Slot hovered = accessor.onekey$findSlotAt(event.getMouseX(), event.getMouseY());
            if (hovered == null) {
                hovered = accessor.onekey$getHoveredSlot();
            }
            if (hovered != null) {
                toggleSlotLock(client, containerScreen, hovered);
                event.setCanceled(true);
                return;
            }
        }

        // Shift + 左键：双击同类批量快速移动
        if (event.getMouseButtonEvent().hasShiftDown() && event.getButton() == 0) {
            Slot hovered = accessor.onekey$findSlotAt(event.getMouseX(), event.getMouseY());
            if (hovered == null) {
                hovered = accessor.onekey$getHoveredSlot();
            }
            if (hovered != null && !hovered.isFake() && hovered.isActive() && !(hovered instanceof ResultSlot)
                    && !(hovered.container instanceof CraftingContainer)) {
                boolean isPlayer = hovered.container == client.player.getInventory();
                boolean lockedOrHotbar = false;
                if (isPlayer) {
                    int slotIdx = hovered.getContainerSlot();
                    lockedOrHotbar = !PlayerSlotFilter.isPlainPlayerInventorySlot(client.player, hovered,
                            OneKeyNeoForge.config().gestures.includeHotbar)
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
                        event.setCanceled(true);
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
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (!conflictWarned && client.player != null) {
            conflictWarned = true;
            List<String> conflicts = ModConflictDetector.detectConflicts();
            if (!conflicts.isEmpty()) {
                OneKeyNeoForge.LOGGER.warn("[OneKey-NeoForge] 检测到共存库存模组: {}", conflicts);
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
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientLockedSlots.clear();
        ItemConversion.clearCache();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientLockedSlots.clear();
        ItemConversion.clearCache();
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

        if (!serverSupports(ToggleSlotLockPayload.TYPE)) {
            return;
        }
        boolean nowLocked = ClientLockedSlots.toggle(containerKey, slotIndex);
        client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, nowLocked ? 1.4F : 0.9F));
        ClientPacketDistributor.sendToServer(new ToggleSlotLockPayload(containerKey, slotIndex));
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
            if (slot.isFake() || !slot.isActive() || slot instanceof ResultSlot || slot.container instanceof CraftingContainer) {
                continue;
            }

            if (isPlayerSide) {
                if (slot.container != client.player.getInventory()) {
                    continue;
                }
                int slotIdx = slot.getContainerSlot();
                if (!PlayerSlotFilter.isPlainPlayerInventorySlot(client.player, slot,
                        OneKeyNeoForge.config().gestures.includeHotbar)
                        || ClientLockedSlots.isLocked("player_inventory", slotIdx)) {
                    continue;
                }
            } else {
                if (slot.container == client.player.getInventory() || ClientLockedSlots.isLocked(menuKey, slot.index)) {
                    continue;
                }
            }

            ItemStack item = slot.getItem();
            if (!item.isEmpty() && ItemStack.isSameItemSameComponents(item, targetItem)) {
                accessor.onekey$slotClicked(slot, slot.index, 0, ContainerInput.QUICK_MOVE);
                if (!slot.getItem().isEmpty()) {
                    break;
                }
            }
        }
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
        if (client.player == null || !OneKeyNeoForge.config().sort.enabled) {
            return;
        }

        // 服务端兼容性检测：未装 OneKey 的服务器会静默丢弃我们的包，此处必须提示而不是「按了没反应」
        if (!serverSupports(SortRequestPayload.TYPE)) {
            client.player.sendOverlayMessage(Component.translatable("onekey.sort.server_not_installed"));
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastClientSortTime < OneKeyNeoForge.config().sort.cooldownMs) {
            return;
        }
        lastClientSortTime = now;

        applyOptimisticSort(client, syncId, mode);
        ClientPacketDistributor.sendToServer(new SortRequestPayload(new SortRequest(syncId, mode)));
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
        if (menu == null || menu.containerId != syncId || !menu.getCarried().isEmpty()) {
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
        if (OneKeyNeoForge.config().sort.mergeStacks) {
            condensedChanged = condenseSnapshot(slotsToSort, snapshot, lockedPositions);
        }

        List<StackView> views = new ArrayList<>(slotsToSort.size());
        for (ItemStack stack : snapshot) {
            views.add(ItemConversion.toStackView(stack));
        }

        int[] permutation = Sorter.permutation(views, lockedPositions, OneKeyNeoForge.config().sort.toRule());
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
                    OneKeyNeoForge.config().sort.includeHotbar)) {
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
        if (!serverSupports(QuickStackRequestPayload.TYPE)) {
            client.player.sendOverlayMessage(Component.translatable("onekey.quick_stack.server_not_installed"));
            return;
        }
        int syncId = client.player.containerMenu != null ? client.player.containerMenu.containerId : -1;
        int radius = OneKeyNeoForge.config().quickStack.radius;
        // 「准星瞄准优先」关闭时不再做射线检测，退回纯范围扫描（看哪存哪失效）
        Optional<TargetPos> targetPos = OneKeyNeoForge.config().quickStack.crosshairPriority
                ? getCrosshairTarget(client)
                : Optional.empty();
        ClientPacketDistributor.sendToServer(new QuickStackRequestPayload(new QuickStackRequest(syncId, radius, stash, targetPos)));
    }
}
