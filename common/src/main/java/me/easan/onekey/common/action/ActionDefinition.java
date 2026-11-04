package me.easan.onekey.common.action;

/**
 * 动作定义：包含统一动作标识、界面翻译键和默认按键名称。
 */
public record ActionDefinition(String id, String translationKey, String defaultKeyName) {
}
