package me.easan.onekey.fabric.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.easan.onekey.common.config.OneKeyConfig;
import me.easan.onekey.common.net.SortRequest;
import me.easan.onekey.common.net.SortResult;
import me.easan.onekey.common.sort.Sorter;
import me.easan.onekey.common.sort.StackView;
import me.easan.onekey.fabric.OneKeyFabric;
import me.easan.onekey.fabric.net.SortResultPayload;
import me.easan.onekey.fabric.storage.LockedSlotsStorage;
import me.easan.onekey.fabric.util.ItemConversion;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 服务端一键整理处理器：负责请求校验、槽位筛选、执行排序和回包。
 */
public final class ServerSortingHandler {

    private static final Map<UUID, Long> LAST_SORT_TIME = new ConcurrentHashMap<>();

    private ServerSortingHandler() {
    }

    public static void handle(SortRequest request, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        if (player == null) {
            return;
        }

        OneKeyConfig config = OneKeyFabric.config();
        if (!config.sort.enabled) {
            return;
        }

        // 校验冷却时间（默认 250ms，防止连点导致网络错位）
        long now = System.currentTimeMillis();
        long lastTime = LAST_SORT_TIME.getOrDefault(player.getUUID(), 0L);
        if (now - lastTime < config.sort.cooldownMs) {
            return;
        }
        LAST_SORT_TIME.put(player.getUUID(), now);

        // 校验容器同步 ID
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menu.containerId != request.syncId()) {
            return;
        }

        // 筛选待排序槽位
        List<Slot> slotsToSort = resolveSlotsToSort(menu, player, request.mode());
        if (slotsToSort.isEmpty()) {
            context.responseSender().sendPacket(new SortResultPayload(new SortResult(false, 0)));
            return;
        }

        // 获取锁定槽位（针对当前容器类型与世界独立存档）
        String containerKey = getContainerKey(menu, request.mode());
        Set<Integer> lockedIndices = LockedSlotsStorage.getLockedSlots(player.getUUID(), containerKey);

        // 转换为纯逻辑 StackView
        List<StackView> views = new ArrayList<>(slotsToSort.size());
        for (Slot slot : slotsToSort) {
            views.add(ItemConversion.toStackView(slot.getItem()));
        }

        // 计算排序置换（Sorter 保证为空格置后、锁定槽不动、且恒为双射）
        int[] permutation = Sorter.permutation(views, lockedIndices, config.sort.toRule());

        // 计算发生移动的槽位数量
        int moved = 0;
        for (int i = 0; i < permutation.length; i++) {
            if (permutation[i] != i && !views.get(permutation[i]).isEmpty()) {
                moved++;
            }
        }

        if (moved > 0) {
            // 快照原始 ItemStack，防止直接写回覆盖造成物品丢失或复制
            List<ItemStack> originals = new ArrayList<>(slotsToSort.size());
            for (Slot slot : slotsToSort) {
                originals.add(slot.getItem().copy());
            }

            // 安全写回置换结果
            for (int target = 0; target < permutation.length; target++) {
                int source = permutation[target];
                Slot slot = slotsToSort.get(target);
                slot.set(originals.get(source));
                slot.setChanged();
            }

            // 同步槽位变更到客户端
            menu.broadcastChanges();
        }

        // 回包给客户端
        context.responseSender().sendPacket(new SortResultPayload(new SortResult(true, moved)));
    }

    private static List<Slot> resolveSlotsToSort(AbstractContainerMenu menu, ServerPlayer player, int mode) {
        List<Slot> result = new ArrayList<>();
        boolean isPlayerOnly = (mode == SortRequest.MODE_PLAYER) || (menu instanceof InventoryMenu);

        if (isPlayerOnly) {
            // 整理玩家背包：底部 9 格快捷栏（0..8）严格不参与整理，只整理主背包 27 格（9..35）
            for (Slot slot : menu.slots) {
                if (slot.container == player.getInventory()) {
                    int slotIndex = slot.getContainerSlot();
                    if (slotIndex >= 9 && slotIndex < 36) {
                        result.add(slot);
                    }
                }
            }
        } else {
            // 整理外部容器槽位（例如箱子、木桶、潜影盒、发射器、漏斗等）
            for (Slot slot : menu.slots) {
                if (slot.container != player.getInventory() && isSortableExternalSlot(slot)) {
                    result.add(slot);
                }
            }
            // 如果该界面没有可排序的外部容器槽位，降级为整理玩家主背包
            if (result.isEmpty()) {
                for (Slot slot : menu.slots) {
                    if (slot.container == player.getInventory()) {
                        int slotIndex = slot.getContainerSlot();
                        if (slotIndex >= 9 && slotIndex < 36) {
                            result.add(slot);
                        }
                    }
                }
            }
        }
        return result;
    }

    private static boolean isSortableExternalSlot(Slot slot) {
        if (slot.isFake() || slot instanceof ResultSlot) {
            return false;
        }
        if (slot.container instanceof CraftingContainer) {
            return false;
        }
        return true;
    }

    private static String getContainerKey(AbstractContainerMenu menu, int mode) {
        if (mode == SortRequest.MODE_PLAYER || menu instanceof InventoryMenu) {
            return "player_inventory";
        }
        Identifier menuType = BuiltInRegistries.MENU.getKey(menu.getType());
        return menuType != null ? menuType.toString() : "generic_container";
    }
}
