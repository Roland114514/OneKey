package me.easan.onekey.fabric.client.mixin;

import java.util.Set;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 访问 AbstractContainerScreen 中受保护的 hoveredSlot、slotClicked 与 quickcraft 状态。
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Accessor("hoveredSlot")
    Slot onekey$getHoveredSlot();

    @Invoker("slotClicked")
    void onekey$slotClicked(Slot slot, int slotId, int mouseButton, ContainerInput clickType);

    @Invoker("getHoveredSlot")
    Slot onekey$findSlotAt(double mouseX, double mouseY);

    @Accessor("isQuickCrafting")
    void onekey$setIsQuickCrafting(boolean value);

    @Accessor("isQuickCrafting")
    boolean onekey$isQuickCrafting();

    @Accessor("quickCraftSlots")
    Set<Slot> onekey$getQuickCraftSlots();

    @Accessor("skipNextRelease")
    void onekey$setSkipNextRelease(boolean value);

    @Accessor("leftPos")
    int onekey$getLeftPos();

    @Accessor("topPos")
    int onekey$getTopPos();

    @Accessor("imageWidth")
    int onekey$getImageWidth();

    @Accessor("imageHeight")
    int onekey$getImageHeight();
}
