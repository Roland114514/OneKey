package me.easan.onekey.common.sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 堆叠合并（condense）：把散落在多个槽位里的「同一物品」先并成尽量满的堆，再交给 {@link Sorter} 排序。
 *
 * <p>不合并的话，背包里 16 个楼梯 + 10 个楼梯会各自被视为一叠独立物品参与排序，
 * 结果只是把它们搬来搬去，永远不会变成 26 个一盘 —— 这正是玩家反馈「按 R 不合并同类物品」的原因。
 *
 * <p>纯整数计算、不依赖 Minecraft 类型，方便单测。判定「同一物品」由调用方提供
 * {@link SameItem}（线上实现用 {@code ItemStack.isSameItemSameComponents}，
 * 保证附魔、损坏度、容器内容不同的堆叠不会被错误合并）。
 */
public final class StackCondenser {

    private StackCondenser() {
    }

    /** 判断两个槽位装的是否为可合并的同一物品。下标均是传入数组的下标。 */
    @FunctionalInterface
    public interface SameItem {
        boolean test(int firstSlot, int secondSlot);
    }

    /**
     * 计算合并后的每个槽位数量。
     *
     * <p>规则：
     * <ul>
     *   <li>锁定槽位既不作为来源、也不作为目标，数量原样保留；</li>
     *   <li>同组内按槽位顺序从前到后填满（每格上限 {@code maxCounts[i]}），余量顺延到后面的同类槽位；</li>
     *   <li>物品总数守恒 —— 一组的总容量恒大于等于总数，极端情况下也不会丢物品。</li>
     * </ul>
     *
     * @param counts     每个槽位当前数量（0 表示空槽）
     * @param maxCounts  每个槽位的堆叠上限
     * @param locked     不允许被触碰的槽位下标
     * @param sameItem   物品同一性判定
     * @return 与 {@code counts} 等长的新数量数组
     */
    public static int[] condense(int[] counts, int[] maxCounts, Set<Integer> locked, SameItem sameItem) {
        int size = counts.length;
        int[] result = counts.clone();
        boolean[] grouped = new boolean[size];

        for (int i = 0; i < size; i++) {
            if (grouped[i] || locked.contains(i) || counts[i] <= 0) {
                continue;
            }

            List<Integer> group = new ArrayList<>();
            group.add(i);
            grouped[i] = true;
            for (int j = i + 1; j < size; j++) {
                if (grouped[j] || locked.contains(j) || counts[j] <= 0) {
                    continue;
                }
                if (sameItem.test(i, j)) {
                    group.add(j);
                    grouped[j] = true;
                }
            }

            if (group.size() == 1) {
                continue;
            }

            int total = 0;
            for (int index : group) {
                total += counts[index];
            }

            for (int index : group) {
                // 容量取「堆叠上限」与「原有数量」的较大者：即便某个槽位因命令等原因超上限，也不丢物品
                int capacity = Math.max(maxCounts[index], counts[index]);
                int give = Math.min(total, capacity);
                result[index] = give;
                total -= give;
            }

            // 理论上不会走到这里（一组总容量 ≥ 总数），留作守恒兜底
            if (total > 0) {
                int last = group.get(group.size() - 1);
                result[last] += total;
            }
        }

        return result;
    }
}
