package me.easan.onekey.fabric.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.easan.onekey.fabric.OneKeyFabric;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * 锁定槽位存档级独立存储。
 * 每一个世界存档（单人 saves/<world> 或专用服 world/）独立持久化一份锁定记录，各存档互相隔离。
 */
public final class LockedSlotsStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type DATA_TYPE = new TypeToken<Map<String, Map<String, Set<Integer>>>>() {}.getType();

    // playerUuid -> (containerKey -> Set<slotIndex>)
    private static final Map<UUID, Map<String, Set<Integer>>> LOCKS = new HashMap<>();
    private static Path currentSavePath = null;

    private LockedSlotsStorage() {
    }

    /** 世界启动时加载本世界的锁定槽位配置。 */
    public static synchronized void init(MinecraftServer server) {
        LOCKS.clear();
        try {
            Path root = server.getWorldPath(LevelResource.ROOT);
            currentSavePath = root.resolve("onekey").resolve("locked_slots.json");
            load();
            OneKeyFabric.LOGGER.info("[OneKey] 已加载存档锁定槽位数据：{}", currentSavePath);
        } catch (Exception exception) {
            OneKeyFabric.LOGGER.warn("[OneKey] 初始化存档锁定槽位存储失败", exception);
        }
    }

    /** 世界保存/停服时写盘。 */
    public static synchronized void save(MinecraftServer server) {
        if (currentSavePath == null) {
            return;
        }
        try {
            if (currentSavePath.getParent() != null && !Files.exists(currentSavePath.getParent())) {
                Files.createDirectories(currentSavePath.getParent());
            }
            Map<String, Map<String, Set<Integer>>> serialized = new HashMap<>();
            for (Map.Entry<UUID, Map<String, Set<Integer>>> entry : LOCKS.entrySet()) {
                serialized.put(entry.getKey().toString(), entry.getValue());
            }
            try (Writer writer = Files.newBufferedWriter(currentSavePath)) {
                GSON.toJson(serialized, writer);
            }
        } catch (IOException exception) {
            OneKeyFabric.LOGGER.warn("[OneKey] 保存锁定槽位失败：{}", currentSavePath, exception);
        }
    }

    private static void load() {
        if (currentSavePath == null || !Files.exists(currentSavePath)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(currentSavePath)) {
            Map<String, Map<String, Set<Integer>>> data = GSON.fromJson(reader, DATA_TYPE);
            if (data != null) {
                for (Map.Entry<String, Map<String, Set<Integer>>> entry : data.entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(entry.getKey());
                        LOCKS.put(uuid, entry.getValue());
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        } catch (Exception exception) {
            OneKeyFabric.LOGGER.warn("[OneKey] 读取锁定槽位失败：{}", currentSavePath, exception);
        }
    }

    /** 获取玩家在特定容器中的锁定槽位集合（只读）。 */
    public static synchronized Set<Integer> getLockedSlots(UUID playerUuid, String containerKey) {
        Map<String, Set<Integer>> playerLocks = LOCKS.get(playerUuid);
        if (playerLocks == null) {
            return Collections.emptySet();
        }
        Set<Integer> slots = playerLocks.get(containerKey);
        return slots == null ? Collections.emptySet() : Collections.unmodifiableSet(slots);
    }

    /** 设置槽位锁定状态。 */
    public static synchronized void setSlotLocked(UUID playerUuid, String containerKey, int slotIndex, boolean locked) {
        Map<String, Set<Integer>> playerLocks = LOCKS.computeIfAbsent(playerUuid, k -> new HashMap<>());
        Set<Integer> slots = playerLocks.computeIfAbsent(containerKey, k -> new HashSet<>());
        if (locked) {
            slots.add(slotIndex);
        } else {
            slots.remove(slotIndex);
        }
    }

    /** 判断槽位是否已锁定。 */
    public static synchronized boolean isSlotLocked(UUID playerUuid, String containerKey, int slotIndex) {
        return getLockedSlots(playerUuid, containerKey).contains(slotIndex);
    }
}
