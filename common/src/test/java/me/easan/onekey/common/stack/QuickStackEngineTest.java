package me.easan.onekey.common.stack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QuickStackEngineTest {

    private List<SimulatedSlot> createPlayerSlots() {
        List<SimulatedSlot> slots = new ArrayList<>(36);
        for (int i = 0; i < 36; i++) {
            slots.add(SimulatedSlot.empty());
        }
        return slots;
    }

    private List<SimulatedSlot> createContainer(int size) {
        List<SimulatedSlot> slots = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            slots.add(SimulatedSlot.empty());
        }
        return slots;
    }

    private int totalItemCount(List<SimulatedSlot> slots) {
        int sum = 0;
        for (SimulatedSlot s : slots) {
            sum += s.count();
        }
        return sum;
    }

    @Test
    void testPhase1FillsExistingIncompleteStacks() {
        List<SimulatedSlot> player = createPlayerSlots();
        // 主背包第 9 格有 30 个圆石
        player.set(9, SimulatedSlot.of("minecraft:cobblestone", 30, 64));

        List<SimulatedSlot> chest = createContainer(27);
        // 箱子第 0 格已有 20 个圆石
        chest.set(0, SimulatedSlot.of("minecraft:cobblestone", 20, 64));

        int moved = QuickStackEngine.execute(player, Set.of(), List.of(chest), false, true, List.of(), List.of());

        assertEquals(30, moved);
        assertEquals(50, chest.get(0).count());
        assertTrue(player.get(9).isEmpty());
    }

    @Test
    void testNonStashModeDoesNotFillEmptySlotsOfUnrelatedItems() {
        List<SimulatedSlot> player = createPlayerSlots();
        player.set(9, SimulatedSlot.of("minecraft:diamond", 5, 64));

        List<SimulatedSlot> chest = createContainer(27);
        chest.set(0, SimulatedSlot.of("minecraft:cobblestone", 64, 64));

        // 仅快速堆叠（stashMode = false），箱子里只有圆石，没有钻石
        int moved = QuickStackEngine.execute(player, Set.of(), List.of(chest), false, true, List.of(), List.of());

        assertEquals(0, moved);
        assertEquals(5, player.get(9).count());
        assertTrue(chest.get(1).isEmpty());
    }

    @Test
    void testNonStashModeFillsEmptySlotsIfContainerAlreadyHasMatchingItem() {
        List<SimulatedSlot> player = createPlayerSlots();
        player.set(9, SimulatedSlot.of("minecraft:cobblestone", 30, 64));

        List<SimulatedSlot> chest = createContainer(27);
        // 箱子里已有满组圆石（64）
        chest.set(0, SimulatedSlot.of("minecraft:cobblestone", 64, 64));

        // 快速堆叠模式下，因为箱子已有圆石，多出的 30 个圆石允许开启新空槽存放
        int moved = QuickStackEngine.execute(player, Set.of(), List.of(chest), false, true, List.of(), List.of());

        assertEquals(30, moved);
        assertEquals(64, chest.get(0).count());
        assertEquals(30, chest.get(1).count());
        assertEquals("minecraft:cobblestone", chest.get(1).itemId());
        assertTrue(player.get(9).isEmpty());
    }

    @Test
    void testPhase2StashModeFillsEmptySlots() {
        List<SimulatedSlot> player = createPlayerSlots();
        player.set(9, SimulatedSlot.of("minecraft:diamond", 10, 64));

        List<SimulatedSlot> chest = createContainer(27);
        chest.set(0, SimulatedSlot.of("minecraft:dirt", 64, 64));

        // 一键存入全部（stashMode = true）
        int moved = QuickStackEngine.execute(player, Set.of(), List.of(chest), true, true, List.of(), List.of());

        assertEquals(10, moved);
        assertTrue(player.get(9).isEmpty());
        assertEquals("minecraft:diamond", chest.get(1).itemId());
        assertEquals(10, chest.get(1).count());
    }

    @Test
    void testHotbarSlotsAreNeverMoved() {
        List<SimulatedSlot> player = createPlayerSlots();
        // 快捷栏第 0 格有宝贵剑和食物
        player.set(0, SimulatedSlot.of("minecraft:diamond_sword", 1, 1));
        player.set(1, SimulatedSlot.of("minecraft:cooked_beef", 64, 64));

        List<SimulatedSlot> chest = createContainer(27);
        chest.set(0, SimulatedSlot.of("minecraft:cooked_beef", 10, 64));

        // 即使 stashMode = true，protectHotbar = true 下快捷栏物品绝对不移动
        int moved = QuickStackEngine.execute(player, Set.of(), List.of(chest), true, true, List.of(), List.of());

        assertEquals(0, moved);
        assertEquals(1, player.get(0).count());
        assertEquals(64, player.get(1).count());
        assertEquals(10, chest.get(0).count());
    }

    @Test
    void testLockedSlotsAreNeverMoved() {
        List<SimulatedSlot> player = createPlayerSlots();
        // 玩家锁定主背包第 15 格的火把
        player.set(15, SimulatedSlot.of("minecraft:torch", 64, 64));

        List<SimulatedSlot> chest = createContainer(27);
        chest.set(0, SimulatedSlot.of("minecraft:torch", 10, 64));

        int moved = QuickStackEngine.execute(player, Set.of(15), List.of(chest), true, true, List.of(), List.of());

        assertEquals(0, moved);
        assertEquals(64, player.get(15).count());
        assertEquals(10, chest.get(0).count());
    }

    @Test
    void testItemConservationTotalCountStrictlyConserved() {
        List<SimulatedSlot> player = createPlayerSlots();
        player.set(9, SimulatedSlot.of("minecraft:iron_ingot", 40, 64));
        player.set(10, SimulatedSlot.of("minecraft:gold_ingot", 25, 64));

        List<SimulatedSlot> chest1 = createContainer(5);
        chest1.set(0, SimulatedSlot.of("minecraft:iron_ingot", 50, 64));

        List<SimulatedSlot> chest2 = createContainer(5);
        chest2.set(0, SimulatedSlot.of("minecraft:gold_ingot", 60, 64));

        int beforeTotal = totalItemCount(player) + totalItemCount(chest1) + totalItemCount(chest2);

        int moved = QuickStackEngine.execute(player, Set.of(), List.of(chest1, chest2), true, true, List.of(), List.of());

        int afterTotal = totalItemCount(player) + totalItemCount(chest1) + totalItemCount(chest2);

        assertEquals(beforeTotal, afterTotal, "全流程总物品数量必须绝对守恒");
        assertTrue(moved > 0);
    }

    @Test
    void testBlacklistAndWhitelist() {
        List<SimulatedSlot> player = createPlayerSlots();
        player.set(9, SimulatedSlot.of("minecraft:dirt", 64, 64));
        player.set(10, SimulatedSlot.of("minecraft:stone", 64, 64));

        List<SimulatedSlot> chest = createContainer(27);

        // 黑名单禁止 dirt
        int moved = QuickStackEngine.execute(player, Set.of(), List.of(chest), true, true, List.of(), List.of("minecraft:dirt"));

        assertEquals(64, moved);
        assertEquals(64, player.get(9).count(), "黑名单物品不转移");
        assertTrue(player.get(10).isEmpty(), "非黑名单物品成功转移");
    }
}
