package me.easan.onekey.fabric.server;

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
import me.easan.onekey.common.net.TargetPos;
import me.easan.onekey.fabric.OneKeyFabric;
import me.easan.onekey.fabric.net.QuickStackResultPayload;
import me.easan.onekey.fabric.storage.LockedSlotsStorage;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * 服务端快速堆叠与一键存入处理器：
 * 采用区块 BlockEntity 智能检索（常数级耗时，杜绝暴力遍历方块造成的卡顿）。
 * 支持准星视线射线锁定目标容器、附着告示牌穿透解析、以及跨容器全局双阶段防污染流水线。
 */
public final class ServerQuickStackHandler {

    private static final Map<UUID, Long> LAST_QUICK_STACK_TIME = new ConcurrentHashMap<>();

    private record ScannedContainer(
            Container container,
            Set<BlockPos> positions,
            double distSq,
            boolean isTargeted,
            boolean allowDumpNewItem
    ) {
    }

    private ServerQuickStackHandler() {
    }

    public static void onPlayerDisconnect(UUID playerUuid) {
        if (playerUuid != null) {
            LAST_QUICK_STACK_TIME.remove(playerUuid);
        }
    }

    public static void handle(QuickStackRequest request, ServerPlayNetworking.Context context) {
        try {
            process(request, context);
        } catch (Throwable throwable) {
            OneKeyFabric.LOGGER.error("[OneKey] 快速堆叠请求处理失败，已回滚界面状态", throwable);
            // 异常可能发生在「部分物品已写入」之后：立刻把权威界面状态推回客户端，避免幽灵物品/丢失
            try {
                ServerPlayer player = context.player();
                if (player != null && player.containerMenu != null) {
                    player.containerMenu.sendAllDataToRemote();
                }
                context.responseSender().sendPacket(new QuickStackResultPayload(new QuickStackResult(0)));
            } catch (Throwable ignored) {
                // 回执失败也不再抛出，避免打断网络线程
            }
        }
    }

    private static void process(QuickStackRequest request, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        if (player == null || player.isSpectator() || player.isDeadOrDying()) {
            return;
        }

        // 校验容器同步 ID：拒绝过期/伪造请求，保证请求与玩家当前打开的菜单一一对应
        AbstractContainerMenu currentMenu = player.containerMenu;
        if (currentMenu == null || currentMenu.containerId != request.syncId()) {
            return;
        }

        // 若玩家光标当前正拿着物品，安全跳过，防止发生坐标与点击同步错位
        if (!currentMenu.getCarried().isEmpty()) {
            return;
        }

        OneKeyConfig config = OneKeyFabric.config();

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

        // 解析并校验客户端上报的准星目标方块坐标（防作弊距离校验）
        BlockPos validTargetPos = null;
        if (request.targetPos().isPresent()) {
            TargetPos candidate = request.targetPos().get();
            // 反作弊：准星目标必须落在「原版方块交互距离」内，禁止客户端伪造远处坐标实现隔空存入
            BlockPos candidatePos = new BlockPos(candidate.x(), candidate.y(), candidate.z());
            if (player.isWithinBlockInteractionRange(candidatePos, 1.0)) {
                validTargetPos = candidatePos;
            }
        }

        // 提取玩家当前打开的界面对应的容器（若处于箱子 GUI 内，该容器天然具备锁定优先级）
        Container openMenuContainer = getOpenMenuContainer(currentMenu);

        // 1. 高性能区块检索附近纯存储容器并匹配准星目标（含告示牌穿透与大箱子归一化）
        List<ScannedContainer> targetContainers = findNearbyContainers(player, radius, config, validTargetPos, openMenuContainer, stashMode);

        // 末影箱界面：容器是玩家个人存储，不在方块扫描范围内，显式绑定为当前 GUI 目标（需求③界面强绑定）
        if (openMenuContainer instanceof PlayerEnderChestContainer enderChest
                && targetContainers.stream().noneMatch(sc -> sc.container() == enderChest)) {
            targetContainers.add(new ScannedContainer(enderChest, Set.of(), 0.0, true, stashMode));
            // 追加后重新排序：界面绑定的末影箱同样应排在队首，而不是因 append 顺序落到末尾
            targetContainers.sort(Comparator.comparing(ScannedContainer::isTargeted).reversed()
                    .thenComparingDouble(ScannedContainer::distSq));
        }

        if (targetContainers.isEmpty()) {
            context.responseSender().sendPacket(new QuickStackResultPayload(new QuickStackResult(0)));
            return;
        }

        // 2. 遍历玩家背包主槽位（9..35，快捷栏 0..8 与锁定槽位永久隔离）
        Inventory playerInv = player.getInventory();
        Set<Integer> lockedSlots = LockedSlotsStorage.getLockedSlots(player.getUUID(), "player_inventory");
        Set<Container> modifiedContainers = new HashSet<>();

        int totalMoved = 0;
        try {
            totalMoved = transferInventoryIntoContainers(playerInv, lockedSlots, targetContainers, config, modifiedContainers);
        } finally {
            // 即使中途抛异常也要先把「已经发生的写入」标脏落盘，避免内存里背包已扣、容器已加，
            // 但区块未标脏导致重启后物品凭空消失（异常本身继续向上抛，由 handle(...) 回滚界面状态）。
            for (Container modified : modifiedContainers) {
                modified.setChanged();
            }
            playerInv.setChanged();
        }

        // 3. 广播（标脏已在 finally 中完成，异常路径同样落盘）
        if (totalMoved > 0) {
            player.containerMenu.broadcastChanges();
        }

        // 4. 回包
        context.responseSender().sendPacket(new QuickStackResultPayload(new QuickStackResult(totalMoved)));
    }

    /**
     * 判断扫描到的容器是否就是玩家当前界面里打开的那个容器。
     * 单箱子/木桶/潜影盒是同一个对象，直接比引用即可；大箱子则不然——菜单侧（{@code MENU_PROVIDER_COMBINER}）
     * 与扫描侧（{@code CHEST_COMBINER}）各自 new 了一个 {@link CompoundContainer}，而它的 equals 未被重写，
     * 需要借 {@link CompoundContainer#contains(Container)} 反查「菜单容器是否包含这个半区方块实体」。
     */
    private static boolean isOpenMenuContainer(Container openMenuContainer, Container scannedContainer,
                                               Container blockEntityContainer) {
        if (openMenuContainer == null) {
            return false;
        }
        if (openMenuContainer == scannedContainer || openMenuContainer.equals(scannedContainer)) {
            return true;
        }
        return openMenuContainer instanceof CompoundContainer compound
                && blockEntityContainer != null
                && compound.contains(blockEntityContainer);
    }

    /**
     * 把背包主槽位（9..35）里的物品分两阶段写入目标容器，返回实际移动件数。
     * 独立成方法是为了让调用方用 try/finally 兜住「部分写入后抛异常」的标脏。
     */
    private static int transferInventoryIntoContainers(Inventory playerInv, Set<Integer> lockedSlots,
                                                       List<ScannedContainer> containers, OneKeyConfig config,
                                                       Set<Container> modifiedContainers) {
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
            if (!config.quickStack.itemBlacklist.isEmpty() && config.quickStack.itemBlacklist.contains(itemIdStr)) {
                continue;
            }
            if (!config.quickStack.itemWhitelist.isEmpty() && !config.quickStack.itemWhitelist.contains(itemIdStr)) {
                continue;
            }

            // =========================================================================
            // Pass 1: 同类归位（优先准星容器，其次按距离遍历所有已有该同类物品的容器）
            // 防止近处的空箱子截胡远处分类箱已有的同类物品
            // =========================================================================
            for (ScannedContainer sc : containers) {
                if (playerStack.isEmpty()) {
                    break;
                }
                Container target = sc.container();
                if (!containsSameItem(target, playerStack)) {
                    continue; // 容器中没有同类物品，Pass 1 严禁触碰，防污染
                }

                // 1.1 补齐未满的同类物品堆叠
                totalMoved += fillExistingStacks(target, playerStack, modifiedContainers);

                // 1.2 同类溢出占用本分类容器自身的空槽
                if (!playerStack.isEmpty()) {
                    totalMoved += fillEmptySlots(target, playerStack, modifiedContainers);
                }
            }

            // =========================================================================
            // Pass 2: 全新物品开荒倾倒（仅向 allowDumpNewItem == true 的准星锁定容器倾倒空槽）
            // =========================================================================
            if (!playerStack.isEmpty()) {
                for (ScannedContainer sc : containers) {
                    if (playerStack.isEmpty()) {
                        break;
                    }
                    if (!sc.allowDumpNewItem()) {
                        continue; // 周边未被准星明确锁定的容器严禁占用空槽，防污染
                    }

                    totalMoved += fillEmptySlots(sc.container(), playerStack, modifiedContainers);
                }
            }

            if (playerStack.isEmpty()) {
                playerInv.setItem(slotIdx, ItemStack.EMPTY);
            }
        }

        return totalMoved;
    }

    private static Container getOpenMenuContainer(AbstractContainerMenu menu) {
        if (menu == null || menu instanceof InventoryMenu) {
            return null;
        }
        for (Slot slot : menu.slots) {
            if (slot.container != null && !(slot.container instanceof Inventory)) {
                return slot.container;
            }
        }
        return null;
    }

    private static Set<BlockPos> resolveTargetPositions(ServerLevel level, BlockPos targetPos) {
        if (targetPos == null) {
            return Set.of();
        }
        Set<BlockPos> candidates = new HashSet<>();
        candidates.add(targetPos);

        // 告示牌穿透：若准星指向贴在箱子正面的告示牌，自动解析其附着的背后容器
        BlockState state = level.getBlockState(targetPos);
        Block block = state.getBlock();
        if (block instanceof WallSignBlock && state.hasProperty(WallSignBlock.FACING)) {
            Direction facing = state.getValue(WallSignBlock.FACING);
            candidates.add(targetPos.relative(facing.getOpposite()));
        } else if (block instanceof WallHangingSignBlock && state.hasProperty(WallHangingSignBlock.FACING)) {
            Direction facing = state.getValue(WallHangingSignBlock.FACING);
            candidates.add(targetPos.relative(facing.getOpposite()));
        }

        return candidates;
    }

    private static boolean containsSameItem(Container target, ItemStack playerStack) {
        for (int c = 0; c < target.getContainerSize(); c++) {
            ItemStack cStack = target.getItem(c);
            if (!cStack.isEmpty() && ItemStack.isSameItemSameComponents(cStack, playerStack)) {
                return true;
            }
        }
        return false;
    }

    private static int fillExistingStacks(Container target, ItemStack playerStack, Set<Container> modifiedContainers) {
        int moved = 0;
        for (int c = 0; c < target.getContainerSize(); c++) {
            if (playerStack.isEmpty()) {
                break;
            }
            ItemStack cStack = target.getItem(c);
            if (!cStack.isEmpty() && ItemStack.isSameItemSameComponents(cStack, playerStack) && target.canPlaceItem(c, playerStack)) {
                int maxStack = Math.min(target.getMaxStackSize(cStack), cStack.getMaxStackSize());
                int available = maxStack - cStack.getCount();
                if (available > 0) {
                    int toMove = Math.min(playerStack.getCount(), available);
                    if (toMove > 0) {
                        cStack.grow(toMove);
                        playerStack.shrink(toMove);
                        moved += toMove;
                        modifiedContainers.add(target);
                    }
                }
            }
        }
        return moved;
    }

    private static int fillEmptySlots(Container target, ItemStack playerStack, Set<Container> modifiedContainers) {
        int moved = 0;
        for (int c = 0; c < target.getContainerSize(); c++) {
            if (playerStack.isEmpty()) {
                break;
            }
            ItemStack cStack = target.getItem(c);
            if (cStack.isEmpty() && target.canPlaceItem(c, playerStack)) {
                int maxStack = Math.min(target.getMaxStackSize(playerStack), playerStack.getMaxStackSize());
                int toMove = Math.min(playerStack.getCount(), maxStack);
                if (toMove <= 0) {
                    continue;
                }
                target.setItem(c, playerStack.copyWithCount(toMove));
                playerStack.shrink(toMove);
                moved += toMove;
                modifiedContainers.add(target);
            }
        }
        return moved;
    }

    private static List<ScannedContainer> findNearbyContainers(
            ServerPlayer player,
            int radius,
            OneKeyConfig config,
            BlockPos clientTargetPos,
            Container openMenuContainer,
            boolean stashMode
    ) {
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
        Set<BlockPos> targetCandidates = resolveTargetPositions(level, clientTargetPos);
        // 「智能分类防污染」关闭时，退回旧版行为：B 键可以把新物品倒进任意扫描到的容器
        // 「智能分类防污染」或「准星瞄准优先」任一关闭，都退回旧版范围存入：
        // 只关准星时若仍强制 isTargeted，B 键将永远找不到目标（等于废掉该键）。
        boolean allowDumpAnywhere = stashMode
                && (!config.quickStack.smartMatching || !config.quickStack.crosshairPriority);

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

                    // 严禁接触尚未开封的战利品箱（地牢、遗迹等），防止破坏战利品生成机制或通过隔墙探矿作弊
                    if (be instanceof RandomizableContainerBlockEntity randomizable && randomizable.getLootTable() != null) {
                        continue;
                    }

                    // 玩家无权开启的已加锁容器（如 LockCode 密码锁），严禁存入
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
                        Set<BlockPos> chestPositions = new HashSet<>();
                        chestPositions.add(pos.immutable());

                        // 避免重复扫描大箱子的两个半区，并将两个物理坐标一并归一化
                        ChestType chestType = state.hasProperty(ChestBlock.TYPE) ? state.getValue(ChestBlock.TYPE) : ChestType.SINGLE;
                        if (chestType == ChestType.LEFT || chestType == ChestType.RIGHT) {
                            Direction connected = ChestBlock.getConnectedDirection(state);
                            BlockPos otherHalf = pos.relative(connected).immutable();
                            visited.add(otherHalf);

                            // 对大箱子另一半也进行安全性与锁状态复核
                            BlockEntity otherBe = level.getBlockEntity(otherHalf);
                            if (otherBe instanceof RandomizableContainerBlockEntity rand && rand.getLootTable() != null) {
                                continue;
                            }
                            if (otherBe instanceof BaseContainerBlockEntity base && !base.canOpen(player)) {
                                continue;
                            }

                            chestPositions.add(otherHalf);
                            distSq = Math.min(distSq, otherHalf.distToCenterSqr(player.position()));
                        }
                        visited.add(pos);

                        Container chestContainer = ChestBlock.getContainer(chestBlock, state, level, pos, false);
                        if (chestContainer != null && chestContainer.stillValid(player)) {
                            boolean isTargeted = isOpenMenuContainer(openMenuContainer, chestContainer,
                                    be instanceof Container beContainer ? beContainer : null)
                                    || targetCandidates.stream().anyMatch(chestPositions::contains);
                            boolean allowDumpNew = allowDumpAnywhere || (stashMode && isTargeted);
                            scanned.add(new ScannedContainer(chestContainer, chestPositions, distSq, isTargeted, allowDumpNew));
                        }
                    } else if (be instanceof BarrelBlockEntity barrel) {
                        visited.add(pos);
                        if (barrel.stillValid(player) && barrel.canOpen(player)) {
                            boolean isTargeted = isOpenMenuContainer(openMenuContainer, barrel, barrel)
                                    || targetCandidates.contains(pos);
                            boolean allowDumpNew = allowDumpAnywhere || (stashMode && isTargeted);
                            scanned.add(new ScannedContainer(barrel, Set.of(pos.immutable()), distSq, isTargeted, allowDumpNew));
                        }
                    } else if (be instanceof ShulkerBoxBlockEntity shulker) {
                        visited.add(pos);
                        if (shulker.stillValid(player) && shulker.canOpen(player)) {
                            boolean isTargeted = isOpenMenuContainer(openMenuContainer, shulker, shulker)
                                    || targetCandidates.contains(pos);
                            boolean allowDumpNew = allowDumpAnywhere || (stashMode && isTargeted);
                            scanned.add(new ScannedContainer(shulker, Set.of(pos.immutable()), distSq, isTargeted, allowDumpNew));
                        }
                    }
                }
            }
        }

        // 排序规则：准星锁定的容器绝对置顶，其余按与玩家的几何距离由近及远排序
        scanned.sort(
                Comparator.comparing(ScannedContainer::isTargeted).reversed()
                        .thenComparingDouble(ScannedContainer::distSq)
        );

        return scanned;
    }
}
