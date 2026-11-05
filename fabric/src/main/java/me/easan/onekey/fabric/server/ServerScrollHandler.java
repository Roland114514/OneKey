package me.easan.onekey.fabric.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.easan.onekey.common.inventory.InventorySlots;
import me.easan.onekey.fabric.util.PlayerSlotFilter;
import me.easan.onekey.fabric.OneKeyFabric;
import me.easan.onekey.fabric.net.ScrollItemTransferPayload;
import me.easan.onekey.fabric.storage.LockedSlotsStorage;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 服务端滚轮单件微调转移处理器：
 * 保证单件转移 100% 遵循物品守恒、槽位放入约束、快捷栏与锁定隔离。
 */
public final class ServerScrollHandler {

    /** 滚轮微调的服务端最小间隔（毫秒）：低于此间隔的包直接丢弃（客户端 40ms 节流可被改包绕过）。 */
    private static final long SCROLL_COOLDOWN_MS = 40L;

    /** 每个玩家的上次滚轮处理时间。 */
    private static final Map<UUID, Long> LAST_SCROLL_TIME = new ConcurrentHashMap<>();

    private ServerScrollHandler() {
    }

    public static void onPlayerDisconnect(UUID playerUuid) {
        if (playerUuid != null) {
            LAST_SCROLL_TIME.remove(playerUuid);
        }
    }

    public static void handle(ScrollItemTransferPayload payload, ServerPlayNetworking.Context context) {
        try {
            process(payload, context);
        } catch (Throwable throwable) {
            OneKeyFabric.LOGGER.error("[OneKey] 滚轮微调请求处理失败，已丢包", throwable);
        }
    }

    private static void process(ScrollItemTransferPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        if (player == null || player.isSpectator() || player.isDeadOrDying()) {
            return;
        }

        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menu.containerId != payload.syncId()) {
            return;
        }

        if (!menu.stillValid(player)) {
            return;
        }

        // 服务端限流：每个滚轮包都会触发一次 menu.broadcastChanges()（等价回发整份容器内容），
        // 若只靠客户端 40ms 节流，恶意客户端可用滚轮包把服务器刷爆。
        long now = System.currentTimeMillis();
        Long lastScroll = LAST_SCROLL_TIME.get(player.getUUID());
        if (lastScroll != null && now - lastScroll < SCROLL_COOLDOWN_MS) {
            return;
        }
        LAST_SCROLL_TIME.put(player.getUUID(), now);

        // 仅在已打开且属于纯存储白名单的容器中生效，避免侵入工作台、铁砧、熔炉等特种界面
        if (!ServerSortingHandler.isMenuSortable(ServerSortingHandler.menuTypeOrNull(menu))) {
            return;
        }

        // 光标悬浮持有物品时禁止滚动操作，避免数据错位
        if (!menu.getCarried().isEmpty()) {
            return;
        }

        if (payload.slotIndex() < 0 || payload.slotIndex() >= menu.slots.size()) {
            return;
        }

        Slot sourceSlot = menu.getSlot(payload.slotIndex());
        if (sourceSlot == null || sourceSlot.isFake() || sourceSlot instanceof ResultSlot) {
            return;
        }
        if (sourceSlot.container instanceof CraftingContainer) {
            return;
        }

        // 源槽位若为空，不执行任何盲抓或移动，防止意外向空槽移入对侧未知物品
        ItemStack sourceStack = sourceSlot.getItem();
        if (sourceStack.isEmpty()) {
            return;
        }

        String extContainerKey = ServerSortingHandler.menuTypeKey(menu);

        // 检查源槽位是否锁定（快捷栏 0..8 永久保护，锁定的槽位禁止移出）
        boolean sourceIsPlayer = sourceSlot.container == player.getInventory();
        if (sourceIsPlayer) {
            int slotIdx = sourceSlot.getContainerSlot();
            if (!PlayerSlotFilter.isPlainPlayerInventorySlot(player, sourceSlot,
                    OneKeyFabric.config().gestures.includeHotbar)) {
                return; // 盔甲 / 副手永不参与；快捷栏由「手势影响快捷栏」开关决定
            }
            if (LockedSlotsStorage.isSlotLocked(player.getUUID(), "player_inventory", slotIdx)) {
                return;
            }
        } else {
            if (LockedSlotsStorage.isSlotLocked(player.getUUID(), extContainerKey, sourceSlot.index)) {
                return;
            }
        }

        // 确定对侧可交互槽位列表
        List<Slot> oppositeSlots = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (slot.isFake() || slot instanceof ResultSlot || slot.container instanceof CraftingContainer) {
                continue;
            }
            if (sourceIsPlayer) {
                // 当前源是玩家背包 -> 对侧是外部容器槽位（排除外部容器中被锁定的槽位）
                if (slot.container != player.getInventory()) {
                    if (!LockedSlotsStorage.isSlotLocked(player.getUUID(), extContainerKey, slot.index)) {
                        oppositeSlots.add(slot);
                    }
                }
            } else {
                // 当前源是外部容器 -> 对侧是玩家主背包（9..35，严格隔离快捷栏 0..8 与锁定槽位）
                if (slot.container == player.getInventory()) {
                    int slotIdx = slot.getContainerSlot();
                    if (PlayerSlotFilter.isPlainPlayerInventorySlot(player, slot,
                            OneKeyFabric.config().gestures.includeHotbar)
                            && !LockedSlotsStorage.isSlotLocked(player.getUUID(), "player_inventory", slotIdx)) {
                        oppositeSlots.add(slot);
                    }
                }
            }
        }

        if (oppositeSlots.isEmpty()) {
            return;
        }

        boolean changed = false;

        if (!payload.scrollUp()) {
            // 向下滚：从本槽位移出 1 件物品送往对侧
            if (!sourceSlot.mayPickup(player)) {
                return;
            }

            // 优先尝试合并进对侧未满的同类槽位
            for (Slot oppSlot : oppositeSlots) {
                if (!oppSlot.mayPlace(sourceStack)) {
                    continue;
                }
                ItemStack oppStack = oppSlot.getItem();
                if (!oppStack.isEmpty() && ItemStack.isSameItemSameComponents(oppStack, sourceStack)) {
                    int max = Math.min(oppSlot.getMaxStackSize(oppStack), oppStack.getMaxStackSize());
                    if (oppStack.getCount() < max) {
                        oppStack.grow(1);
                        sourceStack.shrink(1);
                        oppSlot.setChanged();
                        changed = true;
                        break;
                    }
                }
            }

            // 其次尝试存入对侧空槽
            if (!changed) {
                for (Slot oppSlot : oppositeSlots) {
                    if (oppSlot.getItem().isEmpty() && oppSlot.mayPlace(sourceStack)) {
                        oppSlot.set(sourceStack.copyWithCount(1));
                        sourceStack.shrink(1);
                        oppSlot.setChanged();
                        changed = true;
                        break;
                    }
                }
            }

            if (changed) {
                if (sourceStack.isEmpty()) {
                    sourceSlot.set(ItemStack.EMPTY);
                } else {
                    sourceSlot.setChanged();
                }
                menu.broadcastChanges();
            }
        } else {
            // 向上滚：从对侧抓取 1 件同类物品补充到本槽位
            int max = Math.min(sourceSlot.getMaxStackSize(sourceStack), sourceStack.getMaxStackSize());
            if (sourceStack.getCount() < max) {
                for (Slot oppSlot : oppositeSlots) {
                    ItemStack oppStack = oppSlot.getItem();
                    if (!oppStack.isEmpty() && ItemStack.isSameItemSameComponents(oppStack, sourceStack)) {
                        if (!oppSlot.mayPickup(player) || !sourceSlot.mayPlace(oppStack)) {
                            continue;
                        }
                        sourceStack.grow(1);
                        oppStack.shrink(1);
                        if (oppStack.isEmpty()) {
                            oppSlot.set(ItemStack.EMPTY);
                        } else {
                            oppSlot.setChanged();
                        }
                        sourceSlot.setChanged();
                        changed = true;
                        break;
                    }
                }
            }

            if (changed) {
                menu.broadcastChanges();
            }
        }
    }
}
