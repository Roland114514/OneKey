package me.easan.onekey.common.inventory;

/**
 * 玩家背包槽位范围的**纯整数**定义（容器槽位号 0..40，不含任何 Minecraft 依赖，便于单测）。
 *
 * <p>布局：{@code 0..8} 快捷栏、{@code 9..35} 主背包、{@code 36..39} 盔甲、{@code 40} 副手。
 *
 * <p>本类的用途是集中回答两个问题：
 * <ul>
 *   <li>「这个槽位是否参与整理 / 快速堆叠 / 鼠标手势」——{@link #isIncluded(int, boolean)}；</li>
 *   <li>「这个槽位是否允许玩家手动加锁」——{@link #isPlayerInventorySlot(int)}。</li>
 * </ul>
 * 快捷栏是否参与由玩家在设置界面的两个开关决定；盔甲与副手无论开关如何都**永不参与**。
 */
public final class InventorySlots {

    /** 快捷栏起始槽位号。 */
    public static final int HOTBAR_START = 0;
    /** 主背包起始槽位号（也是快捷栏结束位置，不含边界）。 */
    public static final int MAIN_START = 9;
    /** 主背包结束位置（不含边界，即 9..35 共 27 格）。 */
    public static final int MAIN_END = 36;

    private InventorySlots() {
    }

    /**
     * 该容器槽位号是否参与整理 / 快速堆叠 / 鼠标手势。
     *
     * @param containerSlot 容器槽位号（玩家背包为 {@code Slot#getContainerSlot()}）
     * @param includeHotbar 玩家是否开启「包含快捷栏」开关
     */
    public static boolean isIncluded(int containerSlot, boolean includeHotbar) {
        if (containerSlot < HOTBAR_START || containerSlot >= MAIN_END) {
            // 越界、盔甲 36..39、副手 40 一律不参与
            return false;
        }
        return includeHotbar || containerSlot >= MAIN_START;
    }

    /** 遍历起点：包含快捷栏时从 0 开始，否则从 9 开始。 */
    public static int firstIncludedSlot(boolean includeHotbar) {
        return includeHotbar ? HOTBAR_START : MAIN_START;
    }

    /** 是否属于玩家背包（0..35），即可加锁的范围；盔甲与副手不参与任何功能，也不允许加锁。 */
    public static boolean isPlayerInventorySlot(int containerSlot) {
        return containerSlot >= HOTBAR_START && containerSlot < MAIN_END;
    }
}
