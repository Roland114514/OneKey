package me.easan.onekey.common.excavator;

/**
 * 连锁挖掘形状模式。
 */
public enum MiningShape {
    /** 同类连通脉络（26 邻域三维扩散）。 */
    VEIN(0, "vein", "onekey.shape.vein"),

    /** 1 宽 2 高标准直行矿道。 */
    TUNNEL_1X2(1, "tunnel_1x2", "onekey.shape.tunnel_1x2"),

    /** 3x3 大断面隧道/采石场挖掘。 */
    TUNNEL_3X3(2, "tunnel_3x3", "onekey.shape.tunnel_3x3");

    private final int id;
    private final String key;
    private final String translationKey;

    MiningShape(int id, String key, String translationKey) {
        this.id = id;
        this.key = key;
        this.translationKey = translationKey;
    }

    public int id() {
        return id;
    }

    public String key() {
        return key;
    }

    public String translationKey() {
        return translationKey;
    }

    public MiningShape cycle(boolean forward) {
        MiningShape[] values = values();
        int nextIndex;
        if (forward) {
            nextIndex = (this.ordinal() + 1) % values.length;
        } else {
            nextIndex = (this.ordinal() - 1 + values.length) % values.length;
        }
        return values[nextIndex];
    }

    public static MiningShape fromId(int id) {
        for (MiningShape shape : values()) {
            if (shape.id == id) {
                return shape;
            }
        }
        return VEIN;
    }
}
