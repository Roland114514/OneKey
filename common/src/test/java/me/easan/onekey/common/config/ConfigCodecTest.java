package me.easan.onekey.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        config.hotkeys.lockSlotModifier = "key.keyboard.left.control";
        config.hotkeys.lockSlot = "key.keyboard.x";
        config.sort.cooldownMs = 500;
        config.quickStack.radius = 8;
        config.quickStack.whitelist.add("minecraft:chest");

        OneKeyConfig loaded = ConfigCodec.fromJson(ConfigCodec.toJson(config));

        assertEquals("key.keyboard.g", loaded.hotkeys.sort);
        assertEquals("key.keyboard.left.control", loaded.hotkeys.lockSlotModifier);
        assertEquals("key.keyboard.x", loaded.hotkeys.lockSlot);
        assertEquals(500, loaded.sort.cooldownMs);
        assertEquals(8, loaded.quickStack.radius);
        assertEquals(List.of("minecraft:chest"), loaded.quickStack.whitelist);
    }

    @Test
    void missingFieldsUseDefaults() {
        OneKeyConfig config = ConfigCodec.fromJson("{\"sort\":{\"cooldownMs\":900}}");

        assertEquals(900, config.sort.cooldownMs);
        assertEquals("key.keyboard.r", config.hotkeys.sort);
        assertEquals("key.keyboard.left.alt", config.hotkeys.lockSlotModifier);
        assertEquals("key.mouse.left", config.hotkeys.lockSlot);
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
    void outOfRangeValuesAreClamped() {
        OneKeyConfig config = ConfigCodec.fromJson("{\"sort\":{\"cooldownMs\":-100},\"quickStack\":{\"radius\":9999}}");

        assertEquals(50, config.sort.cooldownMs);
        assertEquals(32, config.quickStack.radius);
    }

    @Test
    void saveCreatesParentDirectoriesAndLoadsBack(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("nested").resolve("onekey.json");
        OneKeyConfig config = new OneKeyConfig();
        config.sort.enabled = false;

        ConfigCodec.save(file, config);

        assertTrue(Files.exists(file));
        assertFalse(ConfigCodec.load(file).sort.enabled);
    }

    @Test
    void corruptedJsonFallsBackToDefaultsInsteadOfThrowing() {
        // 截断的 JSON（断电/磁盘写满的典型产物）不能让游戏启动崩溃
        OneKeyConfig truncated = ConfigCodec.fromJson("{\"sort\":{\"enabled\":tr");
        OneKeyConfig garbage = ConfigCodec.fromJson("not json at all");
        OneKeyConfig wrongType = ConfigCodec.fromJson("{\"sort\":\"应该是对象\"}");

        assertEquals(250, truncated.sort.cooldownMs);
        assertTrue(truncated.sort.enabled);
        assertEquals("key.keyboard.r", garbage.hotkeys.sort);
        assertTrue(wrongType.sort.enabled);
        assertEquals(6, wrongType.quickStack.radius);
    }

    @Test
    void loadOfCorruptedFileReturnsDefaults(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("onekey.json");
        Files.writeString(file, "{\"hotkeys\":{\"sort\":");

        OneKeyConfig loaded = ConfigCodec.load(file);

        assertEquals("key.keyboard.r", loaded.hotkeys.sort);
        assertEquals(250, loaded.sort.cooldownMs);
    }

    @Test
    void saveLeavesNoTemporaryFileBehind(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("onekey.json");
        ConfigCodec.save(file, new OneKeyConfig());

        assertTrue(Files.exists(file));
        assertTrue(Files.notExists(tempDir.resolve("onekey.json.tmp")));
        assertEquals(1, Files.list(tempDir).count());
    }

    @Test
    void itemAndBlockFiltersRoundTripSeparately() {
        OneKeyConfig config = new OneKeyConfig();
        config.quickStack.whitelist.add("minecraft:chest");
        config.quickStack.blacklist.add("minecraft:barrel");
        config.quickStack.itemWhitelist.add("minecraft:diamond");
        config.quickStack.itemBlacklist.add("minecraft:dirt");

        OneKeyConfig loaded = ConfigCodec.fromJson(ConfigCodec.toJson(config));

        assertEquals(List.of("minecraft:chest"), loaded.quickStack.whitelist);
        assertEquals(List.of("minecraft:barrel"), loaded.quickStack.blacklist);
        assertEquals(List.of("minecraft:diamond"), loaded.quickStack.itemWhitelist);
        assertEquals(List.of("minecraft:dirt"), loaded.quickStack.itemBlacklist);
    }

    @Test
    void missingItemFilterFieldsAreNormalizedToEmptyLists() {
        // 旧版配置文件没有 itemWhitelist/itemBlacklist 字段，必须补成空列表而不是 null
        OneKeyConfig loaded = ConfigCodec.fromJson("{\"quickStack\":{\"radius\":6}}");

        assertNotNull(loaded.quickStack.itemWhitelist);
        assertNotNull(loaded.quickStack.itemBlacklist);
        assertTrue(loaded.quickStack.itemWhitelist.isEmpty());
        assertTrue(loaded.quickStack.itemBlacklist.isEmpty());
    }

    @Test
    void quickStackGuiTogglesRoundTripAndDefaultToTrue() {
        // 1.0.2 新增的两个 GUI 开关：默认必须为 true（保持准星锁定 + 智能防污染）
        OneKeyConfig defaults = ConfigCodec.fromJson("{}");
        assertTrue(defaults.quickStack.crosshairPriority);
        assertTrue(defaults.quickStack.smartMatching);

        OneKeyConfig config = new OneKeyConfig();
        config.quickStack.crosshairPriority = false;
        config.quickStack.smartMatching = false;
        OneKeyConfig loaded = ConfigCodec.fromJson(ConfigCodec.toJson(config));

        assertTrue(!loaded.quickStack.crosshairPriority);
        assertTrue(!loaded.quickStack.smartMatching);
    }

    @Test
    void backupIfCorruptedMovesBrokenFileAndKeepsGoodOne(@TempDir Path tempDir) throws IOException {
        Path broken = tempDir.resolve("onekey.json");
        Files.writeString(broken, "{\"quickStack\":{\"radius\":");
        Path backup = ConfigCodec.backupIfCorrupted(broken);

        assertNotNull(backup);
        assertTrue(backup.getFileName().toString().endsWith(".corrupt"));
        assertTrue(Files.exists(backup));
        assertTrue(Files.notExists(broken));

        Path healthy = tempDir.resolve("healthy.json");
        ConfigCodec.save(healthy, new OneKeyConfig());
        assertNull(ConfigCodec.backupIfCorrupted(healthy));
        assertTrue(Files.exists(healthy));
    }

    @Test
    void isValidJsonDetectsBrokenInput() {
        assertTrue(ConfigCodec.isValidJson(ConfigCodec.toJson(new OneKeyConfig())));
        assertTrue(!ConfigCodec.isValidJson("{\"sort\":"));
        assertTrue(!ConfigCodec.isValidJson(""));
        assertTrue(!ConfigCodec.isValidJson(null));
    }

    @Test
    void hotbarTogglesDefaultToTrueAndRoundTrip() {
        OneKeyConfig defaults = ConfigCodec.fromJson("{}");
        assertTrue(defaults.sort.includeHotbar, "整理包含快捷栏默认应为开");
        assertTrue(defaults.gestures.includeHotbar, "手势影响快捷栏默认应为开");
        assertTrue(defaults.gestures.lmbDragSweep, "Shift+左键扫掠默认应为开");
        assertTrue(defaults.gestures.rmbDragRepeat, "右键重复放置默认应为开");
        assertFalse(defaults.gestures.invertWheel, "滚轮方向反转默认应为关");
        assertTrue(defaults.gestures.wheelCarriedHandling, "光标持物滚轮微调默认应为开");

        OneKeyConfig custom = new OneKeyConfig();
        custom.sort.includeHotbar = false;
        custom.gestures.includeHotbar = false;
        custom.gestures.lmbDragSweep = false;
        custom.gestures.rmbDragRepeat = false;
        custom.gestures.invertWheel = true;
        custom.gestures.wheelCarriedHandling = false;
        OneKeyConfig roundTripped = ConfigCodec.fromJson(ConfigCodec.toJson(custom));
        assertFalse(roundTripped.sort.includeHotbar, "关闭后应能往返保持");
        assertFalse(roundTripped.gestures.includeHotbar, "关闭后应能往返保持");
        assertFalse(roundTripped.gestures.lmbDragSweep, "关闭后应能往返保持");
        assertFalse(roundTripped.gestures.rmbDragRepeat, "关闭后应能往返保持");
        assertTrue(roundTripped.gestures.invertWheel, "开启后应能往返保持");
        assertFalse(roundTripped.gestures.wheelCarriedHandling, "关闭后应能往返保持");
    }

    @Test
    void excavatorMaxBlocksIsCappedToHardLimit() {
        OneKeyConfig config = new OneKeyConfig();
        config.excavator.maxBlocks = 999;
        assertEquals(128, config.normalized().excavator.maxBlocks);

        config.excavator.maxBlocks = 0;
        assertEquals(1, config.normalized().excavator.maxBlocks);

        config.excavator.maxBlocks = 96;
        assertEquals(96, config.normalized().excavator.maxBlocks);
    }

    @Test
    void excavatorDefaultsAndRoundTrip() {
        OneKeyConfig defaults = ConfigCodec.fromJson("{}");
        assertNotNull(defaults.excavator);
        assertTrue(defaults.excavator.enabled);
        assertEquals(64, defaults.excavator.maxBlocks);
        assertTrue(defaults.excavator.preventToolBreak);
        assertTrue(defaults.excavator.consolidateDrops);
        assertFalse(defaults.excavator.includeLeaves);
        assertTrue(defaults.excavator.requireValidTool);
        assertEquals(0.005F, defaults.excavator.exhaustionPerBlock, 0.0001F);
        assertEquals("key.keyboard.grave.accent", defaults.hotkeys.excavate);

        OneKeyConfig custom = new OneKeyConfig();
        custom.excavator.enabled = false;
        custom.excavator.maxBlocks = 128;
        custom.excavator.preventToolBreak = false;
        custom.excavator.consolidateDrops = false;
        custom.excavator.includeLeaves = true;
        custom.excavator.requireValidTool = false;
        custom.excavator.exhaustionPerBlock = 0.01F;
        custom.hotkeys.excavate = "key.keyboard.grave.accent";

        OneKeyConfig loaded = ConfigCodec.fromJson(ConfigCodec.toJson(custom));
        assertFalse(loaded.excavator.enabled);
        assertEquals(128, loaded.excavator.maxBlocks);
        assertFalse(loaded.excavator.preventToolBreak);
        assertFalse(loaded.excavator.consolidateDrops);
        assertTrue(loaded.excavator.includeLeaves);
        assertFalse(loaded.excavator.requireValidTool);
        assertEquals(0.01F, loaded.excavator.exhaustionPerBlock, 0.0001F);
    }
}
