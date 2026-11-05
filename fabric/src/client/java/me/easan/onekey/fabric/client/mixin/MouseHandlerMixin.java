package me.easan.onekey.fabric.client.mixin;

import me.easan.onekey.fabric.client.ClientExcavatorHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截游戏内鼠标滚轮滚动：当按住连锁热键（~）时，滚动滚轮切换连锁挖掘模式并取消原版快捷栏槽位切换。
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (ClientExcavatorHandler.onMouseScroll(Minecraft.getInstance(), vertical)) {
            ci.cancel();
        }
    }
}
