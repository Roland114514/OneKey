package me.easan.onekey.common.sort;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 纯逻辑分类器：优先使用物品标签（tags），标签缺失时按物品 id 关键字兜底。
 *
 * <p>平台层把 {@code ItemStack} 的 id 与所属标签传进来即可，不依赖任何 Minecraft 类型。
 * 内部维护高效只读并发缓存，避免在背包整理时高频进行字符串截取与多次后缀比对。</p>
 */
public final class CategoryGuesser {

    private static final int MAX_CACHE_ENTRIES = 1024;
    private static final Map<String, SortCategory> TAG_CACHE = new ConcurrentHashMap<>(128);
    private static final Map<String, SortCategory> ID_CACHE = new ConcurrentHashMap<>(256);

    private CategoryGuesser() {
    }

    /**
     * @param itemId 物品注册 id，可为 null
     * @param tags   物品所属标签（如 {@code minecraft:tools}），可为 null
     */
    public static SortCategory guess(String itemId, Collection<String> tags) {
        if (tags != null) {
            for (String tag : tags) {
                SortCategory byTag = fromTag(tag);
                if (byTag != null) {
                    return byTag;
                }
            }
        }
        return fromId(itemId);
    }

    private static SortCategory fromTag(String rawTag) {
        if (rawTag == null || rawTag.isEmpty()) {
            return null;
        }
        SortCategory cached = TAG_CACHE.get(rawTag);
        if (cached != null) {
            return cached;
        }

        SortCategory computed = computeFromTag(rawTag);
        if (computed != null) {
            if (TAG_CACHE.size() < MAX_CACHE_ENTRIES) {
                TAG_CACHE.put(rawTag, computed);
            }
        }
        return computed;
    }

    private static SortCategory computeFromTag(String rawTag) {
        String tag = rawTag.toLowerCase(Locale.ROOT);
        if (tag.endsWith(":tools") || tag.endsWith(":swords") || tag.endsWith(":axes")
                || tag.endsWith(":pickaxes") || tag.endsWith(":shovels") || tag.endsWith(":hoes")
                || tag.endsWith(":weapons")) {
            return SortCategory.TOOL;
        }
        if (tag.endsWith(":food") || tag.endsWith(":foods")) {
            return SortCategory.FOOD;
        }
        if (tag.endsWith(":potions")) {
            return SortCategory.POTION;
        }
        if (tag.endsWith(":logs") || tag.endsWith(":planks") || tag.endsWith(":wool")
                || tag.endsWith(":stone_bricks") || tag.endsWith(":dirt") || tag.endsWith(":sand")
                || tag.endsWith(":terracotta") || tag.endsWith(":concrete")) {
            return SortCategory.BLOCK;
        }
        return null;
    }

    private static SortCategory fromId(String rawId) {
        if (rawId == null || rawId.isEmpty()) {
            return SortCategory.MISC;
        }
        SortCategory cached = ID_CACHE.get(rawId);
        if (cached != null) {
            return cached;
        }

        SortCategory computed = computeFromId(rawId);
        if (ID_CACHE.size() < MAX_CACHE_ENTRIES) {
            ID_CACHE.put(rawId, computed);
        }
        return computed;
    }

    private static SortCategory computeFromId(String rawId) {
        String id = rawId.toLowerCase(Locale.ROOT);
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;

        if (path.endsWith("_sword") || path.endsWith("_pickaxe") || path.endsWith("_axe")
                || path.endsWith("_shovel") || path.endsWith("_hoe") || path.equals("shears")
                || path.equals("fishing_rod") || path.equals("bow") || path.equals("crossbow")
                || path.equals("trident") || path.endsWith("_arrow") || path.endsWith("_helmet")
                || path.endsWith("_chestplate") || path.endsWith("_leggings") || path.endsWith("_boots")
                || path.equals("shield")) {
            return SortCategory.TOOL;
        }
        if (path.contains("potion") || path.equals("experience_bottle")) {
            return SortCategory.POTION;
        }
        if (path.endsWith("_log") || path.endsWith("_planks") || path.endsWith("_wool")
                || path.endsWith("_bricks") || path.endsWith("_terracotta") || path.endsWith("_concrete")
                || path.endsWith("_stone") || path.endsWith("_dirt") || path.endsWith("_sand")
                || path.endsWith("_block") || path.equals("cobblestone") || path.equals("gravel")) {
            return SortCategory.BLOCK;
        }
        if (path.equals("apple") || path.equals("bread") || path.equals("carrot")
                || path.equals("golden_carrot") || path.equals("potato") || path.equals("baked_potato")
                || path.equals("beef") || path.equals("cooked_beef") || path.equals("porkchop")
                || path.equals("cooked_porkchop") || path.endsWith("_stew") || path.endsWith("_soup")
                || path.endsWith("_cookie") || path.endsWith("_pie") || path.endsWith("_juice")) {
            return SortCategory.FOOD;
        }
        return SortCategory.MISC;
    }

    /** 数据包或世界重载时清理缓存。 */
    public static void clearCache() {
        TAG_CACHE.clear();
        ID_CACHE.clear();
    }
}
