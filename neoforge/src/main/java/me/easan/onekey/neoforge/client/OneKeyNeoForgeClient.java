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
import me.easan.onekey.neoforge.client.gui.SortButton;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
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
    private static KeyMapping lockSlotModifierKey;
    private static KeyMapping lockSlotTriggerKey;
    private static KeyMapping excavateKey;

    public static final class CustomKeyMapping extends KeyMapping {
        public CustomKeyMapping(String name, InputConstants.Type type, int keyCode, KeyMapping.Category category) {
            super(name, type, keyCode, category);
        }

        public InputConstants.Key getKey() {
            return this.key != null ? this.key : this.getDefaultKey();
        }
    }

    public static KeyMapping getLockSlotModifierKey() {
        return lockSlotModifierKey;
    }

    public static KeyMapping getLockSlotTriggerKey() {
        return lockSlotTriggerKey;
    }

    public static KeyMapping getExcavateKey() {
        return excavateKey;
    }

    public static InputConstants.Key getKey(KeyMapping mapping) {
        if (mapping instanceof CustomKeyMapping custom) {
            return custom.getKey();
        }
        if (mapping == null) {
            return InputConstants.UNKNOWN;
        }
        try {
            return InputConstants.getKey(mapping.saveString());
        } catch (Throwable ignored) {
            return InputConstants.UNKNOWN;
        }
    }

    public static Component getLockKeyComboComponent() {
        KeyMapping modifier = lockSlotModifierKey;
        KeyMapping trigger = lockSlotTriggerKey;
        if (trigger == null || trigger.isUnbound()) {
            return Component.translatable("key.keyboard.unknown");
        }
        if (modifier == null || modifier.isUnbound()) {
            return trigger.getTranslatedKeyMessage();
        }
        return Component.empty()
                .append(modifier.getTranslatedKeyMessage())
                .append(Component.literal(" + "))
                .append(trigger.getTranslatedKeyMessage());
    }

    public static boolean isModifierActive(KeyMapping modifierMapping, net.minecraft.client.input.InputWithModifiers event) {
        if (modifierMapping == null || modifierMapping.isUnbound()) {
            return true;
        }
        InputConstants.Key key = getKey(modifierMapping);
        if (key == null || key == InputConstants.UNKNOWN) {
            return true;
        }
        if (event != null) {
            if ((key.getValue() == InputConstants.KEY_LALT || key.getValue() == InputConstants.KEY_RALT) && event.hasAltDown()) {
                return true;
            }
            if ((key.getValue() == InputConstants.KEY_LCONTROL || key.getValue() == InputConstants.KEY_RCONTROL) && event.hasControlDown()) {
                return true;
            }
            if ((key.getValue() == InputConstants.KEY_LSHIFT || key.getValue() == InputConstants.KEY_RSHIFT) && event.hasShiftDown()) {
                return true;
            }
        }
        if (key.getType() == InputConstants.Type.KEYBOARD && key.getValue() > 0) {
            try {
                if (InputConstants.isKeyDown(key.getValue())) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        if (key.getType() == InputConstants.Type.MOUSE) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.mouseHandler != null) {
                if (key.getValue() == InputConstants.MOUSE_BUTTON_LEFT && mc.mouseHandler.isLeftPressed()) {
                    return true;
                }
                if (key.getValue() == InputConstants.MOUSE_BUTTON_RIGHT && mc.mouseHandler.isRightPressed()) {
                    return true;
                }
                if (key.getValue() == InputConstants.MOUSE_BUTTON_MIDDLE && mc.mouseHandler.isMiddlePressed()) {
                    return true;
                }
            }
        }
        return false;
    }

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
        return button == InputConstants.MOUSE_BUTTON_LEFT;
    }

    private static boolean isRightButton(int button) {
        return button == InputConstants.MOUSE_BUTTON_RIGHT;
    }

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
        sortKey = new CustomKeyMapping(ActionRegistry.get(ActionId.SORT).orElseThrow().translationKey(),
                InputConstants.Type.KEYBOARD, configuredKey(hotkeys.sort, InputConstants.KEY_R), CATEGORY);
        quickStackKey = new CustomKeyMapping(ActionRegistry.get(ActionId.QUICK_STACK).orElseThrow().translationKey(),
                InputConstants.Type.KEYBOARD, configuredKey(hotkeys.quickStack, InputConstants.KEY_V), CATEGORY);
        quickStashKey = new CustomKeyMapping(ActionRegistry.get(ActionId.QUICK_STASH).orElseThrow().translationKey(),
                InputConstants.Type.KEYBOARD, configuredKey(hotkeys.quickStash, InputConstants.KEY_B), CATEGORY);
        settingsKey = new CustomKeyMapping(ActionRegistry.get(ActionId.SETTINGS).orElseThrow().translationKey(),
                InputConstants.Type.KEYBOARD, configuredKey(hotkeys.settings, InputConstants.KEY_K), CATEGORY);
        lockSlotModifierKey = new CustomKeyMapping(ActionRegistry.get(ActionId.LOCK_SLOT_MODIFIER).orElseThrow().translationKey(),
                InputConstants.Type.KEYBOARD, configuredKey(hotkeys.lockSlotModifier, InputConstants.KEY_LALT), CATEGORY);
        lockSlotTriggerKey = new CustomKeyMapping(ActionRegistry.get(ActionId.LOCK_SLOT).orElseThrow().translationKey(),
                InputConstants.Type.MOUSE, configuredKey(hotkeys.lockSlot, InputConstants.MOUSE_BUTTON_LEFT), CATEGORY);
        excavateKey = new CustomKeyMapping(ActionRegistry.get(ActionId.EXCAVATE).orElseThrow().translationKey(),
                InputConstants.Type.KEYBOARD, configuredKey(hotkeys.excavate, InputConstants.KEY_GRAVE), CATEGORY);

        event.register(sortKey);
        event.register(quickStackKey);
        event.register(quickStashKey);
        event.register(settingsKey);
        event.register(lockSlotModifierKey);
        event.register(lockSlotTriggerKey);
        event.register(excavateKey);

        ClientExcavatorHandler.init(excavateKey);
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
    public static boolean serverSupports(CustomPacketPayload.Type<?> type) {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(type);
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        resetGestureState();
        if (event.getScreen() instanceof AbstractContainerScreen<?> containerScreen) {
            // 打开容器界面且开启整理按钮时，在右上角挂载原版风格整理按键
            if (OneKeyNeoForge.config().sort.enabled && OneKeyNeoForge.config().sort.showSortButton
                    && isScreenEligibleForSortButton(containerScreen)) {
                if (containerScreen instanceof AbstractContainerScreenAccessor accessor) {
                    int x = accessor.onekey$getLeftPos() + accessor.onekey$getImageWidth() - 18;
                    int y = accessor.onekey$getTopPos() + 5;
                    net.minecraft.client.gui.components.Button sortButton = SortButton.create(x, y, containerScreen);
                    event.addListener(sortButton);
                }
            }

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
                return;
            }
            if (lockSlotTriggerKey != null && lockSlotTriggerKey.matches(event.getKeyEvent()) && isModifierActive(lockSlotModifierKey, event.getKeyEvent())) {
                if (containerScreen instanceof AbstractContainerScreenAccessor accessor) {
                    Slot hovered = accessor.onekey$getHoveredSlot();
                    if (hovered != null && isSlotLockable(client, containerScreen, hovered)) {
                        toggleSlotLock(client, containerScreen, hovered);
                        event.setCanceled(true);
                        return;
                    }
                }
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
        if (containerScreen instanceof CreativeModeInventoryScreen || containerScreen.getMenu() == null) {
            return;
        }
        if (!(containerScreen instanceof AbstractContainerScreenAccessor accessor)) {
            return;
        }

        boolean scrollUp = vertical > 0;
        if (OneKeyNeoForge.config().gestures.invertWheel) {
            scrollUp = !scrollUp;
        }

        ItemStack carried = containerScreen.getMenu().getCarried();
        // 光标持物时的滚轮微调：向下放入 1 件
        if (!carried.isEmpty()) {
            if (OneKeyNeoForge.config().gestures.wheelCarriedHandling && !scrollUp) {
                Slot hovered = accessor.onekey$findSlotAt(event.getMouseX(), event.getMouseY());
                if (hovered == null) {
                    hovered = accessor.onekey$getHoveredSlot();
                }
                if (hovered != null && canPlaceRmb(client.player, containerScreen, hovered, carried)) {
                    accessor.onekey$slotClicked(hovered, hovered.index, 1, ContainerInput.PICKUP);
                    client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2F));
                    event.setCanceled(true);
                    return;
                }
            }
            return;
        }

        if (!ServerSortingHandler.isMenuSortable(ServerSortingHandler.menuTypeOrNull(containerScreen.getMenu()))) {
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
        ClientPacketDistributor.sendToServer(new ScrollItemTransferPayload(
                containerScreen.getMenu().containerId,
                hovered.index,
                scrollUp,
                OneKeyNeoForge.config().gestures.includeHotbar
        ));
        client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, scrollUp ? 1.4F : 1.1F));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMouseButtonPressed(ScreenEvent.MouseButtonPressed.Pre event) {
        Minecraft client = Minecraft.getInstance();
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> containerScreen) || client.player == null) {
            return;
        }
        if (containerScreen instanceof CreativeModeInventoryScreen) {
            return;
        }
        if (!(containerScreen instanceof AbstractContainerScreenAccessor accessor)) {
            return;
        }

        Slot hovered = accessor.onekey$findSlotAt(event.getMouseX(), event.getMouseY());
        if (hovered == null) {
            hovered = accessor.onekey$getHoveredSlot();
        }

        // 自定义按键绑定：锁定/解锁槽位（默认 Left Alt + 鼠标左键，支持任意组合或单键）
        if (lockSlotTriggerKey != null && lockSlotTriggerKey.matchesMouse(event.getMouseButtonEvent()) && isModifierActive(lockSlotModifierKey, event.getMouseButtonEvent())) {
            if (hovered != null && isSlotLockable(client, containerScreen, hovered)) {
                toggleSlotLock(client, containerScreen, hovered);
                event.setCanceled(true);
                return;
            }
        }

        // Shift + 左键：双击同类批量快速移动，同时记录拖拽起始槽位
        if (event.getMouseButtonEvent().hasShiftDown() && isLeftButton(event.getButton())) {
            lastDraggedSlotIndex = (hovered != null ? hovered.index : -1);
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

        // 右键点击时记录起手槽位与初始状态，保持原版完整状态由拖拽状态机按需接管
        if (isRightButton(event.getButton())) {
            lastRmbSlotIndex = (hovered != null ? hovered.index : -1);
            rmbDragActive = false;
        }
    }

    @SubscribeEvent
    public static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        Minecraft client = Minecraft.getInstance();
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> containerScreen) || client.player == null) {
            return;
        }
        if (containerScreen instanceof CreativeModeInventoryScreen || containerScreen.getMenu() == null) {
            return;
        }
        if (!(containerScreen instanceof AbstractContainerScreenAccessor accessor)) {
            return;
        }

        var mouseEvent = event.getMouseButtonEvent();
        // 1. Shift + 左键拖拽滑动扫掠连续转移（LMB Drag Sweep）
        // 仅在光标无持物时允许扫掠，避免与持物放置冲突
        if (OneKeyNeoForge.config().gestures.lmbDragSweep && isLeftButton(event.getMouseButton()) && mouseEvent.hasShiftDown()
                && containerScreen.getMenu().getCarried().isEmpty()) {
            Slot hovered = accessor.onekey$findSlotAt(event.getMouseX(), event.getMouseY());
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
            return;
        }

        // 2. 右键划动重复投放（RMB Drag Repeated Placement）
        if (OneKeyNeoForge.config().gestures.rmbDragRepeat && isRightButton(event.getMouseButton()) && !mouseEvent.hasShiftDown()) {
            ItemStack carried = containerScreen.getMenu().getCarried();
            if (carried.isEmpty()) {
                return;
            }

            Slot hovered = accessor.onekey$findSlotAt(event.getMouseX(), event.getMouseY());
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
                    event.setCanceled(true); // 拦截拖拽，阻止原版 quickCraft 累积
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
                event.setCanceled(true); // 拦截拖拽
            }
        }
    }

    @SubscribeEvent
    public static void onMouseButtonReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (isLeftButton(event.getButton())) {
            lastDraggedSlotIndex = -1;
        }
        if (isRightButton(event.getButton())) {
            lastRmbSlotIndex = -1;
            if (rmbDragActive) {
                rmbDragActive = false;
                if (event.getScreen() instanceof AbstractContainerScreenAccessor accessor) {
                    accessor.onekey$getQuickCraftSlots().clear();
                    accessor.onekey$setIsQuickCrafting(false);
                }
                event.setCanceled(true); // 彻底拦截释放事件，阻止原版 quickCraft 结算或多余点击
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (client.gui != null && client.gui.screen() == null) {
            resetGestureState();
        }

        ClientExcavatorHandler.onClientTick(client);

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
    public static void onMouseScroll(net.neoforged.neoforge.client.event.InputEvent.MouseScrollingEvent event) {
        Minecraft client = Minecraft.getInstance();
        if (ClientExcavatorHandler.onMouseScroll(client, event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderGui(net.neoforged.neoforge.client.event.RenderGuiEvent.Post event) {
        ClientExcavatorHandler.renderHud(event.getGuiGraphics(), event.getPartialTick());
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientLockedSlots.clear();
        ItemConversion.clearCache();
        resetGestureState();
        // 登录后立即把连锁破坏偏好同步给服务端，避免服务端在玩家第一次改配置前用默认值跑
        ClientExcavatorHandler.syncState();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientLockedSlots.clear();
        ItemConversion.clearCache();
        resetGestureState();
        ClientExcavatorHandler.onLogout();
    }

    public static boolean isSlotLockable(Minecraft client, AbstractContainerScreen<?> screen, Slot slot) {
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
            if (!PlayerSlotFilter.isPlainPlayerInventorySlot(player, slot, OneKeyNeoForge.config().gestures.includeHotbar)) {
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
            if (!PlayerSlotFilter.isPlainPlayerInventorySlot(player, slot, OneKeyNeoForge.config().gestures.includeHotbar)) {
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

    public static boolean isScreenEligibleForSortButton(AbstractContainerScreen<?> screen) {
        if (screen == null || screen.getMenu() == null) {
            return false;
        }
        if (screen instanceof InventoryScreen || screen.getMenu() instanceof InventoryMenu) {
            return true;
        }
        if (screen instanceof CreativeModeInventoryScreen) {
            return true;
        }
        return ServerSortingHandler.isMenuSortable(ServerSortingHandler.menuTypeOrNull(screen.getMenu()));
    }

    public static void triggerButtonSort(Minecraft client, AbstractContainerScreen<?> screen, boolean shiftPressed) {
        if (client.player == null || screen == null || screen.getMenu() == null) {
            return;
        }

        int syncId = screen.getMenu().containerId;
        int mode = SortRequest.MODE_CONTAINER;

        if (screen instanceof CreativeModeInventoryScreen) {
            mode = SortRequest.MODE_PLAYER;
            syncId = 0;
        } else if (screen instanceof InventoryScreen || screen.getMenu() instanceof InventoryMenu) {
            mode = SortRequest.MODE_PLAYER;
        } else if (shiftPressed) {
            // Shift + 点击整理按键：强制整理玩家背包
            mode = SortRequest.MODE_PLAYER;
        }

        triggerSort(client, syncId, mode);
    }

    public static void triggerScreenSort(Minecraft client, AbstractContainerScreen<?> screen) {
        if (client.player == null || screen == null || screen.getMenu() == null) {
            return;
        }

        int syncId = screen.getMenu().containerId;
        int mode = SortRequest.MODE_CONTAINER;

        if (screen instanceof CreativeModeInventoryScreen) {
            mode = SortRequest.MODE_PLAYER;
            syncId = 0;
        } else if (screen instanceof InventoryScreen || screen.getMenu() instanceof InventoryMenu) {
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

        var sortCfg = OneKeyNeoForge.config().sort;
        int options = SortRequest.packOptions(
                sortCfg.mergeStacks,
                sortCfg.includeHotbar,
                sortCfg.groupByCategory,
                sortCfg.sortByName,
                sortCfg.sortByCountDesc
        );
        ClientPacketDistributor.sendToServer(new SortRequestPayload(new SortRequest(syncId, mode, options)));
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
        // 创造模式下如果当前不在生存模式背包页签，则从 player.inventoryMenu 提取全部背包槽位
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.gui != null
                && client.gui.screen() instanceof CreativeModeInventoryScreen creative
                && !creative.isInventoryOpen()) {
            menu = player.inventoryMenu;
        }
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
        boolean smartMatching = OneKeyNeoForge.config().quickStack.smartMatching;
        // 「准星瞄准优先」关闭时不再做射线检测，退回纯范围扫描（看哪存哪失效）
        Optional<TargetPos> targetPos = OneKeyNeoForge.config().quickStack.crosshairPriority
                ? getCrosshairTarget(client)
                : Optional.empty();
        ClientPacketDistributor.sendToServer(new QuickStackRequestPayload(new QuickStackRequest(syncId, radius, stash, targetPos, smartMatching)));
    }

    @SubscribeEvent
    public static void onExtractBlockOutline(ExtractBlockOutlineRenderStateEvent event) {
        if (ClientExcavatorHandler.isExcavateKeyDown() && OneKeyNeoForge.config().excavator.highlightOutlines) {
            event.addCustomRenderer((outlineState, submitNodeCollector, poseStack, levelRenderState) -> {
                ClientExcavatorHandler.renderBlockOutlines(outlineState, submitNodeCollector, poseStack, levelRenderState);
                return false;
            });
        }
    }
}
