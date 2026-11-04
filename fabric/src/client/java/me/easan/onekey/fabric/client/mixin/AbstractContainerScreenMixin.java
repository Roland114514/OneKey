package me.easan.onekey.fabric.client.mixin;

import me.easan.onekey.fabric.client.ClientLockedSlots;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 监听槽位渲染，为锁定的槽位绘制高亮琥珀色边框与半透明底色。
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin<T extends AbstractContainerMenu> {

    @Shadow
    @Final
    protected T menu;

    @Inject(method = "extractSlot", at = @At("TAIL"))
    private void onekey$renderLockedSlotOverlay(GuiGraphicsExtractor extractor, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (slot.isActive() && ClientLockedSlots.isSlotLocked(this.menu, slot)) {
            // 半透明琥珀金底色
            extractor.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x33FFA000);
            // 1 像素高亮金黄边框
            extractor.outline(slot.x, slot.y, 16, 16, 0xFFFFB300);
        }
    }
}
