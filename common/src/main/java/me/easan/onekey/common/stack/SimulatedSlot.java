package me.easan.onekey.common.stack;

import java.util.Objects;

/**
 * 平台无关的模拟槽位：用于快速堆叠/存入纯算法推演与单元测试。
 */
public final class SimulatedSlot {

    private String itemId;
    private int count;
    private final int maxStackSize;

    public SimulatedSlot(String itemId, int count, int maxStackSize) {
        this.itemId = itemId == null ? "" : itemId;
        this.count = Math.max(0, count);
        this.maxStackSize = Math.max(1, maxStackSize);
        if (this.itemId.isEmpty()) {
            this.count = 0;
        }
    }

    public static SimulatedSlot of(String itemId, int count, int maxStackSize) {
        return new SimulatedSlot(itemId, count, maxStackSize);
    }

    public static SimulatedSlot empty(int maxStackSize) {
        return new SimulatedSlot("", 0, maxStackSize);
    }

    public static SimulatedSlot empty() {
        return empty(64);
    }

    public boolean isEmpty() {
        return itemId.isEmpty() || count <= 0;
    }

    public boolean canStackWith(SimulatedSlot other) {
        if (other == null || other.isEmpty() || this.isEmpty()) {
            return false;
        }
        return Objects.equals(this.itemId, other.itemId);
    }

    /**
     * 将本槽位中的物品转移到目标槽位。
     *
     * @return 实际转移的数量
     */
    public int transferTo(SimulatedSlot target) {
        if (this.isEmpty() || target == null) {
            return 0;
        }

        if (target.isEmpty()) {
            int toMove = Math.min(this.count, target.maxStackSize);
            target.itemId = this.itemId;
            target.count = toMove;
            this.count -= toMove;
            if (this.count <= 0) {
                this.itemId = "";
                this.count = 0;
            }
            return toMove;
        }

        if (!this.canStackWith(target)) {
            return 0;
        }

        int available = target.maxStackSize - target.count;
        if (available <= 0) {
            return 0;
        }

        int toMove = Math.min(this.count, available);
        target.count += toMove;
        this.count -= toMove;
        if (this.count <= 0) {
            this.itemId = "";
            this.count = 0;
        }
        return toMove;
    }

    public String itemId() {
        return itemId;
    }

    public int count() {
        return count;
    }

    public int maxStackSize() {
        return maxStackSize;
    }

    public SimulatedSlot copy() {
        return new SimulatedSlot(this.itemId, this.count, this.maxStackSize);
    }

    @Override
    public String toString() {
        return isEmpty() ? "[Empty]" : "[" + itemId + " x" + count + "/" + maxStackSize + "]";
    }
}
