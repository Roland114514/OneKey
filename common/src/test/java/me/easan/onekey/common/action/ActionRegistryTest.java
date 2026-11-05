package me.easan.onekey.common.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ActionRegistryTest {

    @Test
    void containsAllDefaultActions() {
        assertTrue(ActionRegistry.get(ActionId.SORT).isPresent());
        assertTrue(ActionRegistry.get(ActionId.QUICK_STACK).isPresent());
        assertTrue(ActionRegistry.get(ActionId.QUICK_STASH).isPresent());
        assertTrue(ActionRegistry.get(ActionId.SETTINGS).isPresent());
        assertTrue(ActionRegistry.get(ActionId.LOCK_SLOT_MODIFIER).isPresent());
        assertTrue(ActionRegistry.get(ActionId.LOCK_SLOT).isPresent());
    }

    @Test
    void defaultActionKeysMatchExpectations() {
        ActionDefinition sort = ActionRegistry.get(ActionId.SORT).orElseThrow();
        assertEquals("key.onekey.sort", sort.translationKey());
        assertEquals("key.keyboard.r", sort.defaultKeyName());

        ActionDefinition settings = ActionRegistry.get(ActionId.SETTINGS).orElseThrow();
        assertEquals("key.onekey.settings", settings.translationKey());
        assertEquals("key.keyboard.k", settings.defaultKeyName());

        ActionDefinition lockMod = ActionRegistry.get(ActionId.LOCK_SLOT_MODIFIER).orElseThrow();
        assertEquals("key.onekey.lock_slot_modifier", lockMod.translationKey());
        assertEquals("key.keyboard.left.alt", lockMod.defaultKeyName());

        ActionDefinition lockSlot = ActionRegistry.get(ActionId.LOCK_SLOT).orElseThrow();
        assertEquals("key.onekey.lock_slot", lockSlot.translationKey());
        assertEquals("key.mouse.left", lockSlot.defaultKeyName());
    }

    @Test
    void canRegisterCustomAction() {
        ActionDefinition custom = new ActionDefinition("onekey.custom", "key.onekey.custom", "key.keyboard.j");
        ActionRegistry.register(custom);

        ActionDefinition retrieved = ActionRegistry.get("onekey.custom").orElse(null);
        assertNotNull(retrieved);
        assertEquals("key.onekey.custom", retrieved.translationKey());
    }
}
