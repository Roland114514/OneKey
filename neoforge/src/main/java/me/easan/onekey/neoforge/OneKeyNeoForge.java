package me.easan.onekey.neoforge;

import java.nio.file.Path;
import java.util.Set;
import me.easan.onekey.common.config.ConfigCodec;
import me.easan.onekey.common.config.OneKeyConfig;
import me.easan.onekey.common.inventory.InventorySlots;
import me.easan.onekey.common.net.ProtocolVersion;
import me.easan.onekey.neoforge.client.ClientLockedSlots;
import me.easan.onekey.neoforge.client.ClientQuickStackHandler;
import me.easan.onekey.neoforge.client.ClientSortingHandler;
import me.easan.onekey.neoforge.client.OneKeyNeoForgeClient;
import me.easan.onekey.neoforge.net.QuickStackRequestPayload;
import me.easan.onekey.neoforge.net.QuickStackResultPayload;
import me.easan.onekey.neoforge.net.RequestSlotLocksPayload;
import me.easan.onekey.neoforge.net.ScrollItemTransferPayload;
import me.easan.onekey.neoforge.net.SortRequestPayload;
import me.easan.onekey.neoforge.net.SortResultPayload;
import me.easan.onekey.neoforge.net.SyncSlotLocksPayload;
import me.easan.onekey.neoforge.net.ToggleSlotLockPayload;
import me.easan.onekey.neoforge.server.ServerQuickStackHandler;
import me.easan.onekey.neoforge.server.ServerScrollHandler;
import me.easan.onekey.neoforge.server.ServerSortingHandler;
import me.easan.onekey.neoforge.storage.LockedSlotsStorage;
import me.easan.onekey.neoforge.util.ItemConversion;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OneKey NeoForge 主入口。
 */
@Mod(OneKeyNeoForge.MOD_ID)
public final class OneKeyNeoForge {

    public static final String MOD_ID = "onekey";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static OneKeyConfig config = new OneKeyConfig();
    private static Path configPath;

    public OneKeyNeoForge(IEventBus modEventBus) {
        configPath = FMLPaths.CONFIGDIR.get().resolve("onekey.json");
        try {
            // 与 Fabric 端对齐：损坏配置先留档为 onekey.json.corrupt，再重建默认值，
            // 否则容错的 fromJson 会让玩家原始配置被默认值静默覆盖、事后无从恢复。
            Path corruptedBackup = ConfigCodec.backupIfCorrupted(configPath);
            if (corruptedBackup != null) {
                LOGGER.warn("[OneKey-NeoForge] 配置文件内容损坏，已备份为 {} 并重建默认配置", corruptedBackup);
            }
            config = ConfigCodec.load(configPath);
            ConfigCodec.save(configPath, config);
            LOGGER.info("[OneKey-NeoForge] 模组已加载，配置路径：{}", configPath);
        } catch (Exception e) {
            LOGGER.warn("[OneKey-NeoForge] 配置读取失败，使用默认值", e);
            config = new OneKeyConfig();
        }

        // 1. 注册网络数据包
        modEventBus.addListener(this::registerPayloadHandlers);

        // 2. 客户端专用初始化
        if (FMLEnvironment.getDist().isClient()) {
            OneKeyNeoForgeClient.init(modEventBus);
        }

        // 3. 注册服务端生命周期事件
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onLevelSave);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedOut);
        // 数据包重载（含 /reload）时刷新物品分类缓存，与 Fabric 端 START_DATA_PACK_RELOAD 行为对齐
        NeoForge.EVENT_BUS.addListener((AddServerReloadListenersEvent reloadEvent) -> ItemConversion.clearCache());
    }

    public static OneKeyConfig config() {
        return config;
    }

    public static void saveConfig() {
        if (configPath != null) {
            try {
                ConfigCodec.save(configPath, config);
            } catch (Exception e) {
                LOGGER.warn("[OneKey-NeoForge] 保存配置失败", e);
            }
        }
    }

    private void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        // 协议版本号与 common 的协议常量保持单一来源，避免将来升降协议时只改一端
        PayloadRegistrar registrar = event.registrar(MOD_ID).versioned(Integer.toString(ProtocolVersion.CURRENT));

        // 一键整理通道 (C2S)
        registrar.playToServer(SortRequestPayload.TYPE, SortRequestPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ServerSortingHandler.handle(payload.request(), ctx)));

        // 快速堆叠与存入通道 (C2S)
        registrar.playToServer(QuickStackRequestPayload.TYPE, QuickStackRequestPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ServerQuickStackHandler.handle(payload.request(), ctx)));

        // 鼠标滚轮单件微调通道 (C2S)
        registrar.playToServer(ScrollItemTransferPayload.TYPE, ScrollItemTransferPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ServerScrollHandler.handle(payload, ctx)));

        // 槽位锁定通道 (C2S)
        registrar.playToServer(RequestSlotLocksPayload.TYPE, RequestSlotLocksPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> handleRequestSlotLocks(payload, ctx)));
        registrar.playToServer(ToggleSlotLockPayload.TYPE, ToggleSlotLockPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> handleToggleSlotLock(payload, ctx)));

        // 客户端专用回包 (S2C)：仅在物理客户端注册客户端处理器，专用服务端仅注册数据包结构避免类加载冲突
        if (FMLEnvironment.getDist().isClient()) {
            registrar.playToClient(SortResultPayload.TYPE, SortResultPayload.CODEC,
                    (payload, ctx) -> ctx.enqueueWork(() -> ClientSortingHandler.handle(payload.result(), ctx)));
            registrar.playToClient(QuickStackResultPayload.TYPE, QuickStackResultPayload.CODEC,
                    (payload, ctx) -> ctx.enqueueWork(() -> ClientQuickStackHandler.handle(payload.result(), ctx)));
            registrar.playToClient(SyncSlotLocksPayload.TYPE, SyncSlotLocksPayload.CODEC,
                    (payload, ctx) -> ctx.enqueueWork(() -> ClientLockedSlots.setLocks(payload.containerKey(), payload.lockedSlots())));
        } else {
            registrar.playToClient(SortResultPayload.TYPE, SortResultPayload.CODEC);
            registrar.playToClient(QuickStackResultPayload.TYPE, QuickStackResultPayload.CODEC);
            registrar.playToClient(SyncSlotLocksPayload.TYPE, SyncSlotLocksPayload.CODEC);
        }
    }

    private void handleRequestSlotLocks(RequestSlotLocksPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (payload.containerKey() == null || payload.containerKey().length() > 64) {
            return;
        }
        Set<Integer> locked = LockedSlotsStorage.getLockedSlots(player.getUUID(), payload.containerKey());
        context.reply(new SyncSlotLocksPayload(payload.containerKey(), locked));
    }

    private void handleToggleSlotLock(ToggleSlotLockPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player.isSpectator() || player.isDeadOrDying()) {
            return;
        }
        if (payload.containerKey() == null || payload.containerKey().length() > 64 || payload.slotIndex() < 0) {
            return;
        }
        if ("player_inventory".equals(payload.containerKey())) {
            // 0..35 全部允许加锁（含快捷栏）；盔甲 36..39 与副手 40 不参与任何功能，禁止加锁
            if (!InventorySlots.isPlayerInventorySlot(payload.slotIndex())) {
                return;
            }
        } else if (player.containerMenu == null || payload.slotIndex() >= player.containerMenu.slots.size()) {
            // 外部容器：槽位号必须落在当前打开菜单的真实槽位范围内（与 Fabric 端同一契约）
            return;
        }

        boolean current = LockedSlotsStorage.isSlotLocked(player.getUUID(), payload.containerKey(), payload.slotIndex());
        LockedSlotsStorage.setSlotLocked(player.getUUID(), payload.containerKey(), payload.slotIndex(), !current);

        Set<Integer> updated = LockedSlotsStorage.getLockedSlots(player.getUUID(), payload.containerKey());
        context.reply(new SyncSlotLocksPayload(payload.containerKey(), updated));
    }

    private void onServerStarted(ServerStartedEvent event) {
        LockedSlotsStorage.init(event.getServer());
    }

    private void onLevelSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel sl && sl.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            LockedSlotsStorage.save(sl.getServer());
        }
    }

    private void onServerStopping(ServerStoppingEvent event) {
        LockedSlotsStorage.saveAndClose(event.getServer());
    }

    private void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerSortingHandler.onPlayerDisconnect(player.getUUID());
            ServerQuickStackHandler.onPlayerDisconnect(player.getUUID());
            ServerScrollHandler.onPlayerDisconnect(player.getUUID());
        }
    }
}
