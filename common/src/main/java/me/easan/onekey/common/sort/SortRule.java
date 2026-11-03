package me.easan.onekey.common.sort;

/**
 * 排序规则，与配置文件中的 {@code sort} 节一一对应。
 */
public final class SortRule {

    /** 是否按分类分组（关闭后直接按名称排）。 */
    public boolean groupByCategory = true;

    /** 是否按物品 id 名称排序。 */
    public boolean sortByName = true;

    /** 是否按数量从多到少排序。 */
    public boolean sortByCountDesc = true;

    public SortRule copy() {
        SortRule copy = new SortRule();
        copy.groupByCategory = this.groupByCategory;
        copy.sortByName = this.sortByName;
        copy.sortByCountDesc = this.sortByCountDesc;
        return copy;
    }
}
