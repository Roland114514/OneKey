package me.easan.onekey.common.net;

/**
 * 服务端 → 客户端：整理结果。
 *
 * @param success 是否执行成功
 * @param moved   移动的槽位数量
 */
public record SortResult(boolean success, int moved) {
}
