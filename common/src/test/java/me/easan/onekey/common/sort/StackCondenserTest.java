package me.easan.onekey.common.sort;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import org.junit.jupiter.api.Test;

class StackCondenserTest {

    /** 简化模型：下标 0..n-1 的「物品 id」，相同即同物品。 */
    private static StackCondenser.SameItem sameByIds(String... ids) {
        return (a, b) -> ids[a].equals(ids[b]);
    }

    @Test
    void mergesSplitStacksIntoEarlierSlots() {
        int[] counts = {16, 10};
        int[] max = {64, 64};
        int[] result = StackCondenser.condense(counts, max, Set.of(), sameByIds("stairs", "stairs"));
        assertArrayEquals(new int[]{26, 0}, result);
    }

    @Test
    void respectsMaxStackSizeAndOverflowsToNextSlot() {
        int[] counts = {40, 40};
        int[] max = {64, 64};
        int[] result = StackCondenser.condense(counts, max, Set.of(), sameByIds("pearl", "pearl"));
        assertArrayEquals(new int[]{64, 16}, result);
    }

    @Test
    void doesNotMergeDifferentItems() {
        int[] counts = {16, 10};
        int[] max = {64, 64};
        int[] result = StackCondenser.condense(counts, max, Set.of(), sameByIds("stairs", "slab"));
        assertArrayEquals(new int[]{16, 10}, result);
    }

    @Test
    void lockedSlotsAreNeitherSourcesNorTargets() {
        int[] counts = {16, 10, 5};
        int[] max = {64, 64, 64};
        StackCondenser.SameItem same = sameByIds("stairs", "stairs", "stairs");
        int[] result = StackCondenser.condense(counts, max, Set.of(0), same);
        // 0 号被锁：16 原样；1+2 合并为 15，优先填 1 号
        assertArrayEquals(new int[]{16, 15, 0}, result);
        assertEquals(31, result[0] + result[1] + result[2]);
    }

    @Test
    void keepsTotalCountConserved() {
        int[] counts = {3, 0, 6, 64, 2, 1};
        int[] max = {64, 64, 64, 64, 16, 1};
        StackCondenser.SameItem same = sameByIds("a", "b", "a", "c", "a", "d");
        int[] result = StackCondenser.condense(counts, max, Set.of(), same);
        assertEquals(76, java.util.Arrays.stream(result).sum());
        assertEquals(3 + 6 + 2, result[0] + result[2] + result[4]);
    }

    @Test
    void emptySlotsAreIgnored() {
        int[] counts = {0, 0};
        int[] max = {64, 64};
        int[] result = StackCondenser.condense(counts, max, Set.of(), sameByIds("x", "x"));
        assertArrayEquals(new int[]{0, 0}, result);
    }

    @Test
    void unstackableItemsStayInTheirOwnSlots() {
        int[] counts = {1, 1};
        int[] max = {1, 1};
        int[] result = StackCondenser.condense(counts, max, Set.of(), sameByIds("sword", "sword"));
        // 上限 1，两把剑合成不了，必须各自保留
        assertArrayEquals(new int[]{1, 1}, result);
    }
}
