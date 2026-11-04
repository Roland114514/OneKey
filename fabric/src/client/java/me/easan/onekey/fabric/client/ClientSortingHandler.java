package me.easan.onekey.fabric.client;

import me.easan.onekey.common.net.SortResult;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * 客户端一键整理回包处理器：接收服务端的 SortResult 并在 Action Bar 呈现反馈。
 */
public final class ClientSortingHandler {

    private ClientSortingHandler() {
    }

    public static void handle(SortResult result, ClientPlayNetworking.Context context) {
        LocalPlayer player = context.player();
        if (player == null) {
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
