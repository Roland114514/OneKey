package me.easan.onekey.common.action;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 统一动作注册表：集中管理所有可绑定的用户动作、翻译键与默认快捷键。
 */
public final class ActionRegistry {

    private static final Map<String, ActionDefinition> ACTIONS = new LinkedHashMap<>();

    static {
        register(new ActionDefinition(ActionId.SORT, "key.onekey.sort", "key.keyboard.r"));
        register(new ActionDefinition(ActionId.QUICK_STACK, "key.onekey.quick_stack", "key.keyboard.v"));
        register(new ActionDefinition(ActionId.QUICK_STASH, "key.onekey.quick_stash", "key.keyboard.b"));
        register(new ActionDefinition(ActionId.SETTINGS, "key.onekey.settings", "key.keyboard.k"));
    }

    private ActionRegistry() {
    }

    public static synchronized void register(ActionDefinition definition) {
        if (definition != null) {
            ACTIONS.put(definition.id(), definition);
        }
    }

    public static synchronized Optional<ActionDefinition> get(String id) {
        return Optional.ofNullable(ACTIONS.get(id));
    }

    public static synchronized Collection<ActionDefinition> all() {
        return Collections.unmodifiableCollection(ACTIONS.values());
    }
}
