package me.easan.onekey.common.sort;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
