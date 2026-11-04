package me.easan.onekey.neoforge;

import java.nio.file.Path;
import java.util.Set;
import me.easan.onekey.common.config.ConfigCodec;
import me.easan.onekey.common.config.OneKeyConfig;
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
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
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
        PayloadRegistrar registrar = event.registrar(MOD_ID).versioned("1");

        // 一键整理通道
        registrar.playToServer(SortRequestPayload.TYPE, SortRequestPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ServerSortingHandler.handle(payload.request(), ctx)));
        registrar.playToClient(SortResultPayload.TYPE, SortResultPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientSortingHandler.handle(payload.result(), ctx)));

        // 快速堆叠与存入通道
        registrar.playToServer(QuickStackRequestPayload.TYPE, QuickStackRequestPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ServerQuickStackHandler.handle(payload.request(), ctx)));
        registrar.playToClient(QuickStackResultPayload.TYPE, QuickStackResultPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientQuickStackHandler.handle(payload.result(), ctx)));

        // 鼠标滚轮单件微调通道
        registrar.playToServer(ScrollItemTransferPayload.TYPE, ScrollItemTransferPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ServerScrollHandler.handle(payload, ctx)));

        // 槽位锁定通道
        registrar.playToServer(RequestSlotLocksPayload.TYPE, RequestSlotLocksPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> handleRequestSlotLocks(payload, ctx)));
        registrar.playToClient(SyncSlotLocksPayload.TYPE, SyncSlotLocksPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientLockedSlots.setLocks(payload.containerKey(), payload.lockedSlots())));
        registrar.playToServer(ToggleSlotLockPayload.TYPE, ToggleSlotLockPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> handleToggleSlotLock(payload, ctx)));
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
        if (payload.containerKey() == null || payload.containerKey().length() > 64 || payload.slotIndex() < 0 || payload.slotIndex() > 300) {
            return;
        }
        if ("player_inventory".equals(payload.containerKey()) && (payload.slotIndex() < 9 || payload.slotIndex() >= 36)) {
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
        }
    }
}
