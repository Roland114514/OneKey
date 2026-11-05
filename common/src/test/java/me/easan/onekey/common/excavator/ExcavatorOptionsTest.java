package me.easan.onekey.common.excavator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@link ExcavatorOptions} 位掩码打包与「服务端权限 ∩ 玩家偏好」合并逻辑。
 */
class ExcavatorOptionsTest {

    @Test
    void maskRoundTripsEveryFlag() {
        ExcavatorOptions options = new ExcavatorOptions(false, true, false, true, false, 32);
        ExcavatorOptions decoded = ExcavatorOptions.unpack(options.optionsMask(), options.maxBlocks());
        assertEquals(options, decoded);
        assertTrue(ExcavatorOptions.hasCustomOptions(options.optionsMask()));
    }

    @Test
    void legacyMaskWithoutCustomFlagFallsBackToServerConfig() {
        ExcavatorOptions server = new ExcavatorOptions(true, true, true, true, true, 128);
        assertFalse(ExcavatorOptions.hasCustomOptions(ExcavatorOptions.NO_CUSTOM_OPTIONS));
        assertEquals(server, ExcavatorOptions.resolve(server, ExcavatorOptions.NO_CUSTOM_OPTIONS, 16));
    }

    @Test
    void playerCanOnlyTightenServerPermission() {
        ExcavatorOptions server = new ExcavatorOptions(true, false, true, true, true, 64);
        // 玩家尝试开启服务端关闭的「包含树叶」，并要求 128 块 —— 应被服务端限制住
        ExcavatorOptions client = new ExcavatorOptions(true, true, true, true, true, 128);
        ExcavatorOptions effective = ExcavatorOptions.resolve(server, client.optionsMask(), client.maxBlocks());

        assertFalse(effective.includeLeaves(), "服务端关闭的开关不能被客户端打开");
        assertEquals(64, effective.maxBlocks(), "方块数不能超过服务端上限");
    }

    @Test
    void playerCanDisableAndShrinkWithinLimit() {
        ExcavatorOptions server = new ExcavatorOptions(true, true, true, true, true, 64);
        ExcavatorOptions client = new ExcavatorOptions(false, false, false, false, false, 16);
        ExcavatorOptions effective = ExcavatorOptions.resolve(server, client.optionsMask(), client.maxBlocks());

        assertFalse(effective.enabled());
        assertFalse(effective.consolidateDrops());
        assertFalse(effective.preventToolBreak());
        assertEquals(16, effective.maxBlocks());
    }

    @Test
    void maxBlocksIsClampedToSafeRange() {
        assertEquals(1, ExcavatorOptions.clampMaxBlocks(0));
        assertEquals(1, ExcavatorOptions.clampMaxBlocks(-16));
        assertEquals(ExcavatorOptions.MAX_BLOCKS_LIMIT, ExcavatorOptions.clampMaxBlocks(4096));
        assertEquals(64, ExcavatorOptions.clampMaxBlocks(64));
    }

    @Test
    void defaultOptionsMatchDocumentedDefaults() {
        assertEquals(ExcavatorOptions.DEFAULT, new ExcavatorOptions(true, false, true, true, true, 64));
    }
}
