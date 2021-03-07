package de.aelpecyem.besmirchment.mixin;

import de.aelpecyem.besmirchment.common.registry.BSMTransformations;
import dev.emi.nourish.NourishComponent;
import dev.emi.nourish.NourishMain;
import dev.emi.nourish.groups.NourishGroup;
import dev.emi.nourish.groups.NourishGroups;
import moriyashiine.bewitchment.api.BewitchmentAPI;
import moriyashiine.bewitchment.api.interfaces.entity.BloodAccessor;
import moriyashiine.bewitchment.common.Bewitchment;
import moriyashiine.bewitchment.common.entity.interfaces.RespawnTimerAccessor;
import moriyashiine.bewitchment.common.entity.interfaces.WerewolfAccessor;
import moriyashiine.bewitchment.common.misc.BWUtil;
import moriyashiine.bewitchment.common.network.packet.TransformationAbilityPacket;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BWUtil.class)
public class BWUtilMixin {
    @Inject(method = "doVampireLogic", at = @At("HEAD"), cancellable = true)
    private static void doVampireLogic(PlayerEntity player, boolean alternateForm, CallbackInfo ci) {
        if (BSMTransformations.isWerepyre(player, true)) {
            boolean pledged = BSMTransformations.hasWerepyrePledge(player);
            if (((RespawnTimerAccessor) player).getRespawnTimer() <= 0 && player.world.isDay() && !player.world.isRaining() && player.world.isSkyVisible(player.getBlockPos())) {
                player.setOnFireFor(8);
            }
            HungerManager hungerManager = player.getHungerManager();
            if (((BloodAccessor) player).getBlood() > 0) {
                if (player.age % (pledged ? 30 : 40) == 0) {
                    if (player.getHealth() < player.getMaxHealth()) {
                        player.heal(1);
                        hungerManager.addExhaustion(3);
                    }
                    if ((hungerManager.isNotFull() || hungerManager.getSaturationLevel() < 10) && ((BloodAccessor) player).drainBlood(1, false)) {
                        hungerManager.add(1, 20);
                    }
                }
                if (Bewitchment.isNourishLoaded) {
                    NourishComponent nourishComponent = NourishMain.NOURISH.get(player);
                    for (NourishGroup group : NourishGroups.groups) {
                        if (nourishComponent.getValue(group) != group.getDefaultValue()) {
                            nourishComponent.setValue(group, group.getDefaultValue());
                        }
                    }
                }
            }
            ci.cancel();
        }
    }

    @Inject(method = "doWerewolfLogic", at = @At("HEAD"), cancellable = true)
    private static void doWerewolfLogic(PlayerEntity player, boolean alternateForm, CallbackInfo ci){
        if (BSMTransformations.isWerepyre(player, true)) {
            boolean forced = ((WerewolfAccessor) player).getForcedTransformation();
            if (!alternateForm && BewitchmentAPI.getMoonPhase(player.world) == 0 && player.world.isNight() && player.world.isSkyVisible(player.getBlockPos())) {
                TransformationAbilityPacket.useAbility(player, true);
                ((WerewolfAccessor) player).setForcedTransformation(true);
            } else if (alternateForm && forced && (player.world.isDay() || BewitchmentAPI.getMoonPhase(player.world) != 0)) {
                TransformationAbilityPacket.useAbility(player, true);
                ((WerewolfAccessor) player).setForcedTransformation(false);
            }
            if (alternateForm) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, Integer.MAX_VALUE, 0, true, false));
                player.getArmorItems().forEach(stack -> player.dropStack(stack.split(1)));
                if (BWUtil.isTool(player.getMainHandStack())) {
                    player.dropStack(player.getMainHandStack().split(1));
                }
                if (BWUtil.isTool(player.getOffHandStack())) {
                    player.dropStack(player.getOffHandStack().split(1));
                }
                if (!forced && !BSMTransformations.hasWerepyrePledge(player)) {
                    TransformationAbilityPacket.useAbility(player, true);
                }
            }
            ci.cancel();
        }
    }
}
