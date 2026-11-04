package me.easan.onekey.common.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 快捷栏包含开关的槽位范围纯逻辑测试。 */
class InventorySlotsTest {

    @Test
    void hotbarExcludedWhenToggleIsOff() {
        for (int slot = 0; slot < 9; slot++) {
            assertFalse(InventorySlots.isIncluded(slot, false), "快捷栏槽位 " + slot + " 不应参与");
        }
        for (int slot = 9; slot < 36; slot++) {
            assertTrue(InventorySlots.isIncluded(slot, true), "主背包槽位 " + slot + " 应参与");
            assertTrue(InventorySlots.isIncluded(slot, false), "主背包槽位 " + slot + " 应参与");
        }
        assertEquals(9, InventorySlots.firstIncludedSlot(false));
    }

    @Test
    void hotbarIncludedWhenToggleIsOn() {
        for (int slot = 0; slot < 36; slot++) {
            assertTrue(InventorySlots.isIncluded(slot, true), "槽位 " + slot + " 应参与");
        }
        assertEquals(0, InventorySlots.firstIncludedSlot(true));
    }

    @Test
    void armorAndOffhandNeverParticipate() {
        for (int slot = 36; slot <= 40; slot++) {
            assertFalse(InventorySlots.isIncluded(slot, true), "盔甲/副手槽位 " + slot + " 不应参与");
        }
        assertFalse(InventorySlots.isIncluded(-1, true));
    }

    @Test
    void everyPlayerslotIsLockableIncludingHotbar() {
        for (int slot = 0; slot < 36; slot++) {
            assertTrue(InventorySlots.isPlayerInventorySlot(slot), "槽位 " + slot + " 应可加锁");
        }
        for (int slot = 36; slot <= 40; slot++) {
            assertFalse(InventorySlots.isPlayerInventorySlot(slot), "盔甲/副手槽位 " + slot + " 不可加锁");
        }
    }
}
