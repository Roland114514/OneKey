package me.easan.onekey.fabric.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import me.easan.onekey.fabric.client.OneKeyFabricClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

/**
 * 位于容器界面右上角的原版风格一键整理快捷按钮工厂。
 * 支持点击直接整理当前容器（背包界面整理背包，容器界面整理容器本体），
 * 按住 Shift 点击可快速整理玩家背包。
 */
public final class SortButton {

    public static final int BUTTON_SIZE = 12;

    private SortButton() {
    }

    public static Button create(int x, int y, AbstractContainerScreen<?> parentScreen) {
        return Button.builder(Component.literal("↕"), button -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) {
                return;
            }
            boolean shift = InputConstants.isKeyDown(InputConstants.KEY_LSHIFT)
                    || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
            OneKeyFabricClient.triggerButtonSort(client, parentScreen, shift);
        }).bounds(x, y, BUTTON_SIZE, BUTTON_SIZE)
          .tooltip(Tooltip.create(Component.translatable("onekey.gui.sort_button.tooltip")))
          .build();
    }
}
