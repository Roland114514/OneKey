package me.easan.onekey.fabric;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Set;

import me.easan.onekey.common.config.ConfigCodec;
import me.easan.onekey.common.config.OneKeyConfig;
import me.easan.onekey.fabric.net.RequestSlotLocksPayload;
import me.easan.onekey.fabric.net.SortRequestPayload;
import me.easan.onekey.fabric.net.SortResultPayload;
import me.easan.onekey.fabric.net.SyncSlotLocksPayload;
import me.easan.onekey.fabric.net.ToggleSlotLockPayload;
import me.easan.onekey.fabric.server.ServerSortingHandler;
import me.easan.onekey.fabric.storage.LockedSlotsStorage;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric 端入口：加载配置、注册网络 Payload、注册服务端排序与槽位锁定逻辑及存档生命周期。
 */
public final class OneKeyFabric implements ModInitializer {

    public static final String MOD_ID = "onekey";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static volatile OneKeyConfig config = new OneKeyConfig();

    public static OneKeyConfig config() {
        return config;
    }

    /** 保存配置到 config/onekey.json。 */
    public static synchronized void saveConfig() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json");
        try {
            ConfigCodec.save(configPath, config);
            LOGGER.info("[OneKey] 配置已保存：{}", configPath);
        } catch (IOException exception) {
            LOGGER.warn("[OneKey] 保存配置失败：{}", configPath, exception);
        }
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

        // 1. 注册网络 Payload 编解码器
        PayloadTypeRegistry.serverboundPlay().register(SortRequestPayload.TYPE, SortRequestPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SortResultPayload.TYPE, SortResultPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RequestSlotLocksPayload.TYPE, RequestSlotLocksPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ToggleSlotLockPayload.TYPE, ToggleSlotLockPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SyncSlotLocksPayload.TYPE, SyncSlotLocksPayload.CODEC);

        // 2. 注册服务端整理请求处理器
        ServerPlayNetworking.registerGlobalReceiver(SortRequestPayload.TYPE, (payload, context) -> {
            ServerSortingHandler.handle(payload.request(), context);
        });

        // 3. 注册服务端槽位锁定请求处理器
        ServerPlayNetworking.registerGlobalReceiver(RequestSlotLocksPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (player != null) {
                Set<Integer> locks = LockedSlotsStorage.getLockedSlots(player.getUUID(), payload.containerKey());
                context.responseSender().sendPacket(new SyncSlotLocksPayload(payload.containerKey(), new ArrayList<>(locks)));
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(ToggleSlotLockPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (player != null) {
                boolean current = LockedSlotsStorage.isSlotLocked(player.getUUID(), payload.containerKey(), payload.slotIndex());
                LockedSlotsStorage.setSlotLocked(player.getUUID(), payload.containerKey(), payload.slotIndex(), !current);
                LockedSlotsStorage.save(context.server());
                Set<Integer> locks = LockedSlotsStorage.getLockedSlots(player.getUUID(), payload.containerKey());
                context.responseSender().sendPacket(new SyncSlotLocksPayload(payload.containerKey(), new ArrayList<>(locks)));
            }
        });

        // 4. 注册存档生命周期（每个世界存档独立持久化锁定槽位）
        ServerLifecycleEvents.SERVER_STARTED.register(LockedSlotsStorage::init);
        ServerLifecycleEvents.SERVER_STOPPING.register(LockedSlotsStorage::save);

        LOGGER.info("[OneKey] 已初始化（Minecraft 26.3 / Fabric）");
    }
}
