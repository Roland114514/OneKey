package me.easan.onekey.common.sort;

/**
 * 与加载器无关的槽位视图。
 *
 * <p>平台适配层负责把 {@code ItemStack} 转换成这个最小信息集合，排序算法只依赖本类型，
 * 因此核心逻辑可以脱离 Minecraft 运行时做单元测试，也可以在 Fabric / NeoForge 之间共享。
 *
 * @param itemId   物品注册 id，例如 {@code minecraft:diamond_sword}；空槽位为空串
 * @param count    堆叠数量，空槽位为 0
 * @param category 物品分类，由 {@link CategoryGuesser} 或平台层提供
 */
public record StackView(String itemId, int count, SortCategory category) {

    /** 空槽位常量。 */
    public static final StackView EMPTY = new StackView("", 0, SortCategory.MISC);

    public StackView {
        if (itemId == null) {
            itemId = "";
        }
        if (category == null) {
            category = SortCategory.MISC;
        }
        if (count < 0) {
            count = 0;
        }
    }

    /** 是否为空格。 */
    public boolean isEmpty() {
        return itemId.isEmpty() || count <= 0;
    }
}
