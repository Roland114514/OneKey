package me.easan.onekey.fabric.util;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import me.easan.onekey.common.sort.CategoryGuesser;
import me.easan.onekey.common.sort.SortCategory;
import me.easan.onekey.common.sort.StackView;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Minecraft ItemStack 与通用 StackView 互转工具。
 * 针对高频调用提供静态类别与 ID 缓存，消除重复 Stream 与字符串对象分配（零临时垃圾，O(1) 纳秒级查询）。
 */
public final class ItemConversion {

    private static final Map<Item, SortCategory> CATEGORY_CACHE = new ConcurrentHashMap<>();
    private static final Map<Item, String> ITEM_ID_CACHE = new ConcurrentHashMap<>();

    private ItemConversion() {
    }

    public static String getItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        return ITEM_ID_CACHE.computeIfAbsent(stack.getItem(), item -> {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            return id != null ? id.toString() : "";
        });
    }

    public static SortCategory getCategory(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return SortCategory.MISC;
        }
        return CATEGORY_CACHE.computeIfAbsent(stack.getItem(), item -> {
            String itemId = getItemId(stack);
            List<String> tags = stack.typeHolder().tags()
                    .map(tagKey -> tagKey.location().toString())
                    .toList();
            return CategoryGuesser.guess(itemId, tags);
        });
    }

    public static StackView toStackView(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return StackView.EMPTY;
        }
        String itemId = getItemId(stack);
        SortCategory category = getCategory(stack);
        return new StackView(itemId, stack.getCount(), category);
    }

    /** 数据包重载时清理缓存。 */
    public static void clearCache() {
        CATEGORY_CACHE.clear();
        ITEM_ID_CACHE.clear();
    }
}
