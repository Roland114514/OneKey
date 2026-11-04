package me.easan.onekey.neoforge.client;

import me.easan.onekey.common.net.QuickStackResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端快速堆叠回包处理器（NeoForge）。
 */
public final class ClientQuickStackHandler {

    private ClientQuickStackHandler() {
    }

    public static void handle(QuickStackResult result, IPayloadContext context) {
        Minecraft client = Minecraft.getInstance();
        if (!(context.player() instanceof LocalPlayer player)) {
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
