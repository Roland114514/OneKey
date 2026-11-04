package me.easan.onekey.neoforge.client;

import me.easan.onekey.common.net.SortResult;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端一键整理回包处理器（NeoForge）。
 */
public final class ClientSortingHandler {

    private ClientSortingHandler() {
    }

    public static void handle(SortResult result, IPayloadContext context) {
        if (!(context.player() instanceof LocalPlayer player)) {
            return;
        }

        if (result.success()) {
            if (result.moved() > 0) {
                player.sendOverlayMessage(Component.translatable("onekey.sort.success", result.moved()));
            } else {
                player.sendOverlayMessage(Component.translatable("onekey.sort.already_sorted"));
            }
        } else {
            // 服务端拒绝（如服主关闭了 sort.enabled）：必须给出可见反馈，否则玩家只看到"按了没反应"
            player.sendOverlayMessage(Component.translatable("onekey.sort.cannot_sort"));
        }
    }
}
