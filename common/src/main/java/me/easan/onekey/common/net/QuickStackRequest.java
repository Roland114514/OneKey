package me.easan.onekey.common.net;

/**
 * 客户端 → 服务端：请求快速堆叠 / 一键存入附近容器。
 *
 * @param syncId 当前容器同步 id，-1 表示背包界面
 * @param radius 扫描半径（格）
 * @param stash  true = 全部存入，false = 只补已有物品
 */
public record QuickStackRequest(int syncId, int radius, boolean stash) {
}
