package me.easan.onekey.common.excavator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * 纯 Java 连锁方块空间搜索与形状生成引擎。
 *
 * <p>支持 26 邻域三维矿脉拓扑扩散 (VEIN)、1x2 标准矿道 (TUNNEL_1X2) 与 3x3 断面隧道 (TUNNEL_3X3)。</p>
 */
public final class VeinFinder {

    /** 26 邻域相对偏移量向量（3x3x3 空间排除自身）。 */
    private static final List<BlockPos3i> NEIGHBOR_OFFSETS = new ArrayList<>(26);

    static {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        NEIGHBOR_OFFSETS.add(new BlockPos3i(dx, dy, dz));
                    }
                }
            }
        }
    }

    private VeinFinder() {
    }

    /**
     * 根据当前选定的挖掘模式统一提取目标方块集合。
     */
    public static List<BlockPos3i> findBlocks(
            MiningShape shape,
            BlockPos3i origin,
            Direction3i hitFace,
            Direction3i playerFacing,
            BlockInspector inspector,
            int maxBlocks,
            boolean includeLeaves,
            boolean isHitUpperHalf) {
        if (shape == null || origin == null || inspector == null || maxBlocks <= 0) {
            return List.of();
        }

        return switch (shape) {
            case VEIN -> findVein(origin, inspector, maxBlocks, includeLeaves);
            case TUNNEL_1X2 -> findTunnel1x2(origin, hitFace, playerFacing, inspector, maxBlocks, isHitUpperHalf);
            case TUNNEL_3X3 -> findTunnel3x3(origin, hitFace, playerFacing, inspector, maxBlocks);
        };
    }

    /**
     * 1. 经典无向图 26 邻域 BFS 空间连通扩散 (VEIN)。
     */
    public static List<BlockPos3i> findVein(
            BlockPos3i origin,
            BlockInspector inspector,
            int maxBlocks,
            boolean includeLeaves) {
        if (!inspector.isDestructible(origin)) {
            return List.of();
        }

        List<BlockPos3i> matches = new ArrayList<>(Math.min(maxBlocks, 128));
        Queue<BlockPos3i> queue = new ArrayDeque<>();
        Set<BlockPos3i> visited = new HashSet<>();

        queue.add(origin);
        visited.add(origin);

        // 最大搜索距离半径约束（32格），防止连通超大矿脉或巨型世界生成引发内存与运算爆炸
        double maxDistSqr = 32.0 * 32.0;

        while (!queue.isEmpty() && matches.size() < maxBlocks) {
            BlockPos3i current = queue.poll();
            if (inspector.matches(origin, current, includeLeaves) && inspector.isDestructible(current)) {
                matches.add(current);
                if (matches.size() >= maxBlocks) {
                    break;
                }

                for (BlockPos3i offset : NEIGHBOR_OFFSETS) {
                    BlockPos3i neighbor = current.offset(offset.x(), offset.y(), offset.z());
                    if (origin.distSqr(neighbor) <= maxDistSqr && visited.add(neighbor)) {
                        queue.add(neighbor);
                    }
                }
            }
        }

        // 按距离原点由近及远排序，保证从下往上、从近到远破坏
        matches.sort(Comparator.comparingDouble(origin::distSqr));
        return matches;
    }

    /**
     * 2. 1x2 标准直行矿道投影 (TUNNEL_1X2)。
     */
    public static List<BlockPos3i> findTunnel1x2(
            BlockPos3i origin,
            Direction3i hitFace,
            Direction3i playerFacing,
            BlockInspector inspector,
            int maxBlocks,
            boolean isHitUpperHalf) {
        List<BlockPos3i> result = new ArrayList<>(maxBlocks);
        Direction3i forward = (hitFace != null) ? hitFace.getOpposite() : (playerFacing != null ? playerFacing : Direction3i.NORTH);

        int depth = Math.max(1, maxBlocks / 2);

        for (int d = 0; d < depth; d++) {
            int bx = origin.x() + d * forward.stepX();
            int by = origin.y() + d * forward.stepY();
            int bz = origin.z() + d * forward.stepZ();

            BlockPos3i p1 = new BlockPos3i(bx, by, bz);
            BlockPos3i p2;

            if (forward.isVertical()) {
                // 向上或向下挖竖井：根据玩家水平朝向沿水平延伸成 1x2
                Direction3i step = (playerFacing != null && playerFacing.isHorizontal()) ? playerFacing : Direction3i.NORTH;
                p2 = p1.offset(step.stepX(), step.stepY(), step.stepZ());
            } else {
                // 水平挖掘：若击中上半部分，另一个方块在下方；若击中下半部分，另一个方块在上方
                int dy = isHitUpperHalf ? -1 : 1;
                p2 = p1.offset(0, dy, 0);
            }

            if (inspector.isDestructible(p1)) {
                result.add(p1);
            }
            if (result.size() >= maxBlocks) {
                break;
            }
            if (inspector.isDestructible(p2)) {
                result.add(p2);
            }
            if (result.size() >= maxBlocks) {
                break;
            }
        }

        return result;
    }

    /**
     * 3. 3x3 截面隧道投影 (TUNNEL_3X3)。
     *
     * <p>利用局部正交基 (F, U, R) 构建严格的三维投影空间。</p>
     */
    public static List<BlockPos3i> findTunnel3x3(
            BlockPos3i origin,
            Direction3i hitFace,
            Direction3i playerFacing,
            BlockInspector inspector,
            int maxBlocks) {
        Direction3i F = (hitFace != null) ? hitFace.getOpposite() : (playerFacing != null ? playerFacing : Direction3i.NORTH);
        Direction3i U;
        Direction3i R;

        Direction3i pDir = (playerFacing != null && playerFacing.isHorizontal()) ? playerFacing : Direction3i.NORTH;

        if (F.isVertical()) {
            // 垂直挖掘（俯视向下或仰视向上）：
            U = (F == Direction3i.DOWN) ? pDir.getOpposite() : pDir;
            R = pDir.getClockWise();
        } else {
            // 水平挖掘：
            U = Direction3i.UP;
            R = F.getClockWise();
        }

        List<BlockPos3i> result = new ArrayList<>(maxBlocks);
        int maxLayers = Math.max(1, (maxBlocks + 8) / 9);

        for (int layer = 0; layer < maxLayers; layer++) {
            for (int u = -1; u <= 1; u++) {
                for (int r = -1; r <= 1; r++) {
                    int x = origin.x() + layer * F.stepX() + u * U.stepX() + r * R.stepX();
                    int y = origin.y() + layer * F.stepY() + u * U.stepY() + r * R.stepY();
                    int z = origin.z() + layer * F.stepZ() + u * U.stepZ() + r * R.stepZ();

                    BlockPos3i pos = new BlockPos3i(x, y, z);
                    if (inspector.isDestructible(pos)) {
                        result.add(pos);
                        if (result.size() >= maxBlocks) {
                            return result;
                        }
                    }
                }
            }
        }

        return result;
    }
}
