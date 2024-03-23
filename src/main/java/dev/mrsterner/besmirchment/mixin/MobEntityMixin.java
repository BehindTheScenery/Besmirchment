package dev.mrsterner.besmirchment.mixin;

import dev.mrsterner.besmirchment.common.entity.interfaces.TameableDemon;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEntity.class)
public abstract class MobEntityMixin {
    @Shadow @Nullable public abstract LivingEntity getTarget();

    @Inject(method = "handleStatus", at = @At("HEAD"))
    public void handleStatus(byte status, CallbackInfo ci) {
        if (this instanceof TameableDemon) {
            if (status == 7) {
                ((TameableDemon) this).showEmoteParticle(true);
            } else if (status == 6) {
                ((TameableDemon) this).showEmoteParticle(false);
            }
        }
    }

    @Inject(method = "canTarget", at = @At("HEAD"), cancellable = true)
    public void canTarget(EntityType<?> type, CallbackInfoReturnable<Boolean> cir) {
        if (this instanceof TameableDemon tameableDemon && tameableDemon.isOwner(getTarget()) && getTarget() != null){
            cir.setReturnValue(false);
        }
    }

}
