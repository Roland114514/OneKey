package me.easan.onekey.neoforge.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 访问 AbstractContainerScreen 中受保护的 hoveredSlot、slotClicked 与 getHoveredSlot 方法。
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Accessor("hoveredSlot")
    Slot onekey$getHoveredSlot();

    @Invoker("slotClicked")
    void onekey$slotClicked(Slot slot, int slotId, int mouseButton, ContainerInput clickType);

    @Invoker("getHoveredSlot")
    Slot onekey$findSlotAt(double mouseX, double mouseY);
}
