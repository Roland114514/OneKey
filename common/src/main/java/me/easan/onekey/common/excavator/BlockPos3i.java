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

    public long asLong() {
        return pack(this.x, this.y, this.z);
    }

    public static long pack(int x, int y, int z) {
        return (((long) x & 0x3FFFFFF) << 38) | (((long) y & 0xFFF) << 26) | ((long) z & 0x3FFFFFF);
    }

    public static int unpackX(long packed) {
        return (int) (packed >> 38);
    }

    public static int unpackY(long packed) {
        return (int) ((packed << 26) >> 52);
    }

    public static int unpackZ(long packed) {
        return (int) ((packed << 38) >> 38);
    }

    public static BlockPos3i fromLong(long packed) {
        return new BlockPos3i(unpackX(packed), unpackY(packed), unpackZ(packed));
    }
}
