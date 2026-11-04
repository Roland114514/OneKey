package me.easan.onekey.neoforge.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.easan.onekey.common.config.OneKeyConfig;
import me.easan.onekey.common.net.QuickStackRequest;
import me.easan.onekey.common.net.QuickStackResult;
import me.easan.onekey.neoforge.OneKeyNeoForge;
import me.easan.onekey.neoforge.net.QuickStackResultPayload;
import me.easan.onekey.neoforge.storage.LockedSlotsStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端快速堆叠与一键存入处理器（NeoForge）：
 * 采用区块 BlockEntity 智能检索（常数级耗时，彻底杜绝暴力遍历方块造成的卡顿）。
 * 两阶段算法严格保护底部 9 格快捷栏与玩家锁定槽位，全流程总物品数量守恒。
 */
public final class ServerQuickStackHandler {

    private static final Map<UUID, Long> LAST_QUICK_STACK_TIME = new ConcurrentHashMap<>();

    private record ScannedContainer(Container container, BlockPos pos, double distSq) {
    }

    private ServerQuickStackHandler() {
    }

    public static void onPlayerDisconnect(UUID playerUuid) {
        if (playerUuid != null) {
            LAST_QUICK_STACK_TIME.remove(playerUuid);
        }
    }

    public static void handle(QuickStackRequest request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player.isSpectator() || player.isDeadOrDying()) {
            return;
        }

        // 若玩家光标当前正拿着物品，安全跳过，防止发生坐标与点击同步错位
        if (player.containerMenu != null && !player.containerMenu.getCarried().isEmpty()) {
            return;
        }

        OneKeyConfig config = OneKeyNeoForge.config();

        // 校验冷却时间
        long now = System.currentTimeMillis();
        long lastTime = LAST_QUICK_STACK_TIME.getOrDefault(player.getUUID(), 0L);
        if (now - lastTime < config.sort.cooldownMs) {
            return;
        }
        LAST_QUICK_STACK_TIME.put(player.getUUID(), now);

        // 半径以「服务端配置」为硬上限：客户端可自填 1..32，若直接采信就能绕过服主调小的扫描范围
        int requestedRadius = request.radius() > 0 ? request.radius() : config.quickStack.radius;
        int radius = Math.clamp(Math.min(requestedRadius, config.quickStack.radius), 1, 32);
        boolean stashMode = request.stash() || config.quickStack.stashMode;

        // 1. 高性能区块检索附近纯存储容器
        List<Container> targetContainers = findNearbyContainers(player, radius, config);
        if (targetContainers.isEmpty()) {
            context.reply(new QuickStackResultPayload(new QuickStackResult(0)));
            return;
        }

        // 2. 遍历玩家背包主槽位（9..35，快捷栏 0..8 与锁定槽位永久隔离）
        Inventory playerInv = player.getInventory();
        Set<Integer> lockedSlots = LockedSlotsStorage.getLockedSlots(player.getUUID(), "player_inventory");
        Set<Container> modifiedContainers = new HashSet<>();
        int totalMoved = 0;

        for (int slotIdx = 9; slotIdx < 36; slotIdx++) {
            if (lockedSlots.contains(slotIdx)) {
                continue;
            }

            ItemStack playerStack = playerInv.getItem(slotIdx);
            if (playerStack.isEmpty()) {
                continue;
            }

            Identifier itemId = BuiltInRegistries.ITEM.getKey(playerStack.getItem());
            String itemIdStr = itemId != null ? itemId.toString() : "";
            // 物品维度用 itemWhitelist/itemBlacklist；whitelist/blacklist 专指容器方块，
            // 不可混用（旧实现把物品 id 与方块 id 塞进同一列表，白名单填物品会让所有容器被过滤）。
            if (!config.quickStack.itemBlacklist.isEmpty() && config.quickStack.itemBlacklist.contains(itemIdStr)) {
                continue;
            }
            if (!config.quickStack.itemWhitelist.isEmpty() && !config.quickStack.itemWhitelist.contains(itemIdStr)) {
                continue;
            }

            for (Container target : targetContainers) {
                if (playerStack.isEmpty()) {
                    break;
                }

                // 判断容器是否已包含该物品
                boolean containerHasItem = false;
                for (int c = 0; c < target.getContainerSize(); c++) {
                    ItemStack cStack = target.getItem(c);
                    if (!cStack.isEmpty() && ItemStack.isSameItemSameComponents(cStack, playerStack)) {
                        containerHasItem = true;
                        break;
                    }
                }

                // 快速堆叠模式下，若容器无该同类物品则跳过
                if (!stashMode && !containerHasItem) {
                    continue;
                }

                // 阶段一：补齐未满的同类物品堆叠
                for (int c = 0; c < target.getContainerSize(); c++) {
                    if (playerStack.isEmpty()) {
                        break;
                    }
                    ItemStack cStack = target.getItem(c);
                    if (!cStack.isEmpty() && ItemStack.isSameItemSameComponents(cStack, playerStack)) {
                        int maxStack = Math.min(target.getMaxStackSize(cStack), cStack.getMaxStackSize());
                        int available = maxStack - cStack.getCount();
                        if (available > 0) {
                            int toMove = Math.min(playerStack.getCount(), available);
                            cStack.grow(toMove);
                            playerStack.shrink(toMove);
                            totalMoved += toMove;
                            modifiedContainers.add(target);
                        }
                    }
                }

                // 阶段二：存入空槽（若为一键存入 stashMode，或该容器本已有该同类物品）
                if (!playerStack.isEmpty() && (stashMode || containerHasItem)) {
                    for (int c = 0; c < target.getContainerSize(); c++) {
                        if (playerStack.isEmpty()) {
                            break;
                        }
                        ItemStack cStack = target.getItem(c);
                        if (cStack.isEmpty() && target.canPlaceItem(c, playerStack)) {
                            int maxStack = Math.min(target.getMaxStackSize(playerStack), playerStack.getMaxStackSize());
                            int toMove = Math.min(playerStack.getCount(), maxStack);
                            target.setItem(c, playerStack.copyWithCount(toMove));
                            playerStack.shrink(toMove);
                            totalMoved += toMove;
                            modifiedContainers.add(target);
                        }
                    }
                }
            }

            if (playerStack.isEmpty()) {
                playerInv.setItem(slotIdx, ItemStack.EMPTY);
            }
        }

        // 3. 变更写回与广播
        if (totalMoved > 0) {
            for (Container c : modifiedContainers) {
                c.setChanged();
            }
            playerInv.setChanged();
            player.containerMenu.broadcastChanges();
        }

        // 4. 回包
        context.reply(new QuickStackResultPayload(new QuickStackResult(totalMoved)));
    }

    private static List<Container> findNearbyContainers(ServerPlayer player, int radius, OneKeyConfig config) {
        ServerLevel level = player.level();
        int px = player.getBlockX();
        int py = player.getBlockY();
        int pz = player.getBlockZ();
        int minChunkX = (px - radius) >> 4;
        int maxChunkX = (px + radius) >> 4;
        int minChunkZ = (pz - radius) >> 4;
        int maxChunkZ = (pz + radius) >> 4;

        double radiusSq = radius * radius;
        Set<BlockPos> visited = new HashSet<>();
        List<ScannedContainer> scanned = new ArrayList<>();

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                    BlockPos pos = entry.getKey();
                    if (visited.contains(pos)) {
                        continue;
                    }

                    double distSq = pos.distToCenterSqr(player.position());
                    if (distSq > radiusSq) {
                        continue;
                    }

                    BlockEntity be = entry.getValue();

                    // 严禁接触尚未开封的战利品箱
                    if (be instanceof RandomizableContainerBlockEntity randomizable && randomizable.getLootTable() != null) {
                        continue;
                    }

                    // 玩家无权开启的容器严禁存入
                    if (be instanceof BaseContainerBlockEntity baseContainer && !baseContainer.canOpen(player)) {
                        continue;
                    }

                    BlockState state = be.getBlockState();
                    Block block = state.getBlock();
                    Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
                    String blockIdStr = blockId != null ? blockId.toString() : "";

                    if (!config.quickStack.blacklist.isEmpty() && config.quickStack.blacklist.contains(blockIdStr)) {
                        continue;
                    }
                    if (!config.quickStack.whitelist.isEmpty() && !config.quickStack.whitelist.contains(blockIdStr)) {
                        continue;
                    }

                    if (block instanceof ChestBlock chestBlock) {
                        ChestType chestType = state.hasProperty(ChestBlock.TYPE) ? state.getValue(ChestBlock.TYPE) : ChestType.SINGLE;
                        if (chestType == ChestType.LEFT || chestType == ChestType.RIGHT) {
                            Direction connected = ChestBlock.getConnectedDirection(state);
                            visited.add(pos.relative(connected));
                        }
                        visited.add(pos);

                        Container chestContainer = ChestBlock.getContainer(chestBlock, state, level, pos, false);
                        if (chestContainer != null && chestContainer.stillValid(player)) {
                            scanned.add(new ScannedContainer(chestContainer, pos, distSq));
                        }
                    } else if (be instanceof BarrelBlockEntity barrel) {
                        visited.add(pos);
                        if (barrel.stillValid(player) && barrel.canOpen(player)) {
                            scanned.add(new ScannedContainer(barrel, pos, distSq));
                        }
                    } else if (be instanceof ShulkerBoxBlockEntity shulker) {
                        visited.add(pos);
                        if (shulker.stillValid(player) && shulker.canOpen(player)) {
                            scanned.add(new ScannedContainer(shulker, pos, distSq));
                        }
                    }
                }
            }
        }

        scanned.sort(Comparator.comparingDouble(ScannedContainer::distSq));

        List<Container> result = new ArrayList<>(scanned.size());
        for (ScannedContainer sc : scanned) {
            result.add(sc.container());
        }
        return result;
    }
}
