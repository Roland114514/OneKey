package me.easan.onekey.neoforge.client;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import me.easan.onekey.neoforge.util.PlayerSlotFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * 客户端锁定槽位本地缓存（NeoForge）。
 */
public final class ClientLockedSlots {

    private static final Map<String, Set<Integer>> LOCKS = new ConcurrentHashMap<>();

    private ClientLockedSlots() {
    }

    public static void setLocks(String containerKey, Collection<Integer> slots) {
        if (containerKey == null || slots == null) {
            return;
        }
        LOCKS.put(containerKey, new HashSet<>(slots));
    }

    public static boolean toggle(String containerKey, int slotIndex) {
        Set<Integer> slots = LOCKS.computeIfAbsent(containerKey, k -> new HashSet<>());
        if (slots.contains(slotIndex)) {
            slots.remove(slotIndex);
            return false;
        } else {
            slots.add(slotIndex);
            return true;
        }
    }

    public static boolean isLocked(String containerKey, int slotIndex) {
        Set<Integer> slots = LOCKS.get(containerKey);
        return slots != null && slots.contains(slotIndex);
    }

    public static Set<Integer> getLocks(String containerKey) {
        if (containerKey == null) {
            return java.util.Collections.emptySet();
        }
        Set<Integer> slots = LOCKS.get(containerKey);
        return (slots == null || slots.isEmpty()) ? java.util.Collections.emptySet() : Set.copyOf(slots);
    }

    public static boolean isSlotLocked(AbstractContainerMenu menu, Slot slot) {
        if (slot == null || menu == null) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return false;
        }

        Slot realSlot = PlayerSlotFilter.unwrap(slot);
        if (realSlot.container == client.player.getInventory()) {
            return isLocked("player_inventory", realSlot.getContainerSlot());
        } else {
            return isLocked(getMenuTypeKey(menu), slot.index);
        }
    }

    public static String getMenuTypeKey(AbstractContainerMenu menu) {
        if (menu == null) {
            return "generic_container";
        }
        if (menu instanceof net.minecraft.world.inventory.InventoryMenu) {
            return "player_inventory";
        }
        try {
            net.minecraft.world.inventory.MenuType<?> type = menu.getType();
            if (type == null) {
                return "generic_container";
            }
            Identifier menuType = BuiltInRegistries.MENU.getKey(type);
            return menuType != null ? menuType.toString() : "generic_container";
        } catch (Throwable ignored) {
            return "generic_container";
        }
    }

    public static void clear() {
        LOCKS.clear();
    }
}
