package me.easan.onekey.common.excavator;

/**
 * 纯 Java 3D 空间六向枚举，供空间投影与方向计算。
 */
public enum Direction3i {
    DOWN(0, -1, 0, 0),
    UP(0, 1, 0, 1),
    NORTH(0, 0, -1, 2),
    SOUTH(0, 0, 1, 3),
    WEST(-1, 0, 0, 4),
    EAST(1, 0, 0, 5);

    private final int stepX;
    private final int stepY;
    private final int stepZ;
    private final int id;

    Direction3i(int stepX, int stepY, int stepZ, int id) {
        this.stepX = stepX;
        this.stepY = stepY;
        this.stepZ = stepZ;
        this.id = id;
    }

    public int stepX() {
        return stepX;
    }

    public int stepY() {
        return stepY;
    }

    public int stepZ() {
        return stepZ;
    }

    public int id() {
        return id;
    }

    public boolean isVertical() {
        return this == DOWN || this == UP;
    }

    public boolean isHorizontal() {
        return !isVertical();
    }

    public Direction3i getOpposite() {
        return switch (this) {
            case DOWN -> UP;
            case UP -> DOWN;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
        };
    }

    /**
     * 在水平面上顺时针旋转 90 度（俯视看：北 -> 东 -> 南 -> 西 -> 北）。
     */
    public Direction3i getClockWise() {
        return switch (this) {
            case NORTH -> EAST;
            case EAST -> SOUTH;
            case SOUTH -> WEST;
            case WEST -> NORTH;
            default -> this;
        };
    }

    public static Direction3i fromId(int id) {
        return switch (id) {
            case 0 -> DOWN;
            case 1 -> UP;
            case 2 -> NORTH;
            case 3 -> SOUTH;
            case 4 -> WEST;
            case 5 -> EAST;
            default -> NORTH;
        };
    }
}
