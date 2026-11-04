package me.easan.onekey.common.stack;

import java.util.List;
import java.util.Set;

/**
 * 跨加载器通用的快速堆叠/存入纯算法内核。
 *
 * <p>两阶段规则：
 * <ul>
 *   <li>阶段一（补齐同类）：优先填入已有同类物品且未堆满的槽位。</li>
 *   <li>阶段二（存入空槽）：在 {@code stashMode == true}（一键存入）或容器中已存在该类物品时，允许占用容器空槽。</li>
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
     * 执行两阶段快速堆叠与存入。
     *
     * @param playerSlots   玩家槽位列表（通常 36 格，0..8 快捷栏，9..35 主背包）
     * @param lockedSlots   锁定槽位集合（使用容器槽位号，例如 9..35）
     * @param containers    附近的容器列表（每个容器为一个槽位列表）
     * @param stashMode     true = 存入全部空槽；false = 仅补齐已有同类物品
     * @param protectHotbar true = 底部 9 格快捷栏（0..8）严格不参与
     * @param itemWhitelist 白名单（非空时仅转移白名单内的物品）
     * @param itemBlacklist 黑名单（黑名单内的物品严禁转移）
     * @return 实际转移成功的物品总数量
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
        if (playerSlots == null || playerSlots.isEmpty() || containers == null || containers.isEmpty()) {
            return 0;
        }

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

            for (List<SimulatedSlot> container : containers) {
                if (pSlot.isEmpty()) {
                    break;
                }
                if (container == null || container.isEmpty()) {
                    continue;
                }

                // 检查本容器是否已拥有此物品
                boolean containerHasItem = false;
                for (SimulatedSlot cSlot : container) {
                    if (!cSlot.isEmpty() && cSlot.canStackWith(pSlot)) {
                        containerHasItem = true;
                        break;
                    }
                }

                // 若既不是全存模式（stashMode），且容器内也没有该物品，则直接跳过该容器
                if (!stashMode && !containerHasItem) {
                    continue;
                }

                // 阶段一：填入未堆满的同类物品槽位
                for (SimulatedSlot cSlot : container) {
                    if (!cSlot.isEmpty() && cSlot.canStackWith(pSlot)) {
                        int moved = pSlot.transferTo(cSlot);
                        totalMoved += moved;
                        if (pSlot.isEmpty()) {
                            break;
                        }
                    }
                }

                // 阶段二：若仍有剩余且允许存入空槽（stashMode 或该容器已有该同类物品）
                if (!pSlot.isEmpty() && (stashMode || containerHasItem)) {
                    for (SimulatedSlot cSlot : container) {
                        if (cSlot.isEmpty()) {
                            int moved = pSlot.transferTo(cSlot);
                            totalMoved += moved;
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
