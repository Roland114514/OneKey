package me.easan.onekey.common.net;

import me.easan.onekey.common.sort.SortRule;

/**
 * 客户端 → 服务端：请求整理当前打开的容器。
 *
 * @param syncId         容器同步 id（服务端会校验玩家确实打开了该容器）
 * @param mode           排序模式（0 = 自动, 1 = 仅容器, 2 = 仅玩家主背包）
 * @param optionsBitmask 客户端携带的个性化整理配置位掩码（0 表示未指定，服务端使用自身配置）
 */
public record SortRequest(int syncId, int mode, int optionsBitmask) {
    public static final int MODE_AUTO = 0;
    public static final int MODE_CONTAINER = 1;
    public static final int MODE_PLAYER = 2;

    public static final int FLAG_CUSTOM_OPTIONS = 1;
    public static final int FLAG_MERGE_STACKS = 2;
    public static final int FLAG_INCLUDE_HOTBAR = 4;
    public static final int FLAG_GROUP_BY_CATEGORY = 8;
    public static final int FLAG_SORT_BY_NAME = 16;
    public static final int FLAG_SORT_BY_COUNT_DESC = 32;

    public static final int NO_CUSTOM_OPTIONS = 0;

    public SortRequest(int syncId, int mode) {
        this(syncId, mode, NO_CUSTOM_OPTIONS);
    }

    public static int packOptions(boolean mergeStacks, boolean includeHotbar,
                                  boolean groupByCategory, boolean sortByName, boolean sortByCountDesc) {
        int mask = FLAG_CUSTOM_OPTIONS;
        if (mergeStacks) mask |= FLAG_MERGE_STACKS;
        if (includeHotbar) mask |= FLAG_INCLUDE_HOTBAR;
        if (groupByCategory) mask |= FLAG_GROUP_BY_CATEGORY;
        if (sortByName) mask |= FLAG_SORT_BY_NAME;
        if (sortByCountDesc) mask |= FLAG_SORT_BY_COUNT_DESC;
        return mask;
    }

    public boolean hasCustomOptions() {
        return (optionsBitmask & FLAG_CUSTOM_OPTIONS) != 0;
    }

    public boolean mergeStacks() {
        return (optionsBitmask & FLAG_MERGE_STACKS) != 0;
    }

    public boolean includeHotbar() {
        return (optionsBitmask & FLAG_INCLUDE_HOTBAR) != 0;
    }

    public boolean groupByCategory() {
        return (optionsBitmask & FLAG_GROUP_BY_CATEGORY) != 0;
    }

    public boolean sortByName() {
        return (optionsBitmask & FLAG_SORT_BY_NAME) != 0;
    }

    public boolean sortByCountDesc() {
        return (optionsBitmask & FLAG_SORT_BY_COUNT_DESC) != 0;
    }

    public SortRule toRule() {
        SortRule rule = new SortRule();
        rule.groupByCategory = groupByCategory();
        rule.sortByName = sortByName();
        rule.sortByCountDesc = sortByCountDesc();
        return rule;
    }
}
