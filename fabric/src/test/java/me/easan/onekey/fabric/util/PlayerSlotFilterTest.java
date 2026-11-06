package me.easan.onekey.fabric.util;

import java.lang.reflect.Constructor;
import net.minecraft.world.inventory.Slot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerSlotFilterTest {

    @Test
    void unwrapNullReturnsNull() {
        assertNull(PlayerSlotFilter.unwrap(null));
    }

    @Test
    void unwrapPlainSlotReturnsSameInstance() {
        Slot slot = new Slot(null, 0, 10, 20);
        assertSame(slot, PlayerSlotFilter.unwrap(slot));
    }

    @Test
    void unwrapSlotWrapperReturnsUnderlyingTarget() {
        Slot realSlot = new Slot(null, 5, 10, 20);
        try {
            Class<?> wrapperClass = Class.forName("net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen$SlotWrapper");
            Constructor<?> ctor = wrapperClass.getDeclaredConstructor(Slot.class, int.class, int.class, int.class);
            ctor.setAccessible(true);
            Slot wrapperSlot = (Slot) ctor.newInstance(realSlot, 36, 10, 20);

            // 原始 wrapper 报告的 containerSlot 是 36（InventoryMenu 下标）
            assertEquals(36, wrapperSlot.getContainerSlot());
            // 解包后拿到真实 slot
            Slot unwrapped = PlayerSlotFilter.unwrap(wrapperSlot);
            assertSame(realSlot, unwrapped);
            assertEquals(5, unwrapped.getContainerSlot());
        } catch (ClassNotFoundException e) {
            // 在无客户端环境（如部分 CI）下安全跳过或原样返回
        } catch (Exception e) {
            fail("Failed to test SlotWrapper unwrapping: " + e.getMessage());
        }
    }
}
