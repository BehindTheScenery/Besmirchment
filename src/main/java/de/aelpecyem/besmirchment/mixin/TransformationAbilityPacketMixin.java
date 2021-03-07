package de.aelpecyem.besmirchment.mixin;

import de.aelpecyem.besmirchment.common.registry.BSMEntityTypes;
import de.aelpecyem.besmirchment.common.registry.BSMTransformations;
import moriyashiine.bewitchment.api.interfaces.entity.TransformationAccessor;
import moriyashiine.bewitchment.client.network.packet.SpawnSmokeParticlesPacket;
import moriyashiine.bewitchment.common.network.packet.TransformationAbilityPacket;
import moriyashiine.bewitchment.common.registry.BWSoundEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import virtuoel.pehkui.api.ScaleData;

@Mixin(TransformationAbilityPacket.class)
public class TransformationAbilityPacketMixin {
    private static final float WEREPYRE_WIDTH = BSMEntityTypes.WEREPYRE.getWidth() / EntityType.PLAYER.getWidth();
    private static final float WEREPYRE_HEIGHT = BSMEntityTypes.WEREPYRE.getHeight() / EntityType.PLAYER.getHeight();

    @Inject(method = "canUseAbility", at = @At("HEAD"), cancellable = true)
    private static void canUseAbility(PlayerEntity player, CallbackInfoReturnable<Boolean> cir){
        if (BSMTransformations.isWerepyre(player, true)){
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "useAbility", at = @At(value = "INVOKE_ASSIGN", target = "virtuoel/pehkui/api/ScaleType.getScaleData(Lnet/minecraft/entity/Entity;)Lvirtuoel/pehkui/api/ScaleData;", ordinal = 1), locals = LocalCapture.CAPTURE_FAILHARD, cancellable = true)
    private static void useAbility(PlayerEntity player, boolean forced, CallbackInfo ci, World world, boolean isInAlternateForm, ScaleData width, ScaleData height){
        if (((TransformationAccessor)player).getTransformation() == BSMTransformations.WEREPYRE && (forced || BSMTransformations.hasWerepyrePledge(player))){
            PlayerLookup.tracking(player).forEach((foundPlayer) -> {
                SpawnSmokeParticlesPacket.send(foundPlayer, player);
            });
            SpawnSmokeParticlesPacket.send(player, player);
            world.playSound(null, player.getBlockPos(), BWSoundEvents.ENTITY_GENERIC_TRANSFORM, player.getSoundCategory(), 1.0F, 1.0F);
            ((TransformationAccessor)player).setAlternateForm(!isInAlternateForm);
            if (isInAlternateForm) {
                width.setScale(width.getScale() / WEREPYRE_WIDTH);
                height.setScale(height.getScale() / WEREPYRE_HEIGHT);
                if (player.hasStatusEffect(StatusEffects.NIGHT_VISION) && player.getStatusEffect(StatusEffects.NIGHT_VISION).isAmbient()) {
                    player.removeStatusEffect(StatusEffects.NIGHT_VISION);
                }
            } else {
                width.setScale(width.getScale() * WEREPYRE_WIDTH);
                height.setScale(height.getScale() * WEREPYRE_HEIGHT);
            }
            ci.cancel();
        }
    }
}