package me.easan.onekey.fabric.client;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * 客户端锁定槽位本地缓存：用于在容器界面实时高亮绘制锁定标记，并提供快速状态查询。
 */
public final class ClientLockedSlots {

    // containerKey -> Set<slotIndex>
    private static final Map<String, Set<Integer>> LOCKS = new ConcurrentHashMap<>();

    private ClientLockedSlots() {
    }

    /** 更新特定容器类型的锁定槽位集合。 */
    public static void setLocks(String containerKey, Collection<Integer> slots) {
        if (containerKey == null || slots == null) {
            return;
        }
        LOCKS.put(containerKey, new HashSet<>(slots));
    }

    /** 本地快速切换锁定状态，返回切换后的锁定布尔值。 */
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

    /** 判断指定键与索引是否被锁定。 */
    public static boolean isLocked(String containerKey, int slotIndex) {
        Set<Integer> slots = LOCKS.get(containerKey);
        return slots != null && slots.contains(slotIndex);
    }

    /** 获取指定容器类型的全部锁定槽位快照。 */
    public static Set<Integer> getLocks(String containerKey) {
        if (containerKey == null) {
            return java.util.Collections.emptySet();
        }
        Set<Integer> slots = LOCKS.get(containerKey);
        return (slots == null || slots.isEmpty()) ? java.util.Collections.emptySet() : Set.copyOf(slots);
    }

    /** 结合当前容器与槽位对象，判断该槽位是否已被锁定。 */
    public static boolean isSlotLocked(AbstractContainerMenu menu, Slot slot) {
        if (slot == null || menu == null) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return false;
        }

        if (slot.container == client.player.getInventory()) {
            return isLocked("player_inventory", slot.getContainerSlot());
        } else {
            return isLocked(getMenuTypeKey(menu), slot.index);
        }
    }

    /** 获取容器菜单类型的唯一标识串。 */
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

    /** 清空缓存。 */
    public static void clear() {
        LOCKS.clear();
    }
}
