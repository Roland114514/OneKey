package me.easan.onekey.fabric.util;

import java.lang.reflect.Field;
import me.easan.onekey.common.inventory.InventorySlots;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ArmorSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 玩家背包槽位筛选。
 *
 * <p>兼容创造模式：26.3 的创造模式「生存模式物品栏」页签
 * （{@code CreativeModeInventoryScreen$ItemPickerMenu}）会用 {@code SlotWrapper} 包装背包槽位。
 * 通过反射缓存的 {@code target} 字段自动解包至真实底层 {@link Slot}，获得精确的物理槽位号（0..8 快捷栏、9..35 主背包），
 * 并正确识别底层 {@link ArmorSlot} 与副手槽，杜绝错位和幽灵物品。</p>
 */
public final class PlayerSlotFilter {

    private static final Field SLOT_WRAPPER_TARGET;

    static {
        Field field = null;
        try {
            Class<?> clazz = Class.forName("net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen$SlotWrapper");
            for (Field f : clazz.getDeclaredFields()) {
                if (Slot.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    field = f;
                    break;
                }
            }
        } catch (Throwable ignored) {
        }
        SLOT_WRAPPER_TARGET = field;
    }

    private PlayerSlotFilter() {
    }

    /**
     * 若槽位属于创造模式生存页签的 {@code SlotWrapper}，递归解包获取底层的真实 {@link Slot}；
     * 否则原样返回。
     */
    public static Slot unwrap(Slot slot) {
        if (slot == null) {
            return null;
        }
        if (SLOT_WRAPPER_TARGET != null && SLOT_WRAPPER_TARGET.getDeclaringClass().isInstance(slot)) {
            try {
                Object target = SLOT_WRAPPER_TARGET.get(slot);
                if (target instanceof Slot targetSlot) {
                    return unwrap(targetSlot);
                }
            } catch (Throwable ignored) {
            }
        }
        return slot;
    }

    /** 是否是「可安全参与整理/手势」的玩家背包槽位。 */
    public static boolean isPlainPlayerInventorySlot(Player player, Slot rawSlot, boolean includeHotbar) {
        Slot slot = unwrap(rawSlot);
        if (player == null || slot == null || slot.container != player.getInventory()) {
            return false;
        }
        if (slot instanceof ArmorSlot) {
            return false;
        }
        if (!InventorySlots.isIncluded(slot.getContainerSlot(), includeHotbar)) {
            return false;
        }
        return !isEquipmentLike(slot);
    }

    private static boolean isEquipmentLike(Slot slot) {
        if (slot.getMaxStackSize() <= 1) {
            return true;
        }
        try {
            return !slot.mayPlace(new ItemStack(Items.STONE));
        } catch (RuntimeException ignored) {
            return true;
        }
    }
}
