package me.easan.onekey.common.net;

/**
 * 客户端 → 服务端：请求整理当前打开的容器。
 *
 * @param syncId 容器同步 id（服务端会校验玩家确实打开了该容器）
 * @param mode   排序模式（0 = 自动, 1 = 仅容器, 2 = 仅玩家主背包）
 */
public record SortRequest(int syncId, int mode) {
    public static final int MODE_AUTO = 0;
    public static final int MODE_CONTAINER = 1;
    public static final int MODE_PLAYER = 2;
}
