package me.easan.onekey.neoforge.mixin;

import me.easan.onekey.neoforge.server.ServerExcavatorHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 拦截 ServerLevel.addFreshEntity 实现连锁破坏过程中掉落物与经验球的内存级聚合。
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void onAddFreshEntity(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (ServerExcavatorHandler.isCollectingDrops()) {
            if (entity instanceof ItemEntity itemEntity) {
                ServerExcavatorHandler.captureDrop(itemEntity.getItem().copy());
                cir.setReturnValue(true);
            } else if (entity instanceof ExperienceOrb xpOrb) {
                ServerExcavatorHandler.captureXp(xpOrb.getValue());
                cir.setReturnValue(true);
            }
        }
    }
}
