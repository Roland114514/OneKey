package me.easan.onekey.neoforge.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import me.easan.onekey.neoforge.client.OneKeyNeoForgeClient;
import me.easan.onekey.neoforge.client.mixin.AbstractContainerScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;

/**
 * 位于背包与容器界面物品栏右上方的原版风格一键整理快捷按钮（UI 标识为 "R"）。
 * 具备动态坐标同步与创造模式安全防护：
 * 1. 位于背包物品栏正上方（inventoryLabelY - 2），对齐第 9 列槽位；
 * 2. 动态自适应配方书展开/折叠导致的 leftPos 偏移；
 * 3. 创造模式下仅在「生存模式物品栏」页签显示并生效；
 * 4. 支持普通点击与 Shift 点击（整理玩家背包）。
 */
public class SortButton extends Button {

    public static final int BUTTON_SIZE = 12;

    private final AbstractContainerScreen<?> parentScreen;

    public SortButton(int x, int y, AbstractContainerScreen<?> parentScreen, OnPress onPress) {
        super(x, y, BUTTON_SIZE, BUTTON_SIZE, Component.literal("R"), onPress, DEFAULT_NARRATION);
        this.parentScreen = parentScreen;
        setTooltip(Tooltip.create(Component.translatable("onekey.gui.sort_button.tooltip")));
        syncPositionAndVisibility();
    }

    public static SortButton create(AbstractContainerScreen<?> parentScreen) {
        if (!(parentScreen instanceof AbstractContainerScreenAccessor accessor)) {
            return null;
        }
        int labelY = accessor.onekey$getInventoryLabelY();
        if (labelY <= 0) {
            labelY = accessor.onekey$getImageHeight() - 94;
        }
        int x = accessor.onekey$getLeftPos() + (parentScreen instanceof CreativeModeInventoryScreen ? 158 : accessor.onekey$getImageWidth() - 18);
        int y = accessor.onekey$getTopPos() + labelY - 2;
        return new SortButton(x, y, parentScreen, button -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) {
                return;
            }
            boolean shift = InputConstants.isKeyDown(InputConstants.KEY_LSHIFT)
                    || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
            OneKeyNeoForgeClient.triggerButtonSort(client, parentScreen, shift);
        });
    }

    public void syncPositionAndVisibility() {
        if (this.parentScreen instanceof AbstractContainerScreenAccessor accessor) {
            int labelY = accessor.onekey$getInventoryLabelY();
            if (labelY <= 0) {
                labelY = accessor.onekey$getImageHeight() - 94;
            }
            int targetX = accessor.onekey$getLeftPos() + (this.parentScreen instanceof CreativeModeInventoryScreen ? 158 : accessor.onekey$getImageWidth() - 18);
            int targetY = accessor.onekey$getTopPos() + labelY - 2;
            this.setX(targetX);
            this.setY(targetY);
        }
        if (this.parentScreen instanceof CreativeModeInventoryScreen creative) {
            boolean open = creative.isInventoryOpen();
            this.visible = open;
            this.active = open;
        }
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        syncPositionAndVisibility();
        return super.isMouseOver(mouseX, mouseY);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        syncPositionAndVisibility();
        this.extractDefaultSprite(extractor);
        this.extractDefaultLabel(extractor.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
    }
}
