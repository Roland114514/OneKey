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
    }

    @Test
    void defaultActionKeysMatchExpectations() {
        ActionDefinition sort = ActionRegistry.get(ActionId.SORT).orElseThrow();
        assertEquals("key.onekey.sort", sort.translationKey());
        assertEquals("key.keyboard.r", sort.defaultKeyName());

        ActionDefinition settings = ActionRegistry.get(ActionId.SETTINGS).orElseThrow();
        assertEquals("key.onekey.settings", settings.translationKey());
        assertEquals("key.keyboard.k", settings.defaultKeyName());
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
