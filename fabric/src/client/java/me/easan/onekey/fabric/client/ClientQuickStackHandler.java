package me.easan.onekey.fabric.client;

import me.easan.onekey.common.net.QuickStackResult;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * 客户端快速堆叠与存入结果接收器：播放音效与提示消息。
 */
public final class ClientQuickStackHandler {

    private ClientQuickStackHandler() {
    }

    public static void handle(QuickStackResult result, ClientPlayNetworking.Context context) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) {
            return;
        }

        if (result.moved() > 0) {
            player.sendOverlayMessage(
                    Component.translatable("onekey.quick_stack.success", result.moved()));
            client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BUNDLE_INSERT, 1.2F));
        } else {
            player.sendOverlayMessage(
                    Component.translatable("onekey.quick_stack.none"));
        }
    }
}
