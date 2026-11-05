package me.easan.onekey.neoforge.client.mixin;

import java.util.ArrayList;
import java.util.List;
import me.easan.onekey.neoforge.client.ClientLockedSlots;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 监听槽位渲染与浮窗提示：
 * 1. 为锁定的槽位绘制高亮琥珀色边框与半透明底色（NeoForge）；
 * 2. 悬停在锁定槽位上时，在 Tooltip 底部追加醒目的锁定状态提示。
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin<T extends AbstractContainerMenu> {

    @Shadow
    @Final
    protected T menu;

    @Shadow
    protected Slot hoveredSlot;

    @Inject(method = "extractSlot", at = @At("TAIL"))
    private void onekey$renderLockedSlotOverlay(GuiGraphicsExtractor extractor, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (slot.isActive() && ClientLockedSlots.isSlotLocked(this.menu, slot)) {
            // 半透明琥珀金底色
            extractor.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x33FFA000);
            // 1 像素高亮金黄边框
            extractor.outline(slot.x, slot.y, 16, 16, 0xFFFFB300);
        }
    }

    @Inject(method = "getTooltipFromContainerItem", at = @At("RETURN"), cancellable = true)
    private void onekey$appendLockedItemTooltip(ItemStack itemStack, CallbackInfoReturnable<List<Component>> cir) {
        if (this.hoveredSlot != null && this.hoveredSlot.isActive() && ClientLockedSlots.isSlotLocked(this.menu, this.hoveredSlot)) {
            List<Component> list = new ArrayList<>(cir.getReturnValue());
            list.add(Component.translatable("onekey.gui.slot_locked_tooltip"));
            cir.setReturnValue(list);
        }
    }

    @Inject(method = "extractTooltip", at = @At("RETURN"))
    private void onekey$renderEmptyLockedSlotTooltip(GuiGraphicsExtractor extractor, int mouseX, int mouseY, CallbackInfo ci) {
        if (this.menu.getCarried().isEmpty()
                && this.hoveredSlot != null
                && !this.hoveredSlot.hasItem()
                && this.hoveredSlot.isActive()
                && ClientLockedSlots.isSlotLocked(this.menu, this.hoveredSlot)) {
            extractor.setTooltipForNextFrame(Component.translatable("onekey.gui.slot_locked_empty_tooltip"), mouseX, mouseY);
        }
    }
}
