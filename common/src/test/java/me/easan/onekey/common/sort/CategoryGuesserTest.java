package me.easan.onekey.common.sort;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class CategoryGuesserTest {

    @Test
    void tagsTakePriorityOverIdHeuristics() {
        assertEquals(SortCategory.TOOL, CategoryGuesser.guess("minecraft:stick", List.of("minecraft:tools")));
    }

    @Test
    void recognisesCommonItemsById() {
        assertEquals(SortCategory.TOOL, CategoryGuesser.guess("minecraft:diamond_sword", List.of()));
        assertEquals(SortCategory.TOOL, CategoryGuesser.guess("minecraft:iron_pickaxe", List.of()));
        assertEquals(SortCategory.BLOCK, CategoryGuesser.guess("minecraft:oak_planks", List.of()));
        assertEquals(SortCategory.BLOCK, CategoryGuesser.guess("minecraft:cobblestone", List.of()));
        assertEquals(SortCategory.FOOD, CategoryGuesser.guess("minecraft:apple", List.of()));
        assertEquals(SortCategory.FOOD, CategoryGuesser.guess("minecraft:baked_potato", List.of()));
        assertEquals(SortCategory.POTION, CategoryGuesser.guess("minecraft:splash_potion", List.of()));
    }

    @Test
    void unknownItemsFallBackToMisc() {
        assertEquals(SortCategory.MISC, CategoryGuesser.guess("minecraft:diamond", List.of("minecraft:gems")));
        assertEquals(SortCategory.MISC, CategoryGuesser.guess(null, null));
        assertEquals(SortCategory.MISC, CategoryGuesser.guess("", List.of()));
    }
}
