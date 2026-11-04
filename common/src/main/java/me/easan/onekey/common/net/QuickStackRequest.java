package me.easan.onekey.common.net;

import java.util.Optional;

/**
 * 客户端 → 服务端：请求快速堆叠 / 一键存入附近容器。
 *
 * @param syncId    当前容器同步 id，-1 表示背包界面
 * @param radius    扫描半径（格）
 * @param stash     true = 一键存入模式，false = 快速堆叠模式（仅补已有同类）
 * @param targetPos 客户端准星瞄准锁定的目标方块坐标（未瞄准任何方块时为 Optional.empty()）
 */
public record QuickStackRequest(
        int syncId,
        int radius,
        boolean stash,
        Optional<TargetPos> targetPos
) {

    public QuickStackRequest(int syncId, int radius, boolean stash) {
        this(syncId, radius, stash, Optional.empty());
    }
}
