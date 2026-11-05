package me.easan.onekey.common.action;

/**
 * 动作标识常量：热键、配置、网络包都引用这里的 id，避免字符串散落。
 */
public final class ActionId {

    public static final String SORT = "onekey.sort";
    public static final String QUICK_STACK = "onekey.quick_stack";
    public static final String QUICK_STASH = "onekey.quick_stash";
    public static final String SETTINGS = "onekey.settings";
    public static final String LOCK_SLOT_MODIFIER = "onekey.lock_slot_modifier";
    public static final String LOCK_SLOT = "onekey.lock_slot";

    private ActionId() {
    }
}
