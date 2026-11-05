package me.easan.onekey.neoforge.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import me.easan.onekey.common.excavator.BlockInspector;
import me.easan.onekey.common.excavator.BlockPos3i;
import me.easan.onekey.common.excavator.Direction3i;
import me.easan.onekey.common.excavator.MiningShape;
import me.easan.onekey.common.excavator.VeinFinder;
import me.easan.onekey.neoforge.OneKeyNeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * NeoForge 端服务端连锁破坏引擎：
 * 负责玩家挖掘状态维护、防重入递归锁、严格工具防作弊匹配、方块连通批量破坏与掉落物/经验聚合。
 */
public final class ServerExcavatorHandler {

    public static final class PlayerExcavatorState {
        public volatile boolean isPressed;
        public volatile MiningShape shape = MiningShape.VEIN;
    }

    private static final Map<UUID, PlayerExcavatorState> PLAYER_STATES = new ConcurrentHashMap<>();

    // 递归防重入保护，防止连锁破坏后续方块再次触发事件导致栈溢出
    private static final ThreadLocal<Boolean> IS_MINING = ThreadLocal.withInitial(() -> false);

    // 掉落物与经验球内存聚合收集器
    private static final ThreadLocal<Boolean> COLLECTING_DROPS = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<List<ItemStack>> CAPTURED_DROPS = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<Integer> CAPTURED_XP = ThreadLocal.withInitial(() -> 0);

    private static boolean isEquivalentOre(Block origin, Block current) {
        if (origin == current) {
            return true;
        }
        String origId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(origin).getPath();
        String currId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(current).getPath();
        String origBase = origId.startsWith("deepslate_") ? origId.substring("deepslate_".length()) : origId;
        String currBase = currId.startsWith("deepslate_") ? currId.substring("deepslate_".length()) : currId;
        return origBase.equals(currBase);
    }

    private ServerExcavatorHandler() {
    }

    public static void onPlayerDisconnect(UUID playerUuid) {
        if (playerUuid != null) {
            PLAYER_STATES.remove(playerUuid);
        }
    }

    public static void updateState(UUID playerUuid, boolean isPressed, int shapeId) {
        if (playerUuid == null) {
            return;
        }
        PlayerExcavatorState state = PLAYER_STATES.computeIfAbsent(playerUuid, k -> new PlayerExcavatorState());
        state.isPressed = isPressed;
        state.shape = MiningShape.fromId(shapeId);
    }

    public static boolean isMining() {
        return IS_MINING.get();
    }

    public static boolean isCollectingDrops() {
        return COLLECTING_DROPS.get();
    }

    public static void captureDrop(ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            CAPTURED_DROPS.get().add(stack);
        }
    }

    public static void captureXp(int xp) {
        if (xp > 0) {
            CAPTURED_XP.set(CAPTURED_XP.get() + xp);
        }
    }

    /**
     * 判断方块是否允许被当前玩家的工具进行连锁破坏（反作弊 + 工具匹配）。
     */
    public static boolean canVeinMine(
            ServerPlayer player,
            ServerLevel level,
            BlockPos targetPos,
            BlockState targetState) {
        if (player == null || level == null || targetPos == null || targetState == null) {
            return false;
        }

        // 1. 基岩/不可破坏方块绝对禁止
        float destroySpeed = targetState.getDestroySpeed(level, targetPos);
        if (destroySpeed < 0.0F) {
            return false;
        }

        // 2. 空气跳过
        if (targetState.isAir()) {
            return false;
        }

        ItemStack tool = player.getMainHandItem();

        // 3. 工具防爆保护：耐久 <= 2 时立即刹车
        if (OneKeyNeoForge.config().excavator.preventToolBreak && !tool.isEmpty() && tool.isDamageableItem()) {
            int remainingDurability = tool.getMaxDamage() - tool.getDamageValue();
            if (remainingDurability <= 2) {
                return false;
            }
        }

        // 4. 严格工具匹配（防止作弊力大砖飞）
        if (OneKeyNeoForge.config().excavator.requireValidTool) {
            // 需要特定工具的方块（如矿石、黑曜石、石头）：必须手持正确等级工具且速度大于 1.0
            if (targetState.requiresCorrectToolForDrops()) {
                if (!player.hasCorrectToolForDrops(targetState)) {
                    return false;
                }
                if (tool.getDestroySpeed(targetState) <= 1.0F) {
                    return false;
                }
            } else if (destroySpeed > 0.0F) {
                // 不需要特定工具掉落但有硬度的方块（如原木、泥土）：手持工具挖掘速度必须大于 1.0
                // 树叶与农作物允许空手采集，免除挖掘速度 > 1.0 的强制限制
                boolean isOrganic = targetState.is(BlockTags.LEAVES) || targetState.is(BlockTags.CROPS);
                if (!isOrganic && tool.getDestroySpeed(targetState) <= 1.0F) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * 响应玩家破坏方块时触发连锁破坏。
     */
    public static void onBlockBroken(ServerLevel level, ServerPlayer player, BlockPos originPos, BlockState originState) {
        if (!OneKeyNeoForge.config().excavator.enabled) {
            return;
        }
        if (IS_MINING.get()) {
            return;
        }
        if (player.isSpectator() || player.isCreative()) {
            return;
        }

        PlayerExcavatorState playerState = PLAYER_STATES.get(player.getUUID());
        if (playerState == null || !playerState.isPressed) {
            return;
        }

        // 初始方块若不符合严格工具要求，直接不触发连锁破坏
        if (!canVeinMine(player, level, originPos, originState)) {
            return;
        }

        IS_MINING.set(true);
        try {
            performVeinMine(level, player, originPos, originState, playerState.shape);
        } finally {
            IS_MINING.remove();
        }
    }

    private static void performVeinMine(
            ServerLevel level,
            ServerPlayer player,
            BlockPos originPos,
            BlockState originState,
            MiningShape shape) {
        int maxBlocks = Math.max(1, Math.min(OneKeyNeoForge.config().excavator.maxBlocks, 128));
        boolean includeLeaves = OneKeyNeoForge.config().excavator.includeLeaves;

        Direction playerDir = player.getDirection();
        Direction3i playerFacing = Direction3i.valueOf(playerDir.name());
        Direction3i hitFace = playerFacing.getOpposite();

        BlockInspector inspector = new BlockInspector() {
            @Override
            public boolean matches(BlockPos3i origin, BlockPos3i current, boolean leavesAllowed) {
                BlockPos cPos = new BlockPos(current.x(), current.y(), current.z());
                BlockState cState = level.getBlockState(cPos);

                if (cState.isAir()) {
                    return false;
                }

                // 1. 同一种方块或深层/普通矿石等价匹配（例如普通铁矿与深层铁矿视为同一矿脉）
                if (isEquivalentOre(originState.getBlock(), cState.getBlock())) {
                    return true;
                }

                // 2. 矿石标签等价匹配
                if ((originState.is(BlockTags.IRON_ORES) && cState.is(BlockTags.IRON_ORES))
                        || (originState.is(BlockTags.GOLD_ORES) && cState.is(BlockTags.GOLD_ORES))
                        || (originState.is(BlockTags.COPPER_ORES) && cState.is(BlockTags.COPPER_ORES))) {
                    return true;
                }

                // 3. 原木互相连通匹配
                if (originState.is(BlockTags.LOGS) && cState.is(BlockTags.LOGS)) {
                    return true;
                }

                // 4. 砍树携带相连树叶（由配置开关决定）
                if (leavesAllowed && originState.is(BlockTags.LOGS) && cState.is(BlockTags.LEAVES)) {
                    return true;
                }

                return false;
            }

            @Override
            public boolean isDestructible(BlockPos3i pos) {
                BlockPos bPos = new BlockPos(pos.x(), pos.y(), pos.z());
                BlockState bState = level.getBlockState(bPos);
                return canVeinMine(player, level, bPos, bState);
            }
        };

        BlockPos3i origin3i = new BlockPos3i(originPos.getX(), originPos.getY(), originPos.getZ());
        List<BlockPos3i> targetPositions = VeinFinder.findBlocks(
                shape != null ? shape : MiningShape.VEIN,
                origin3i,
                hitFace,
                playerFacing,
                inspector,
                maxBlocks,
                includeLeaves,
                false
        );

        if (targetPositions.isEmpty()) {
            return;
        }

        boolean consolidate = OneKeyNeoForge.config().excavator.consolidateDrops;
        if (consolidate) {
            COLLECTING_DROPS.set(true);
            CAPTURED_DROPS.get().clear();
            CAPTURED_XP.set(0);
        }

        try {
            for (BlockPos3i target : targetPositions) {
                BlockPos targetPos = new BlockPos(target.x(), target.y(), target.z());
                if (targetPos.equals(originPos)) {
                    continue; // 初始方块跳过（由事件触发源销毁）
                }

                BlockState targetState = level.getBlockState(targetPos);
                if (!canVeinMine(player, level, targetPos, targetState)) {
                    break; // 工具耐久见底或环境发生变化，立即中止
                }

                // 委托原版 ServerPlayerGameMode 破坏，完整遵循领地防作弊、耐久扣除与统计
                boolean destroyed = player.gameMode.destroyBlock(targetPos);
                if (!destroyed) {
                    // 若被领地/保护插件取消，跳过该方块
                    continue;
                }
            }
        } finally {
            if (consolidate) {
                COLLECTING_DROPS.set(false);
            }
        }

        if (consolidate) {
            // 合并相同物品并集中生成在初始方块中心
            List<ItemStack> merged = mergeItemStacks(CAPTURED_DROPS.get());
            for (ItemStack stack : merged) {
                Block.popResource(level, originPos, stack);
            }

            int xp = CAPTURED_XP.get();
            if (xp > 0) {
                ExperienceOrb.award(level, Vec3.atCenterOf(originPos), xp);
            }

            CAPTURED_DROPS.remove();
            CAPTURED_XP.remove();
            COLLECTING_DROPS.remove();
        }
    }

    private static List<ItemStack> mergeItemStacks(List<ItemStack> rawDrops) {
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack drop : rawDrops) {
            if (drop.isEmpty()) {
                continue;
            }
            for (ItemStack existing : merged) {
                if (ItemStack.isSameItemSameComponents(existing, drop)) {
                    int space = existing.getMaxStackSize() - existing.getCount();
                    int transferable = Math.min(drop.getCount(), space);
                    if (transferable > 0) {
                        existing.grow(transferable);
                        drop.shrink(transferable);
                        if (drop.isEmpty()) {
                            break;
                        }
                    }
                }
            }
            if (!drop.isEmpty()) {
                merged.add(drop.copy());
            }
        }
        return merged;
    }
}
