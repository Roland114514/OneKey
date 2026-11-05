package me.easan.onekey.common.excavator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class VeinFinderTest {

    private static class SimpleBlockInspector implements BlockInspector {
        final Map<BlockPos3i, String> world = new HashMap<>();
        final Set<BlockPos3i> indestructible = new HashSet<>();

        void set(int x, int y, int z, String blockId) {
            world.put(new BlockPos3i(x, y, z), blockId);
        }

        @Override
        public boolean matches(BlockPos3i origin, BlockPos3i current, boolean includeLeaves) {
            String originBlock = world.get(origin);
            String currentBlock = world.get(current);
            if (originBlock == null || currentBlock == null) {
                return false;
            }
            if (originBlock.equals(currentBlock)) {
                return true;
            }
            if (includeLeaves && originBlock.contains("log") && currentBlock.contains("leaves")) {
                return true;
            }
            return false;
        }

        @Override
        public boolean isDestructible(BlockPos3i pos) {
            return world.containsKey(pos) && !indestructible.contains(pos);
        }
    }

    @Test
    void miningShapeCyclesCorrectly() {
        assertEquals(MiningShape.TUNNEL_1X2, MiningShape.VEIN.cycle(true));
        assertEquals(MiningShape.TUNNEL_3X3, MiningShape.TUNNEL_1X2.cycle(true));
        assertEquals(MiningShape.VEIN, MiningShape.TUNNEL_3X3.cycle(true));

        assertEquals(MiningShape.TUNNEL_3X3, MiningShape.VEIN.cycle(false));
        assertEquals(MiningShape.TUNNEL_1X2, MiningShape.TUNNEL_3X3.cycle(false));
        assertEquals(MiningShape.VEIN, MiningShape.TUNNEL_1X2.cycle(false));

        assertEquals(MiningShape.VEIN, MiningShape.fromId(0));
        assertEquals(MiningShape.TUNNEL_1X2, MiningShape.fromId(1));
        assertEquals(MiningShape.TUNNEL_3X3, MiningShape.fromId(2));
        assertEquals(MiningShape.VEIN, MiningShape.fromId(99));
    }

    @Test
    void findVeinDiscoversConnected26Neighbors() {
        SimpleBlockInspector inspector = new SimpleBlockInspector();
        BlockPos3i origin = new BlockPos3i(0, 60, 0);

        // 创建一个包含原点及对角、垂直相连的 4 个铁矿石矿脉
        inspector.set(0, 60, 0, "minecraft:iron_ore");
        inspector.set(1, 60, 0, "minecraft:iron_ore");
        inspector.set(1, 61, 1, "minecraft:iron_ore"); // 对角邻居
        inspector.set(2, 61, 1, "minecraft:iron_ore"); // 间接邻居
        inspector.set(0, 59, 0, "minecraft:stone"); // 不匹配

        List<BlockPos3i> result = VeinFinder.findVein(origin, inspector, 64, false);

        assertEquals(4, result.size());
        assertEquals(origin, result.get(0)); // 原点始终最靠前（距离最小）
        assertTrue(result.contains(new BlockPos3i(1, 60, 0)));
        assertTrue(result.contains(new BlockPos3i(1, 61, 1)));
        assertTrue(result.contains(new BlockPos3i(2, 61, 1)));
        assertFalse(result.contains(new BlockPos3i(0, 59, 0)));
    }

    @Test
    void findVeinLimitsMaxBlocks() {
        SimpleBlockInspector inspector = new SimpleBlockInspector();
        BlockPos3i origin = new BlockPos3i(0, 60, 0);

        for (int i = 0; i < 20; i++) {
            inspector.set(i, 60, 0, "minecraft:coal_ore");
        }

        List<BlockPos3i> result = VeinFinder.findVein(origin, inspector, 5, false);
        assertEquals(5, result.size());
    }

    @Test
    void findVeinLeavesInclusionToggle() {
        SimpleBlockInspector inspector = new SimpleBlockInspector();
        BlockPos3i origin = new BlockPos3i(0, 60, 0);

        inspector.set(0, 60, 0, "minecraft:oak_log");
        inspector.set(0, 61, 0, "minecraft:oak_log");
        inspector.set(0, 62, 0, "minecraft:oak_leaves");

        // 默认 includeLeaves = false 时不应破坏树叶
        List<BlockPos3i> logsOnly = VeinFinder.findVein(origin, inspector, 64, false);
        assertEquals(2, logsOnly.size());
        assertFalse(logsOnly.contains(new BlockPos3i(0, 62, 0)));

        // includeLeaves = true 时应一同采集
        List<BlockPos3i> withLeaves = VeinFinder.findVein(origin, inspector, 64, true);
        assertEquals(3, withLeaves.size());
        assertTrue(withLeaves.contains(new BlockPos3i(0, 62, 0)));
    }

    @Test
    void findTunnel1x2Horizontal() {
        SimpleBlockInspector inspector = new SimpleBlockInspector();
        // 假设面前是一堵墙 (x = 10, y: 60..61, z: 0)
        BlockPos3i origin = new BlockPos3i(10, 61, 0);

        for (int x = 10; x <= 15; x++) {
            for (int y = 59; y <= 62; y++) {
                inspector.set(x, y, 0, "minecraft:stone");
            }
        }

        // 玩家面向 EAST (hitFace = WEST，击中了西面，forward = EAST)
        // 击中上半部分 (y = 61)，则第二方块在下方 (y = 60)
        List<BlockPos3i> tunnel = VeinFinder.findTunnel1x2(
                origin,
                Direction3i.WEST,
                Direction3i.EAST,
                inspector,
                4, // 深度为 2 层（每层 2 个）
                true // 击中上半部分
        );

        assertEquals(4, tunnel.size());
        // Layer 0: (10, 61, 0) and (10, 60, 0)
        assertTrue(tunnel.contains(new BlockPos3i(10, 61, 0)));
        assertTrue(tunnel.contains(new BlockPos3i(10, 60, 0)));
        // Layer 1: (11, 61, 0) and (11, 60, 0)
        assertTrue(tunnel.contains(new BlockPos3i(11, 61, 0)));
        assertTrue(tunnel.contains(new BlockPos3i(11, 60, 0)));
    }

    @Test
    void findTunnel3x3Horizontal() {
        SimpleBlockInspector inspector = new SimpleBlockInspector();
        BlockPos3i origin = new BlockPos3i(10, 60, 0);

        // 填充 3x3x2 区域
        for (int x = 10; x <= 11; x++) {
            for (int y = 59; y <= 61; y++) {
                for (int z = -1; z <= 1; z++) {
                    inspector.set(x, y, z, "minecraft:stone");
                }
            }
        }

        // 击中 WEST 面，掘进方向为 EAST
        List<BlockPos3i> tunnel = VeinFinder.findTunnel3x3(
                origin,
                Direction3i.WEST,
                Direction3i.EAST,
                inspector,
                9 // 单层 3x3 = 9 块
        );

        assertEquals(9, tunnel.size());
        // 验证第一层覆盖 x = 10，y in [59..61], z in [-1..1]
        for (int y = 59; y <= 61; y++) {
            for (int z = -1; z <= 1; z++) {
                assertTrue(tunnel.contains(new BlockPos3i(10, y, z)));
            }
        }
    }
}
