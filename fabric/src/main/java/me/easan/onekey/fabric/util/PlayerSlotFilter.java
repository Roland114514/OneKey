package me.easan.onekey.fabric.util;

import me.easan.onekey.common.inventory.InventorySlots;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ArmorSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 玩家背包槽位筛选。
 *
 * <p>为什么不能只信 {@link Slot#getContainerSlot()}：26.3 的创造模式「生存模式物品栏」页签
 * （{@code CreativeModeInventoryScreen$ItemPickerMenu}）会用 {@code SlotWrapper} 包装背包槽位，
 * 装备槽在其中报告的容器槽位是 5..8（InventoryMenu 下标）而不是 36..39（Inventory 下标）。
 * 只按 0..35 判断就会把头盔/胸甲/护腿/靴子当成主背包槽位参与排序，整理结果直接穿到身上。
 * 这里再用装备槽的两个稳定特征兜底：堆叠上限为 1、且只接受可装备物品。</p>
 */
public final class PlayerSlotFilter {

    private PlayerSlotFilter() {
    }

    /** 是否是「可安全参与整理/手势」的玩家背包槽位。 */
    public static boolean isPlainPlayerInventorySlot(Player player, Slot slot, boolean includeHotbar) {
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
