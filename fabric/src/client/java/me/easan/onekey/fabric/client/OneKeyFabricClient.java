package me.easan.onekey.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import me.easan.onekey.fabric.OneKeyFabric;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * Fabric 客户端入口：注册 4 个热键占位（整理 / 快速堆叠 / 一键存入 / 设置）。
 *
 * <p>真正的动作实现分别在 M3 / M4 / M5 里程碑补齐，见 PLAN.md。
 */
public final class OneKeyFabricClient implements ClientModInitializer {

    /** 26.3 的按键分类是记录类型，需要先注册到原版分类列表（重复注册会抛异常，故只注册一次）。 */
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(OneKeyFabric.MOD_ID, "keys"));

    private static KeyMapping sortKey;
    private static KeyMapping quickStackKey;
    private static KeyMapping quickStashKey;
    private static KeyMapping settingsKey;

    @Override
    public void onInitializeClient() {
        sortKey = register("key.onekey.sort", InputConstants.KEY_R);
        quickStackKey = register("key.onekey.quick_stack", InputConstants.KEY_V);
        quickStashKey = register("key.onekey.quick_stash", InputConstants.KEY_B);
        settingsKey = register("key.onekey.settings", InputConstants.KEY_K);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (sortKey.consumeClick()) {
                OneKeyFabric.LOGGER.info("[OneKey] 一键整理：待实现（M3）");
            }
            while (quickStackKey.consumeClick()) {
                OneKeyFabric.LOGGER.info("[OneKey] 快速堆叠：待实现（M5）");
            }
            while (quickStashKey.consumeClick()) {
                OneKeyFabric.LOGGER.info("[OneKey] 一键存入：待实现（M5）");
            }
            while (settingsKey.consumeClick()) {
                OneKeyFabric.LOGGER.info("[OneKey] 设置界面：待实现（M4）");
            }
        });

        OneKeyFabric.LOGGER.info("[OneKey] 客户端已初始化，注册 4 个热键（默认 R / V / B / K）");
    }

    private static KeyMapping register(String translationKey, int defaultKey) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(
                translationKey,
                InputConstants.Type.KEYBOARD,
                defaultKey,
                CATEGORY));
    }
}
