package me.easan.onekey.fabric;

import java.io.IOException;
import java.nio.file.Path;

import me.easan.onekey.common.config.ConfigCodec;
import me.easan.onekey.common.config.OneKeyConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric 端入口：加载配置、打印版本信息。
 *
 * <p>服务端排序与网络包在 M3 里程碑补全，见 PLAN.md。
 */
public final class OneKeyFabric implements ModInitializer {

    public static final String MOD_ID = "onekey";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static volatile OneKeyConfig config = new OneKeyConfig();

    public static OneKeyConfig config() {
        return config;
    }

    @Override
    public void onInitialize() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json");
        try {
            config = ConfigCodec.load(configPath);
            ConfigCodec.save(configPath, config);
            LOGGER.info("[OneKey] 配置已加载：{}", configPath);
        } catch (IOException exception) {
            LOGGER.warn("[OneKey] 配置读取失败，使用默认值", exception);
            config = new OneKeyConfig();
        }
        LOGGER.info("[OneKey] 已初始化（Minecraft 26.3 / Fabric）");
    }
}
