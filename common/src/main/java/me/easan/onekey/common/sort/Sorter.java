package me.easan.onekey.common.sort;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 一键整理的核心算法：输入槽位视图与锁定槽位，输出「置换数组」。
 *
 * <p>置换语义：{@code result[目标槽位] = 源槽位}，平台层用它把物品移动到新位置。
 * 锁定槽位（例如玩家指定的热键栏）在置换中映射到自身。
 */
public final class Sorter {

    private Sorter() {
    }

    /**
     * 计算排序置换。
     *
     * @param slots       当前所有槽位（索引即槽位号），不可为 null
     * @param lockedSlots 锁定槽位集合，可为 null
     * @param rule        排序规则，为 null 时使用默认规则
     * @return 长度为 {@code slots.size()} 的置换数组
     */
    public static int[] permutation(List<StackView> slots, Set<Integer> lockedSlots, SortRule rule) {
        Objects.requireNonNull(slots, "slots");
        SortRule effectiveRule = rule == null ? new SortRule() : rule;
        Set<Integer> locked = lockedSlots == null ? Set.of() : lockedSlots;

        int size = slots.size();
        int[] result = new int[size];

        List<Integer> movable = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            if (locked.contains(i)) {
                result[i] = i;
            } else {
                movable.add(i);
            }
        }

        Comparator<Integer> comparator = comparator(slots, effectiveRule);
        List<Integer> nonEmpty = new ArrayList<>();
        List<Integer> empty = new ArrayList<>();
        for (int index : movable) {
            if (slots.get(index).isEmpty()) {
                empty.add(index);
            } else {
                nonEmpty.add(index);
            }
        }
        nonEmpty.sort(comparator);

        List<Integer> ordered = new ArrayList<>(movable.size());
        ordered.addAll(nonEmpty);
        ordered.addAll(empty);

        for (int position = 0; position < movable.size(); position++) {
            result[movable.get(position)] = ordered.get(position);
        }
        return result;
    }

    /**
     * 按置换生成新的槽位列表（纯逻辑，便于单元测试与平台层复用）。
     */
    public static List<StackView> apply(List<StackView> slots, int[] permutation) {
        Objects.requireNonNull(slots, "slots");
        Objects.requireNonNull(permutation, "permutation");
        if (slots.size() != permutation.length) {
            throw new IllegalArgumentException("slots 与 permutation 长度不一致");
        }
        List<StackView> result = new ArrayList<>(slots.size());
        for (int source : permutation) {
            result.add(slots.get(source));
        }
        return result;
    }

    /**
     * 把「锁定键」映射成视图下标集合（纯逻辑，供平台层把容器槽位号换算为视图下标）。
     *
     * @param slotLockKeys 与待排序视图一一对应：第 i 个元素是第 i 个视图槽位在锁定存储中的键
     * @param lockedKeys   当前容器类型下已锁定的键集合，可为 null
     * @return 应传给 {@link #permutation} 的视图下标集合
     */
    public static Set<Integer> resolveLockedPositions(List<Integer> slotLockKeys, Set<Integer> lockedKeys) {
        Objects.requireNonNull(slotLockKeys, "slotLockKeys");
        Set<Integer> effective = lockedKeys == null ? Set.of() : lockedKeys;
        Set<Integer> positions = new HashSet<>();
        for (int i = 0; i < slotLockKeys.size(); i++) {
            Integer key = slotLockKeys.get(i);
            if (key != null && effective.contains(key)) {
                positions.add(i);
            }
        }
        return positions;
    }

    private static Comparator<Integer> comparator(List<StackView> slots, SortRule rule) {
        return (left, right) -> {
            StackView a = slots.get(left);
            StackView b = slots.get(right);

            if (rule.groupByCategory) {
                int byCategory = Integer.compare(a.category().weight(), b.category().weight());
                if (byCategory != 0) {
                    return byCategory;
                }
            }
            if (rule.sortByName) {
                int byName = a.itemId().compareTo(b.itemId());
                if (byName != 0) {
                    return byName;
                }
            }
            if (rule.sortByCountDesc) {
                int byCount = Integer.compare(b.count(), a.count());
                if (byCount != 0) {
                    return byCount;
                }
            }
            return Integer.compare(left, right);
        };
    }
}
