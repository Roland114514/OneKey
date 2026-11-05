package me.easan.onekey.common.excavator;

/**
 * 纯 Java 3D 整数坐标（无任何 Minecraft 依赖），供 common 模块计算几何形状与拓扑扩散。
 */
public record BlockPos3i(int x, int y, int z) {

    public BlockPos3i offset(int dx, int dy, int dz) {
        return (dx == 0 && dy == 0 && dz == 0) ? this : new BlockPos3i(this.x + dx, this.y + dy, this.z + dz);
    }

    public double distSqr(int ox, int oy, int oz) {
        double dx = (double) this.x - ox;
        double dy = (double) this.y - oy;
        double dz = (double) this.z - oz;
        return dx * dx + dy * dy + dz * dz;
    }

    public double distSqr(BlockPos3i other) {
        return distSqr(other.x, other.y, other.z);
    }
}
