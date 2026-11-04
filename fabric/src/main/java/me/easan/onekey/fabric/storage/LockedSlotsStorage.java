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
import java.nio.file.StandardCopyOption;
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
 *
 * <p>槽位索引约定（与 {@code ServerSortingHandler} 读取侧保持一致）：
 * <ul>
 *   <li>玩家背包（containerKey = {@code "player_inventory"}）：使用 {@code Slot.getContainerSlot()}，范围 0..35
 *       （玩家背包 0..35，含快捷栏；盔甲 36..39 与副手 40 不参与）。</li>
 *   <li>外部容器（containerKey = 菜单类型 id，如 {@code minecraft:generic_9x3}）：使用菜单槽位号 {@code Slot.index}。</li>
 * </ul>
 */
public final class LockedSlotsStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type DATA_TYPE = new TypeToken<Map<String, Map<String, Set<Integer>>>>() {}.getType();

    private static final int MAX_CONTAINERS_PER_PLAYER = 50;
    private static final int MAX_LOCKS_PER_CONTAINER = 64;

    // playerUuid -> (containerKey -> Set<slotIndex>)
    private static final Map<UUID, Map<String, Set<Integer>>> LOCKS = new HashMap<>();
    private static Path currentSavePath = null;
    private static volatile boolean dirty = false;

    private LockedSlotsStorage() {
    }

    /** 标记数据有变动，等待统一周期写盘。 */
    public static synchronized void markDirty() {
        dirty = true;
    }

    /** 世界启动时加载本世界的锁定槽位配置。 */
    public static synchronized void init(MinecraftServer server) {
        LOCKS.clear();
        dirty = false;
        try {
            Path root = server.getWorldPath(LevelResource.ROOT);
            currentSavePath = root.resolve("onekey").resolve("locked_slots.json");
            load();
            OneKeyFabric.LOGGER.info("[OneKey] 已加载存档锁定槽位数据：{}", currentSavePath);
        } catch (Exception exception) {
            OneKeyFabric.LOGGER.warn("[OneKey] 初始化存档锁定槽位存储失败", exception);
        }
    }

    /**
     * 世界保存/自动保存触发时的写盘。仅在 dirty 为 true 时才真正执行 I/O，避免无意义主线程卡顿。
     */
    public static synchronized void save(MinecraftServer server) {
        if (currentSavePath == null || !dirty) {
            return;
        }
        forceWrite();
    }

    /** 关服时强制写盘并释放当前存档引用与内存。 */
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

            // 采用 .tmp 原子替换写盘，防止突发停电或崩溃导致 JSON 文件损坏或截断
            Path tempPath = currentSavePath.resolveSibling("locked_slots.json.tmp");
            try (Writer writer = Files.newBufferedWriter(tempPath)) {
                GSON.toJson(serialized, writer);
            }
            try {
                Files.move(tempPath, currentSavePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveException) {
                // 部分文件系统不支持 ATOMIC_MOVE 时平滑降级
                Files.move(tempPath, currentSavePath, StandardCopyOption.REPLACE_EXISTING);
            }

            dirty = false;
            OneKeyFabric.LOGGER.debug("[OneKey] 锁定槽位已保存：{}", currentSavePath);
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

    /**
     * 获取玩家在特定容器中的锁定槽位集合。
     * 返回不可变快照副本，避免多线程并发修改异常。
     */
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

    /**
     * 设置槽位锁定状态（含输入合法性校验与数量防 DoS 约束）。
     *
     * @return 状态是否实际发生改变
     */
    public static synchronized boolean setSlotLocked(UUID playerUuid, String containerKey, int slotIndex, boolean locked) {
        if (playerUuid == null || containerKey == null || containerKey.length() > 64 || slotIndex < 0 || slotIndex > 300) {
            return false;
        }

        Map<String, Set<Integer>> playerLocks = LOCKS.computeIfAbsent(playerUuid, k -> new HashMap<>());

        if (locked) {
            // 防内存滥用：限制单个玩家记录的容器类型数
            if (playerLocks.size() >= MAX_CONTAINERS_PER_PLAYER && !playerLocks.containsKey(containerKey)) {
                return false;
            }
            Set<Integer> slots = playerLocks.computeIfAbsent(containerKey, k -> new HashSet<>());
            // 限制单个容器锁定的槽位数
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

    /** 判断槽位是否已锁定。 */
    public static synchronized boolean isSlotLocked(UUID playerUuid, String containerKey, int slotIndex) {
        return getLockedSlots(playerUuid, containerKey).contains(slotIndex);
    }
}
