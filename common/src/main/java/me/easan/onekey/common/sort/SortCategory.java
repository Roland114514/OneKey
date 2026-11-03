package me.easan.onekey.common.sort;

/**
 * 物品分类。排序时先按 {@link #weight()} 升序分组，再在组内按名称与数量排序。
 */
public enum SortCategory {
    TOOL(0),
    BLOCK(1),
    FOOD(2),
    POTION(3),
    MISC(4);

    private final int weight;

    SortCategory(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }
}
