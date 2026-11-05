package me.easan.onekey.neoforge.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.easan.onekey.common.config.OneKeyConfig;
import me.easan.onekey.common.net.SortRequest;
import me.easan.onekey.common.net.SortResult;
import me.easan.onekey.common.sort.StackCondenser;
import me.easan.onekey.common.inventory.InventorySlots;
import me.easan.onekey.neoforge.util.PlayerSlotFilter;
import me.easan.onekey.common.sort.Sorter;
import me.easan.onekey.common.sort.StackView;
import me.easan.onekey.neoforge.OneKeyNeoForge;
import me.easan.onekey.neoforge.net.SortResultPayload;
import me.easan.onekey.neoforge.storage.LockedSlotsStorage;
import me.easan.onekey.neoforge.util.ItemConversion;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端一键整理处理器（NeoForge）：负责请求校验、槽位筛选、执行排序和回包。
 */
public final class ServerSortingHandler {

    private static final Map<UUID, Long> LAST_SORT_TIME = new ConcurrentHashMap<>();

    private static final Set<MenuType<?>> SORTABLE_MENU_TYPES = Set.of(
            MenuType.GENERIC_9x1, MenuType.GENERIC_9x2, MenuType.GENERIC_9x3,
            MenuType.GENERIC_9x4, MenuType.GENERIC_9x5, MenuType.GENERIC_9x6,
            MenuType.GENERIC_3x3, MenuType.HOPPER, MenuType.SHULKER_BOX);

    public static boolean isMenuSortable(MenuType<?> menuType) {
        return menuType != null && SORTABLE_MENU_TYPES.contains(menuType);
    }

    /**
     * 安全获取菜单类型。玩家背包菜单（{@link InventoryMenu}）与创造模式物品选择菜单在构造时
     * {@code menuType == null}，直接调用 {@link AbstractContainerMenu#getType()} 会抛
     * {@code UnsupportedOperationException("Unable to construct this menu by type")}；
     * 这里统一返回 {@code null}，让调用方按「非纯存储容器」处理。
     */
    public static MenuType<?> menuTypeOrNull(AbstractContainerMenu menu) {
        if (menu == null || menu instanceof InventoryMenu) {
            return null;
        }
        try {
            return menu.getType();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static void onPlayerDisconnect(UUID playerUuid) {
        if (playerUuid != null) {
            LAST_SORT_TIME.remove(playerUuid);
        }
    }

    private ServerSortingHandler() {
    }

    public static void handle(SortRequest request, IPayloadContext context) {
        try {
            process(request, context);
        } catch (Throwable throwable) {
            // 兜底隔离：任何未预期异常都不允许冒泡到服务端 tick（Fabric 不会替你吞异常）
            OneKeyNeoForge.LOGGER.error("[OneKey] 整理请求处理失败，已回滚并回执失败", throwable);
            try {
                if (context.player() != null && context.player().containerMenu != null) {
                    context.player().containerMenu.sendAllDataToRemote();
                }
                replyResult(context, false, 0);
            } catch (Throwable ignored) {
                // 连回执通道都异常时不再二次抛出，避免异常从 catch 里漏出去
            }
        }
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

    private static void replyResult(IPayloadContext context, boolean success, int moved) {
        context.reply(new SortResultPayload(new SortResult(success, moved)));
    }

    private static void process(SortRequest request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player.isSpectator() || player.isDeadOrDying()) {
            return;
        }

        OneKeyConfig config = OneKeyNeoForge.config();
        if (!config.sort.enabled) {
            // 服务端关闭整理时必须把权威状态推回客户端：客户端已做乐观排序，
            // 静默 return 会留下「界面整齐、服务端没动」的幽灵状态。
            AbstractContainerMenu disabledMenu = player.containerMenu;
            if (disabledMenu != null) {
                disabledMenu.sendAllDataToRemote();
            }
            replyResult(context, false, 0);
            return;
        }

        // 校验容器同步 ID 与合法性
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menu.containerId != request.syncId() || !menu.stillValid(player)) {
            if (menu != null) {
                menu.sendAllDataToRemote();
            }
            replyResult(context, false, 0);
            return;
        }

        // 校验冷却时间（默认 250ms，防止连点导致网络错位；被拦截时强制重置客户端同步）
        long now = System.currentTimeMillis();
        long lastTime = LAST_SORT_TIME.getOrDefault(player.getUUID(), 0L);
        if (now - lastTime < config.sort.cooldownMs) {
            menu.sendAllDataToRemote();
            replyResult(context, false, 0);
            return;
        }
        LAST_SORT_TIME.put(player.getUUID(), now);

        // 若玩家光标当前正拿着物品，安全跳过整理，避免发生坐标错位或幽灵物品
        if (!menu.getCarried().isEmpty()) {
            menu.sendAllDataToRemote();
            replyResult(context, false, 0);
            return;
        }

        // 确定本轮整理使用的规则配置（优先采用客户端上报的玩家个性化配置，未提供时回退到服务端默认配置）
        boolean includeHotbar = request.hasCustomOptions() ? request.includeHotbar() : config.sort.includeHotbar;
        boolean mergeStacks = request.hasCustomOptions() ? request.mergeStacks() : config.sort.mergeStacks;
        me.easan.onekey.common.sort.SortRule sortRule = request.hasCustomOptions() ? request.toRule() : config.sort.toRule();

        // 筛选待排序槽位
        List<Slot> slotsToSort = resolveSlotsToSort(menu, player, request.mode(), includeHotbar);
        if (slotsToSort.isEmpty()) {
            menu.sendAllDataToRemote();
            replyResult(context, false, 0);
            return;
        }

        // 锁定槽位：槽位集合是同质的（要么全是玩家背包、要么全是外部容器），据此决定锁定作用域
        boolean playerScope = slotsToSort.get(0).container == player.getInventory();
        String containerKey = playerScope ? "player_inventory" : menuTypeKey(menu);
        Set<Integer> lockedKeys = LockedSlotsStorage.getLockedSlots(player.getUUID(), containerKey);

        // 把每个视图槽位的「锁定键」换算成视图下标，再交给 Sorter（修复容器槽位号与视图下标的错位）
        List<Integer> slotLockKeys = new ArrayList<>(slotsToSort.size());
        for (Slot slot : slotsToSort) {
            slotLockKeys.add(lockIndexFor(player, slot));
        }
        Set<Integer> lockedPositions = Sorter.resolveLockedPositions(slotLockKeys, lockedKeys);

        // 快照原始槽位物品：纯内存快照中合并与重排，避免直接原地脏写导致异常时半污染
        List<ItemStack> snapshot = new ArrayList<>(slotsToSort.size());
        for (Slot slot : slotsToSort) {
            snapshot.add(slot.getItem().copy());
        }

        // 合并同类堆叠：把散在不同槽位的「同物品 + 同组件」先并成满堆，再排序
        boolean condensedChanged = false;
        if (mergeStacks) {
            condensedChanged = condenseSnapshot(slotsToSort, snapshot, lockedPositions);
        }

        // 转换为纯逻辑 StackView
        List<StackView> views = new ArrayList<>(slotsToSort.size());
        for (ItemStack stack : snapshot) {
            views.add(ItemConversion.toStackView(stack));
        }

        // 计算排序置换（Sorter 保证为空格置后、锁定槽不动、且恒为双射）
        int[] permutation = Sorter.permutation(views, lockedPositions, sortRule);

        // 计算发生移动的槽位数量
        int moved = 0;
        for (int i = 0; i < permutation.length; i++) {
            if (permutation[i] != i && !views.get(permutation[i]).isEmpty()) {
                moved++;
            }
        }

        if (moved > 0 || condensedChanged) {
            // 安全写回置换结果
            for (int target = 0; target < permutation.length; target++) {
                int source = permutation[target];
                Slot slot = slotsToSort.get(target);
                slot.set(snapshot.get(source));
                slot.setChanged();
            }

            // 同步槽位变更到客户端
            menu.broadcastChanges();
        }

        // 回包给客户端
        replyResult(context, true, moved);
    }

    private static List<Slot> resolveSlotsToSort(AbstractContainerMenu menu, ServerPlayer player, int mode, boolean includeHotbar) {
        boolean isPlayerOnly = (mode == SortRequest.MODE_PLAYER) || (menu instanceof InventoryMenu);

        if (isPlayerOnly) {
            return playerMainInventorySlots(menu, player, includeHotbar);
        }

        // 只在白名单内的纯存储容器上整理容器本体
        if (isMenuSortable(menuTypeOrNull(menu))) {
            List<Slot> external = new ArrayList<>();
            for (Slot slot : menu.slots) {
                if (slot.container != player.getInventory() && isSortableExternalSlot(slot)) {
                    external.add(slot);
                }
            }
            if (!external.isEmpty()) {
                return external;
            }
        }

        // 不支持整理本容器（或本容器无可排序槽位）→ 降级整理玩家主背包
        return playerMainInventorySlots(menu, player, includeHotbar);
    }

    /** 玩家背包可整理槽位：盔甲 36..39 与副手 40 永不参与；快捷栏 0..8 由「整理包含快捷栏」开关决定。 */
    private static List<Slot> playerMainInventorySlots(AbstractContainerMenu menu, ServerPlayer player, boolean includeHotbar) {
        List<Slot> result = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (slot.container == player.getInventory()) {
                int slotIndex = slot.getContainerSlot();
                if (PlayerSlotFilter.isPlainPlayerInventorySlot(player, slot, includeHotbar)) {
                    result.add(slot);
                }
            }
        }
        return result;
    }

    private static boolean isSortableExternalSlot(Slot slot) {
        if (slot.isFake() || slot instanceof ResultSlot) {
            return false;
        }
        if (slot.container instanceof CraftingContainer) {
            return false;
        }
        return true;
    }

    private static int lockIndexFor(ServerPlayer player, Slot slot) {
        if (slot.container == player.getInventory()) {
            return slot.getContainerSlot();
        }
        return slot.index;
    }

    public static String menuTypeKey(AbstractContainerMenu menu) {
        if (menu == null) {
            return "generic_container";
        }
        if (menu instanceof net.minecraft.world.inventory.InventoryMenu) {
            return "player_inventory";
        }
        try {
            MenuType<?> type = menu.getType();
            if (type == null) {
                return "generic_container";
            }
            Identifier menuType = BuiltInRegistries.MENU.getKey(type);
            return menuType != null ? menuType.toString() : "generic_container";
        } catch (Throwable ignored) {
            return "generic_container";
        }
    }
}
