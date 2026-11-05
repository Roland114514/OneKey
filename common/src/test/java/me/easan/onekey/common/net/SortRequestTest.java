package me.easan.onekey.common.net;

import static org.junit.jupiter.api.Assertions.*;

import me.easan.onekey.common.sort.SortRule;
import org.junit.jupiter.api.Test;

class SortRequestTest {

    @Test
    void defaultConstructorHasNoCustomOptions() {
        SortRequest request = new SortRequest(1, SortRequest.MODE_CONTAINER);
        assertEquals(1, request.syncId());
        assertEquals(SortRequest.MODE_CONTAINER, request.mode());
        assertFalse(request.hasCustomOptions());
        assertEquals(0, request.optionsBitmask());
    }

    @Test
    void packAndUnpackOptions() {
        int mask = SortRequest.packOptions(true, false, true, true, false);
        SortRequest request = new SortRequest(5, SortRequest.MODE_PLAYER, mask);

        assertTrue(request.hasCustomOptions());
        assertTrue(request.mergeStacks());
        assertFalse(request.includeHotbar());
        assertTrue(request.groupByCategory());
        assertTrue(request.sortByName());
        assertFalse(request.sortByCountDesc());

        SortRule rule = request.toRule();
        assertTrue(rule.groupByCategory);
        assertTrue(rule.sortByName);
        assertFalse(rule.sortByCountDesc);
    }

    @Test
    void packDifferentOptions() {
        int mask = SortRequest.packOptions(false, true, false, false, true);
        SortRequest request = new SortRequest(2, SortRequest.MODE_AUTO, mask);

        assertTrue(request.hasCustomOptions());
        assertFalse(request.mergeStacks());
        assertTrue(request.includeHotbar());
        assertFalse(request.groupByCategory());
        assertFalse(request.sortByName());
        assertTrue(request.sortByCountDesc());

        SortRule rule = request.toRule();
        assertFalse(rule.groupByCategory);
        assertFalse(rule.sortByName);
        assertTrue(rule.sortByCountDesc);
    }
}
