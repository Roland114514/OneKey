package me.easan.onekey.common.net;

/**
 * 服务端 → 客户端：快速堆叠结果。
 *
 * @param moved 实际移动的物品总数
 */
public record QuickStackResult(int moved) {
}
