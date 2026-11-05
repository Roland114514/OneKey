package me.easan.onekey.common.excavator;

/**
 * 纯 Java 方块属性探针，解耦 Minecraft 原版方块模型，供拓扑与形状生成算法快速查询。
 */
@FunctionalInterface
public interface BlockInspector {

    /**
     * 判断 {@code target} 位置的方块是否与起始点 {@code origin} 匹配为同类连通方块。
     *
     * @param origin 起始破坏方块位置
     * @param target 待评估的相邻方块位置
     * @param includeLeaves 采伐树木时是否放行相连树叶
     * @return true 表示属于有效连通方块
     */
    boolean matches(BlockPos3i origin, BlockPos3i target, boolean includeLeaves);

    /**
     * 判断方块是否可被常规破坏（非基岩、非空气、非屏障等）。
     */
    default boolean isDestructible(BlockPos3i target) {
        return true;
    }
}
