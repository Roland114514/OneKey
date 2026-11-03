package me.easan.onekey.fabric.util;

import java.util.List;
import me.easan.onekey.common.sort.CategoryGuesser;
import me.easan.onekey.common.sort.SortCategory;
import me.easan.onekey.common.sort.StackView;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Minecraft ItemStack 与通用 StackView 互转工具。
 */
public final class ItemConversion {

    private ItemConversion() {
    }

    public static StackView toStackView(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return StackView.EMPTY;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String itemId = id != null ? id.toString() : "";
        List<String> tags = stack.typeHolder().tags()
                .map(tagKey -> tagKey.location().toString())
                .toList();
        SortCategory category = CategoryGuesser.guess(itemId, tags);
        return new StackView(itemId, stack.getCount(), category);
    }
}
