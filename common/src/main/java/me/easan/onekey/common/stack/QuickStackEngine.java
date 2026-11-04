package me.easan.onekey.common.stack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 跨加载器通用的快速堆叠/存入纯算法内核。
 *
 * <p>多阶段防污染流水线规则：
 * <ul>
 *   <li>阶段一（Pass 1 同类归位）：遍历所有容器（准星锁定的容器排在首位，其余按距离升序）。
 *       若容器中已包含同类物品，优先填满未满堆叠；若同类溢出，允许占用该分类容器自身的空槽扩展。</li>
 *   <li>阶段二（Pass 2 焦点开荒）：若经过阶段一后物品仍有剩余（或属于未分类新物品），
 *       仅向被准星锁定且允许开荒的容器（{@code allowDumpNewItem == true}）分配空槽；
 *       周边未被锁定的容器严禁占用空槽，彻底杜绝分类箱被乱塞污染。</li>
 * </ul>
 *
 * <p>安全保障：
 * <ul>
 *   <li>快捷栏（0..8）根据配置可受严格永久保护，不参与转移。</li>
 *   <li>锁定槽位严格不参与转移。</li>
 *   <li>操作全流程遵循总数守恒，杜绝物品丢失或复制。</li>
 * </ul>
 */
public final class QuickStackEngine {

    private QuickStackEngine() {
    }

    /**
     * 容器上下文描述：解耦物理坐标与原版类型，仅向算法内核暴露插槽状态与操作策略。
     *
     * @param slots            容器槽位列表
     * @param isTargeted       是否为玩家准星锁定的焦点容器
     * @param allowDumpNewItem 是否允许在此容器的空槽中存入「容器原本没有的全新物品」
     */
    public record ContainerContext(
            List<SimulatedSlot> slots,
            boolean isTargeted,
            boolean allowDumpNewItem
    ) {
        public static ContainerContext of(List<SimulatedSlot> slots, boolean isTargeted, boolean allowDumpNewItem) {
            return new ContainerContext(slots, isTargeted, allowDumpNewItem);
        }
    }

    /**
     * 兼容旧接口的调用方法。
     */
    public static int execute(
            List<SimulatedSlot> playerSlots,
            Set<Integer> lockedSlots,
            List<List<SimulatedSlot>> containers,
            boolean stashMode,
            boolean protectHotbar,
            List<String> itemWhitelist,
            List<String> itemBlacklist
    ) {
        if (containers == null || containers.isEmpty()) {
            return 0;
        }
        List<ContainerContext> contexts = new ArrayList<>(containers.size());
        for (List<SimulatedSlot> c : containers) {
            contexts.add(new ContainerContext(c, false, stashMode));
        }
        return executeContextAware(playerSlots, lockedSlots, contexts, protectHotbar, itemWhitelist, itemBlacklist);
    }

    /**
     * 执行上下文感知的多阶段快速堆叠与一键存入（支持准星锁定置顶与周边容器防污染）。
     *
     * @param playerSlots   玩家槽位列表（通常 36 格，0..8 快捷栏，9..35 主背包）
     * @param lockedSlots   锁定槽位集合（使用槽位索引号）
     * @param containers    附近的容器上下文列表
     * @param protectHotbar true = 底部 9 格快捷栏（0..8）严格不参与
     * @param itemWhitelist 白名单（非空时仅转移白名单内的物品）
     * @param itemBlacklist 黑名单（黑名单内的物品严禁转移）
     * @return 实际转移成功的物品总数量
     */
    public static int executeContextAware(
            List<SimulatedSlot> playerSlots,
            Set<Integer> lockedSlots,
            List<ContainerContext> containers,
            boolean protectHotbar,
            List<String> itemWhitelist,
            List<String> itemBlacklist
    ) {
        if (playerSlots == null || playerSlots.isEmpty() || containers == null || containers.isEmpty()) {
            return 0;
        }

        // 准星锁定的容器排在首位，其余保持原有传入顺序（稳定排序）
        List<ContainerContext> sortedContainers = new ArrayList<>(containers);
        sortedContainers.sort((a, b) -> Boolean.compare(b.isTargeted(), a.isTargeted()));

        Set<Integer> locked = lockedSlots == null ? Set.of() : lockedSlots;
        int totalMoved = 0;

        for (int i = 0; i < playerSlots.size(); i++) {
            // 保护底部快捷栏 0..8
            if (protectHotbar && i < 9) {
                continue;
            }
            // 保护玩家手动锁定的槽位
            if (locked.contains(i)) {
                continue;
            }

            SimulatedSlot pSlot = playerSlots.get(i);
            if (pSlot.isEmpty()) {
                continue;
            }

            String itemId = pSlot.itemId();
            if (itemBlacklist != null && !itemBlacklist.isEmpty() && itemBlacklist.contains(itemId)) {
                continue;
            }
            if (itemWhitelist != null && !itemWhitelist.isEmpty() && !itemWhitelist.contains(itemId)) {
                continue;
            }

            // =========================================================================
            // Pass 1: 同类归位（优先准星容器，其次按距离遍历所有已有该同类物品的容器）
            // 防止近处的空箱子截胡远处分类箱已有的同类物品
            // =========================================================================
            for (ContainerContext ctx : sortedContainers) {
                if (pSlot.isEmpty()) {
                    break;
                }
                List<SimulatedSlot> cSlots = ctx.slots();
                if (cSlots == null || cSlots.isEmpty()) {
                    continue;
                }

                boolean hasMatchingItem = false;
                for (SimulatedSlot cSlot : cSlots) {
                    if (!cSlot.isEmpty() && cSlot.canStackWith(pSlot)) {
                        hasMatchingItem = true;
                        break;
                    }
                }

                if (!hasMatchingItem) {
                    continue; // 容器中没有同类物品，Pass 1 严禁触碰，防污染
                }

                // 1.1 填入已有同类且未堆满的槽位
                for (SimulatedSlot cSlot : cSlots) {
                    if (!cSlot.isEmpty() && cSlot.canStackWith(pSlot)) {
                        totalMoved += pSlot.transferTo(cSlot);
                        if (pSlot.isEmpty()) {
                            break;
                        }
                    }
                }

                // 1.2 同类物品溢出，且容器已包含该物品：允许占用该容器自身的空槽扩展
                if (!pSlot.isEmpty()) {
                    for (SimulatedSlot cSlot : cSlots) {
                        if (cSlot.isEmpty()) {
                            totalMoved += pSlot.transferTo(cSlot);
                            if (pSlot.isEmpty()) {
                                break;
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // Pass 2: 全新物品开荒倾倒（仅向 allowDumpNewItem == true 的容器倾倒空槽）
            // =========================================================================
            if (!pSlot.isEmpty()) {
                for (ContainerContext ctx : sortedContainers) {
                    if (pSlot.isEmpty()) {
                        break;
                    }
                    if (!ctx.allowDumpNewItem()) {
                        continue; // 未授权开荒的容器严禁放入新物品
                    }
                    List<SimulatedSlot> cSlots = ctx.slots();
                    if (cSlots == null || cSlots.isEmpty()) {
                        continue;
                    }

                    for (SimulatedSlot cSlot : cSlots) {
                        if (cSlot.isEmpty()) {
                            totalMoved += pSlot.transferTo(cSlot);
                            if (pSlot.isEmpty()) {
                                break;
                            }
                        }
                    }
                }
            }
        }

        return totalMoved;
    }
}
