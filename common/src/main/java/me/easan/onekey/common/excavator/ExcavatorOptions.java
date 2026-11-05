package me.easan.onekey.common.excavator;

/**
 * 连锁破坏的「玩家个人偏好」与「服务端权限」合并模型。
 *
 * <p>服务端的 {@code config.excavator.*} 是硬上限（服主权限），客户端在每次按键/模式变化时把
 * 自己的开关打包成位掩码随 {@code ExcavatorStatePayload} 一起发上来，服务端用
 * {@link #resolve(ExcavatorOptions, int, int)} 取交集：<b>玩家只能收紧、不能放开</b>。
 * 这样多人服里玩家关掉连锁破坏、调小最大方块数时才会真的生效，而不是被服主配置覆盖。</p>
 */
public record ExcavatorOptions(
        boolean enabled,
        boolean includeLeaves,
        boolean preventToolBreak,
        boolean requireValidTool,
        boolean consolidateDrops,
        int maxBlocks) {

    /** 位掩码最高位：表示客户端确实携带了个人偏好（旧版客户端不带，服务端按自身配置处理）。 */
    public static final int FLAG_CUSTOM_OPTIONS = 1;
    public static final int FLAG_ENABLED = 1 << 1;
    public static final int FLAG_INCLUDE_LEAVES = 1 << 2;
    public static final int FLAG_PREVENT_TOOL_BREAK = 1 << 3;
    public static final int FLAG_REQUIRE_VALID_TOOL = 1 << 4;
    public static final int FLAG_CONSOLIDATE_DROPS = 1 << 5;

    /** 旧版客户端/未携带偏好时的占位掩码。 */
    public static final int NO_CUSTOM_OPTIONS = 0;

    /** 单次连锁破坏方块数的硬上限（与 GUI 档位一致）。 */
    public static final int MAX_BLOCKS_LIMIT = 128;

    /** 默认值，与服务端 {@code OneKeyConfig.Excavator} 出厂值保持一致。 */
    public static final ExcavatorOptions DEFAULT =
            new ExcavatorOptions(true, false, true, true, true, 64);

    /** 打包成可放进数据包的位掩码。 */
    public int optionsMask() {
        int mask = FLAG_CUSTOM_OPTIONS;
        if (enabled) {
            mask |= FLAG_ENABLED;
        }
        if (includeLeaves) {
            mask |= FLAG_INCLUDE_LEAVES;
        }
        if (preventToolBreak) {
            mask |= FLAG_PREVENT_TOOL_BREAK;
        }
        if (requireValidTool) {
            mask |= FLAG_REQUIRE_VALID_TOOL;
        }
        if (consolidateDrops) {
            mask |= FLAG_CONSOLIDATE_DROPS;
        }
        return mask;
    }

    public static boolean hasCustomOptions(int mask) {
        return (mask & FLAG_CUSTOM_OPTIONS) != 0;
    }

    /** 从位掩码还原；调用方应先确认 {@link #hasCustomOptions(int)}。 */
    public static ExcavatorOptions unpack(int mask, int maxBlocks) {
        return new ExcavatorOptions(
                (mask & FLAG_ENABLED) != 0,
                (mask & FLAG_INCLUDE_LEAVES) != 0,
                (mask & FLAG_PREVENT_TOOL_BREAK) != 0,
                (mask & FLAG_REQUIRE_VALID_TOOL) != 0,
                (mask & FLAG_CONSOLIDATE_DROPS) != 0,
                clampMaxBlocks(maxBlocks));
    }

    /**
     * 服务端配置与客户端偏好的交集：布尔项取「与」（玩家只能关不能开），
     * 方块数取较小值并夹到 1..128。
     */
    public static ExcavatorOptions resolve(ExcavatorOptions server, int clientMask, int clientMaxBlocks) {
        if (server == null) {
            return DEFAULT;
        }
        if (!hasCustomOptions(clientMask)) {
            return server;
        }
        ExcavatorOptions client = unpack(clientMask, clientMaxBlocks);
        int max = Math.min(server.maxBlocks(), client.maxBlocks());
        return new ExcavatorOptions(
                server.enabled() && client.enabled(),
                server.includeLeaves() && client.includeLeaves(),
                server.preventToolBreak() && client.preventToolBreak(),
                server.requireValidTool() && client.requireValidTool(),
                server.consolidateDrops() && client.consolidateDrops(),
                clampMaxBlocks(max));
    }

    public static int clampMaxBlocks(int maxBlocks) {
        if (maxBlocks < 1) {
            return 1;
        }
        return Math.min(maxBlocks, MAX_BLOCKS_LIMIT);
    }
}
