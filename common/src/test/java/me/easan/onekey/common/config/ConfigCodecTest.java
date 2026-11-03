package me.easan.onekey.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigCodecTest {

    @Test
    void roundTripPreservesValues() {
        OneKeyConfig config = new OneKeyConfig();
        config.hotkeys.sort = "key.keyboard.g";
        config.sort.cooldownMs = 500;
        config.quickStack.radius = 8;
        config.quickStack.whitelist.add("minecraft:chest");

        OneKeyConfig loaded = ConfigCodec.fromJson(ConfigCodec.toJson(config));

        assertEquals("key.keyboard.g", loaded.hotkeys.sort);
        assertEquals(500, loaded.sort.cooldownMs);
        assertEquals(8, loaded.quickStack.radius);
        assertEquals(List.of("minecraft:chest"), loaded.quickStack.whitelist);
    }

    @Test
    void missingFieldsUseDefaults() {
        OneKeyConfig config = ConfigCodec.fromJson("{\"sort\":{\"cooldownMs\":900}}");

        assertEquals(900, config.sort.cooldownMs);
        assertEquals("key.keyboard.r", config.hotkeys.sort);
        assertEquals(6, config.quickStack.radius);
        assertTrue(config.sort.enabled);
        assertEquals(1, config.schema);
    }

    @Test
    void nullSectionsAreNormalized() {
        OneKeyConfig config = ConfigCodec.fromJson("{\"hotkeys\":null,\"sort\":null,\"quickStack\":null}");

        assertNotNull(config.hotkeys);
        assertNotNull(config.sort);
        assertNotNull(config.quickStack);
        assertNotNull(config.quickStack.whitelist);
    }

    @Test
    void saveCreatesParentDirectoriesAndLoadsBack(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("nested").resolve("onekey.json");
        OneKeyConfig config = new OneKeyConfig();
        config.compat.enabled = true;

        ConfigCodec.save(file, config);

        assertTrue(Files.exists(file));
        assertTrue(ConfigCodec.load(file).compat.enabled);
    }
}
