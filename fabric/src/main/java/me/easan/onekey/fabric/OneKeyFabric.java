package me.easan.onekey.fabric;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Set;

import me.easan.onekey.common.config.ConfigCodec;
import me.easan.onekey.common.config.OneKeyConfig;
import me.easan.onekey.common.inventory.InventorySlots;
import me.easan.onekey.fabric.net.QuickStackRequestPayload;
import me.easan.onekey.fabric.net.QuickStackResultPayload;
import me.easan.onekey.fabric.net.RequestSlotLocksPayload;
import me.easan.onekey.fabric.net.ScrollItemTransferPayload;
import me.easan.onekey.fabric.net.SortRequestPayload;
import me.easan.onekey.fabric.net.SortResultPayload;
import me.easan.onekey.fabric.net.SyncSlotLocksPayload;
import me.easan.onekey.fabric.net.ToggleSlotLockPayload;
import me.easan.onekey.fabric.server.ServerQuickStackHandler;
import me.easan.onekey.fabric.server.ServerScrollHandler;
import me.easan.onekey.fabric.server.ServerSortingHandler;
import me.easan.onekey.fabric.storage.LockedSlotsStorage;
import me.easan.onekey.fabric.util.ItemConversion;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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

    /**
     * 配置 JSON 损坏时先把原文件备份为 onekey.json.corrupt 再重建默认配置，
     * 避免玩家的原始内容被默认值静默覆盖、事后无从排查。
     */
    private static void backupCorruptedConfig(Path configPath) {
        try {
            Path backup = ConfigCodec.backupIfCorrupted(configPath);
            if (backup != null) {
                LOGGER.warn("[OneKey] 配置文件内容损坏，已备份为 {} 并重建默认配置", backup);
            }
        } catch (Exception exception) {
            LOGGER.warn("[OneKey] 检查配置文件时出错，将直接使用默认配置", exception);
        }
    }

    @Override
    public void onInitialize() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json");
        try {
            backupCorruptedConfig(configPath);
            config = ConfigCodec.load(configPath);
            ConfigCodec.save(configPath, config);
            LOGGER.info("[OneKey] 配置已加载：{}", configPath);
        } catch (Exception exception) {
            // 配置解析/读写异常绝不允许冒泡到模组入口，否则整个游戏启动失败
            LOGGER.warn("[OneKey] 配置读取失败，使用默认值", exception);
            config = new OneKeyConfig();
        }

        // 1. 注册网络 Payload 编解码器
        PayloadTypeRegistry.serverboundPlay().register(SortRequestPayload.TYPE, SortRequestPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SortResultPayload.TYPE, SortResultPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RequestSlotLocksPayload.TYPE, RequestSlotLocksPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ToggleSlotLockPayload.TYPE, ToggleSlotLockPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SyncSlotLocksPayload.TYPE, SyncSlotLocksPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(QuickStackRequestPayload.TYPE, QuickStackRequestPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(QuickStackResultPayload.TYPE, QuickStackResultPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ScrollItemTransferPayload.TYPE, ScrollItemTransferPayload.CODEC);

        // 2. 注册服务端整理、快速堆叠与滚轮微调请求处理器
        ServerPlayNetworking.registerGlobalReceiver(SortRequestPayload.TYPE, (payload, context) -> {
            ServerSortingHandler.handle(payload.request(), context);
        });

        ServerPlayNetworking.registerGlobalReceiver(QuickStackRequestPayload.TYPE, (payload, context) -> {
            ServerQuickStackHandler.handle(payload.request(), context);
        });

        ServerPlayNetworking.registerGlobalReceiver(ScrollItemTransferPayload.TYPE, (payload, context) -> {
            ServerScrollHandler.handle(payload, context);
        });

        // 3. 注册服务端槽位锁定请求处理器（含合法性与内存防 DoS 校验）
        ServerPlayNetworking.registerGlobalReceiver(RequestSlotLocksPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (player != null && payload.containerKey() != null && payload.containerKey().length() <= 64) {
                Set<Integer> locks = LockedSlotsStorage.getLockedSlots(player.getUUID(), payload.containerKey());
                context.responseSender().sendPacket(new SyncSlotLocksPayload(payload.containerKey(), new ArrayList<>(locks)));
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(ToggleSlotLockPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (player == null || payload.containerKey() == null || payload.containerKey().length() > 64) {
                return;
            }

            // 槽位合法性校验：玩家背包仅允许主背包 9..35（快捷栏 0..8 永久保护、盔甲 36..39 / 副手 40 均不参与整理）
            if ("player_inventory".equals(payload.containerKey())) {
                if (!InventorySlots.isPlayerInventorySlot(payload.slotIndex())) {
                    return;
                }
            } else if (payload.slotIndex() < 0
                    || player.containerMenu == null
                    || payload.slotIndex() >= player.containerMenu.slots.size()) {
                // 外部容器：槽位号必须落在当前打开菜单的真实槽位范围内（不再用宽泛硬编码上界）
                return;
            }

            boolean current = LockedSlotsStorage.isSlotLocked(player.getUUID(), payload.containerKey(), payload.slotIndex());
            boolean changed = LockedSlotsStorage.setSlotLocked(player.getUUID(), payload.containerKey(), payload.slotIndex(), !current);
            if (!changed && !current) {
                LOGGER.warn("[OneKey] 槽位锁定写入被拒绝（可能已达存储上限）：{}#{}", payload.containerKey(), payload.slotIndex());
            }
            // 无论写入成功与否都回发权威快照：客户端本地是乐观锁存，若因上限被拒不回包，
            // 玩家会看到「高亮已锁定、实际没保存」的长期不一致状态。
            Set<Integer> locks = LockedSlotsStorage.getLockedSlots(player.getUUID(), payload.containerKey());
            context.responseSender().sendPacket(new SyncSlotLocksPayload(payload.containerKey(), new ArrayList<>(locks)));
        });

        // 4. 注册存档生命周期（每个世界存档独立持久化锁定槽位；采用脏标记与周期写盘，消除主线程 I/O DoS）
        ServerLifecycleEvents.SERVER_STARTED.register(LockedSlotsStorage::init);
        ServerLifecycleEvents.BEFORE_SAVE.register((server, flush, force) -> LockedSlotsStorage.save(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(LockedSlotsStorage::saveAndClose);
        ServerLifecycleEvents.START_DATA_PACK_RELOAD.register((server, manager) -> ItemConversion.clearCache());

        // 5. 监听玩家离线，及时清理内存中的冷却记录
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (handler.player != null) {
                ServerSortingHandler.onPlayerDisconnect(handler.player.getUUID());
                ServerQuickStackHandler.onPlayerDisconnect(handler.player.getUUID());
                ServerScrollHandler.onPlayerDisconnect(handler.player.getUUID());
            }
        });

        LOGGER.info("[OneKey] 已初始化（Minecraft 26.3 / Fabric）");
    }
}
