package me.easan.onekey.common.net;

/**
 * 目标方块三维空间坐标（纯数据载体，解耦 Minecraft 原版方块坐标类型）。
 *
 * @param x X 坐标
 * @param y Y 坐标
 * @param z Z 坐标
 */
public record TargetPos(int x, int y, int z) {

    public static TargetPos of(int x, int y, int z) {
        return new TargetPos(x, y, z);
    }

    public boolean matches(int bx, int by, int bz) {
        return this.x == bx && this.y == by && this.z == bz;
    }
}
