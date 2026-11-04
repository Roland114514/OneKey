package me.easan.onekey.neoforge.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.easan.onekey.neoforge.OneKeyNeoForge;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * 锁定槽位存档级独立存储。
 * 每一个世界存档独立持久化一份锁定记录，各存档互相隔离。
 */
public final class LockedSlotsStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type DATA_TYPE = new TypeToken<Map<String, Map<String, Set<Integer>>>>() {}.getType();

    private static final int MAX_CONTAINERS_PER_PLAYER = 50;
    private static final int MAX_LOCKS_PER_CONTAINER = 64;

    private static final Map<UUID, Map<String, Set<Integer>>> LOCKS = new HashMap<>();
    private static Path currentSavePath = null;
    private static volatile boolean dirty = false;

    private LockedSlotsStorage() {
    }

    public static synchronized void markDirty() {
        dirty = true;
    }

    public static synchronized void init(MinecraftServer server) {
        LOCKS.clear();
        dirty = false;
        try {
            Path root = server.getWorldPath(LevelResource.ROOT);
            currentSavePath = root.resolve("onekey").resolve("locked_slots.json");
            load();
            OneKeyNeoForge.LOGGER.info("[OneKey] 已加载存档锁定槽位数据：{}", currentSavePath);
        } catch (Exception exception) {
            OneKeyNeoForge.LOGGER.warn("[OneKey] 初始化存档锁定槽位存储失败", exception);
        }
    }

    public static synchronized void save(MinecraftServer server) {
        if (currentSavePath == null || !dirty) {
            return;
        }
        forceWrite();
    }

    public static synchronized void saveAndClose(MinecraftServer server) {
        if (currentSavePath != null && dirty) {
            forceWrite();
        }
        LOCKS.clear();
        currentSavePath = null;
        dirty = false;
    }

    private static void forceWrite() {
        if (currentSavePath == null) {
            return;
        }
        try {
            Path parent = currentSavePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            Map<String, Map<String, Set<Integer>>> serialized = new HashMap<>();
            for (Map.Entry<UUID, Map<String, Set<Integer>>> entry : LOCKS.entrySet()) {
                serialized.put(entry.getKey().toString(), entry.getValue());
            }

            Path tempPath = currentSavePath.resolveSibling("locked_slots.json.tmp");
            try (Writer writer = Files.newBufferedWriter(tempPath)) {
                GSON.toJson(serialized, writer);
            }
            try {
                Files.move(tempPath, currentSavePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveException) {
                Files.move(tempPath, currentSavePath, StandardCopyOption.REPLACE_EXISTING);
            }

            dirty = false;
            OneKeyNeoForge.LOGGER.debug("[OneKey] 锁定槽位已保存：{}", currentSavePath);
        } catch (IOException exception) {
            OneKeyNeoForge.LOGGER.warn("[OneKey] 保存锁定槽位失败：{}", currentSavePath, exception);
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
            OneKeyNeoForge.LOGGER.warn("[OneKey] 读取锁定槽位失败：{}", currentSavePath, exception);
        }
    }

    public static synchronized Set<Integer> getLockedSlots(UUID playerUuid, String containerKey) {
        if (playerUuid == null || containerKey == null) {
            return Collections.emptySet();
        }
        Map<String, Set<Integer>> playerLocks = LOCKS.get(playerUuid);
        if (playerLocks == null) {
            return Collections.emptySet();
        }
        Set<Integer> slots = playerLocks.get(containerKey);
        return (slots == null || slots.isEmpty()) ? Collections.emptySet() : Set.copyOf(slots);
    }

    public static synchronized boolean setSlotLocked(UUID playerUuid, String containerKey, int slotIndex, boolean locked) {
        if (playerUuid == null || containerKey == null || containerKey.length() > 64 || slotIndex < 0 || slotIndex > 300) {
            return false;
        }

        Map<String, Set<Integer>> playerLocks = LOCKS.computeIfAbsent(playerUuid, k -> new HashMap<>());

        if (locked) {
            if (playerLocks.size() >= MAX_CONTAINERS_PER_PLAYER && !playerLocks.containsKey(containerKey)) {
                return false;
            }
            Set<Integer> slots = playerLocks.computeIfAbsent(containerKey, k -> new HashSet<>());
            if (slots.size() >= MAX_LOCKS_PER_CONTAINER) {
                return false;
            }
            boolean changed = slots.add(slotIndex);
            if (changed) {
                dirty = true;
            }
            return changed;
        } else {
            Set<Integer> slots = playerLocks.get(containerKey);
            if (slots != null) {
                boolean changed = slots.remove(slotIndex);
                if (slots.isEmpty()) {
                    playerLocks.remove(containerKey);
                }
                if (changed) {
                    dirty = true;
                }
                return changed;
            }
            return false;
        }
    }

    public static synchronized boolean isSlotLocked(UUID playerUuid, String containerKey, int slotIndex) {
        return getLockedSlots(playerUuid, containerKey).contains(slotIndex);
    }
}
