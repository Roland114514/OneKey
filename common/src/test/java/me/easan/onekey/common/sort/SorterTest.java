package me.easan.onekey.common.sort;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SorterTest {

    private static StackView stack(String id, int count, SortCategory category) {
        return new StackView(id, count, category);
    }

    @Test
    void sortsByCategoryThenName() {
        List<StackView> slots = List.of(
                stack("minecraft:cobblestone", 64, SortCategory.BLOCK),
                stack("minecraft:diamond_sword", 1, SortCategory.TOOL),
                stack("minecraft:apple", 3, SortCategory.FOOD),
                StackView.EMPTY,
                stack("minecraft:stone", 64, SortCategory.BLOCK));

        int[] permutation = Sorter.permutation(slots, Set.of(), new SortRule());
        List<StackView> sorted = Sorter.apply(slots, permutation);

        assertEquals(
                List.of("minecraft:diamond_sword", "minecraft:cobblestone", "minecraft:stone", "minecraft:apple"),
                sorted.subList(0, 4).stream().map(StackView::itemId).toList());
        assertTrue(sorted.get(4).isEmpty());
    }

    @Test
    void keepsLockedSlotsUntouched() {
        List<StackView> slots = List.of(
                stack("minecraft:apple", 1, SortCategory.FOOD),
                stack("minecraft:diamond_sword", 1, SortCategory.TOOL),
                stack("minecraft:stone", 5, SortCategory.BLOCK));

        int[] permutation = Sorter.permutation(slots, Set.of(1), new SortRule());
        assertEquals(1, permutation[1]);

        List<StackView> sorted = Sorter.apply(slots, permutation);
        assertEquals("minecraft:diamond_sword", sorted.get(1).itemId());
        assertEquals("minecraft:stone", sorted.get(0).itemId());
        assertEquals("minecraft:apple", sorted.get(2).itemId());
    }

    @Test
    void pushesEmptySlotsToTheEnd() {
        List<StackView> slots = List.of(
                StackView.EMPTY,
                stack("minecraft:apple", 2, SortCategory.FOOD),
                StackView.EMPTY);

        List<StackView> sorted = Sorter.apply(slots, Sorter.permutation(slots, Set.of(), new SortRule()));

        assertEquals("minecraft:apple", sorted.get(0).itemId());
        assertTrue(sorted.get(1).isEmpty());
        assertTrue(sorted.get(2).isEmpty());
    }

    @Test
    void allLockedSlotsProduceIdentityPermutation() {
        List<StackView> slots = List.of(
                stack("minecraft:apple", 1, SortCategory.FOOD),
                StackView.EMPTY);

        int[] permutation = Sorter.permutation(slots, Set.of(0, 1), new SortRule());

        assertArrayEquals(new int[] {0, 1}, permutation);
    }

    @Test
    void permutationIsAlwaysABijection() {
        List<StackView> slots = List.of(
                stack("minecraft:dirt", 64, SortCategory.BLOCK),
                stack("minecraft:apple", 3, SortCategory.FOOD),
                StackView.EMPTY,
                stack("minecraft:iron_pickaxe", 1, SortCategory.TOOL),
                stack("minecraft:stone", 12, SortCategory.BLOCK),
                StackView.EMPTY);

        int[] permutation = Sorter.permutation(slots, Set.of(4), new SortRule());

        boolean[] seen = new boolean[slots.size()];
        for (int source : permutation) {
            assertFalse(seen[source], "置换出现重复源槽位: " + source);
            seen[source] = true;
        }
        for (boolean value : seen) {
            assertTrue(value);
        }
        assertEquals(4, permutation[4]);
    }

    @Test
    void equalStacksKeepRelativeOrder() {
        List<StackView> slots = List.of(
                stack("minecraft:dirt", 64, SortCategory.BLOCK),
                stack("minecraft:dirt", 64, SortCategory.BLOCK));

        assertArrayEquals(new int[] {0, 1}, Sorter.permutation(slots, Set.of(), new SortRule()));
    }

    @Test
    void hotbarSlotsSeparationMaintainsHotbarIntegrity() {
        // 模拟 27 格主背包 + 9 格快捷栏
        List<StackView> mainInventory = List.of(
                stack("minecraft:cobblestone", 64, SortCategory.BLOCK),
                stack("minecraft:apple", 5, SortCategory.FOOD),
                StackView.EMPTY
        );
        List<StackView> hotbar = List.of(
                stack("minecraft:diamond_sword", 1, SortCategory.TOOL),
                stack("minecraft:torch", 64, SortCategory.BLOCK)
        );

        // 仅对主背包调用 Sorter，快捷栏完全隔离不参与
        int[] perm = Sorter.permutation(mainInventory, Set.of(), new SortRule());
        List<StackView> sortedMain = Sorter.apply(mainInventory, perm);

        assertEquals("minecraft:cobblestone", sortedMain.get(0).itemId());
        assertEquals("minecraft:apple", sortedMain.get(1).itemId());
        assertTrue(sortedMain.get(2).isEmpty());

        // 验证快捷栏保持不变
        assertEquals("minecraft:diamond_sword", hotbar.get(0).itemId());
        assertEquals("minecraft:torch", hotbar.get(1).itemId());
    }

    @Test
    void resolveLockedPositionsMapsContainerIndicesToViewPositions() {
        // 玩家主背包 27 格视图，对应容器槽位号 9..35（差 9 的偏移）
        List<Integer> slotLockKeys = List.of(9, 10, 11, 12);
        Set<Integer> lockedKeys = Set.of(10, 12);

        Set<Integer> positions = Sorter.resolveLockedPositions(slotLockKeys, lockedKeys);

        assertEquals(Set.of(1, 3), positions);
    }

    @Test
    void permutationKeepsItemAtMappedLockedPosition() {
        // 27 格主背包视图（索引 0..26 对应容器槽位号 9..35）
        List<StackView> slots = new ArrayList<>();
        for (int i = 0; i < 27; i++) {
            slots.add(StackView.EMPTY);
        }
        slots.set(0, stack("minecraft:diamond_sword", 1, SortCategory.TOOL));
        slots.set(3, stack("minecraft:diamond", 5, SortCategory.MISC)); // 容器槽位号 12
        slots.set(4, stack("minecraft:apple", 3, SortCategory.FOOD));

        // 视图 0..26 对应的容器槽位号为 9..35
        List<Integer> slotLockKeys = new ArrayList<>();
        for (int i = 9; i < 36; i++) {
            slotLockKeys.add(i);
        }

        Set<Integer> lockedPositions = Sorter.resolveLockedPositions(slotLockKeys, Set.of(12));
        List<StackView> sorted = Sorter.apply(slots, Sorter.permutation(slots, lockedPositions, new SortRule()));

        // 容器槽位号 12 → 视图 3 被锁定，物品保持原位
        assertEquals("minecraft:diamond", sorted.get(3).itemId());
    }
}
