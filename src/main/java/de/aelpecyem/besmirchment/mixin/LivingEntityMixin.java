package de.aelpecyem.besmirchment.mixin;

import de.aelpecyem.besmirchment.client.renderer.LichRollAccessor;
import de.aelpecyem.besmirchment.common.Besmirchment;
import de.aelpecyem.besmirchment.common.block.entity.PhylacteryBlockEntity;
import de.aelpecyem.besmirchment.common.entity.LichGemItem;
import de.aelpecyem.besmirchment.common.entity.WerepyreEntity;
import de.aelpecyem.besmirchment.common.registry.BSMObjects;
import de.aelpecyem.besmirchment.common.registry.BSMTags;
import de.aelpecyem.besmirchment.common.transformation.LichAccessor;
import de.aelpecyem.besmirchment.common.transformation.WerepyreAccessor;
import de.aelpecyem.besmirchment.common.registry.BSMContracts;
import de.aelpecyem.besmirchment.common.registry.BSMTransformations;
import de.aelpecyem.besmirchment.common.transformation.LichLogic;
import moriyashiine.bewitchment.api.BewitchmentAPI;
import moriyashiine.bewitchment.api.interfaces.entity.ContractAccessor;
import moriyashiine.bewitchment.api.interfaces.entity.CurseAccessor;
import moriyashiine.bewitchment.api.interfaces.entity.TransformationAccessor;
import moriyashiine.bewitchment.client.network.packet.SpawnSmokeParticlesPacket;
import moriyashiine.bewitchment.common.entity.living.VampireEntity;
import moriyashiine.bewitchment.common.entity.living.util.BWHostileEntity;
import moriyashiine.bewitchment.common.registry.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Pair;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.invoke.SerializedLambda;

@Mixin(value = LivingEntity.class, priority = 1001)
public abstract class LivingEntityMixin extends Entity implements LichRollAccessor, LichAccessor {
    private int bsm_lastRevive = 0;
    private int bsm_cachedSouls = 0;
    @Environment(EnvType.CLIENT) private int bsm_lastRoll = 100;
    @Shadow
    protected abstract float getSoundVolume();

    @Shadow
    protected abstract float getSoundPitch();

    @Shadow
    public abstract boolean teleport(double x, double y, double z, boolean particleEffects);

    @Shadow
    public abstract void setHealth(float health);

    @Shadow
    public abstract boolean clearStatusEffects();

    @Shadow
    public abstract boolean addStatusEffect(StatusEffectInstance effect);

    @Shadow
    public abstract float getMaxHealth();

    @Shadow
    public abstract boolean isAlive();

    @Shadow public abstract float getYaw(float tickDelta);

    public LivingEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @ModifyVariable(method = "applyArmorToDamage", at = @At("HEAD"))
    private float modifyDamage1(float amount, DamageSource source) {
        if (source.isProjectile()) {
            if (source.getAttacker() instanceof ContractAccessor) {
                ContractAccessor accessor = (ContractAccessor) source.getAttacker();
                if (accessor.hasContract(BSMContracts.CONQUEST)) {
                    return amount * 2;
                }
            }
            ContractAccessor accessor = (ContractAccessor) this;
            if (accessor.hasNegativeEffects() && accessor.hasContract(BSMContracts.CONQUEST)) {
                return amount * 2;
            }
        }
        return amount;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void tick(CallbackInfo ci) {
        if (!world.isClient && bsm_lastRevive < 1000 && isAlive()) {
            bsm_lastRevive++;
        }
    }

    @Inject(method = "onKilledBy", at = @At("RETURN"))
    private void onKilledBy(LivingEntity adversary, CallbackInfo ci){
        if (getType().isIn(BSMTags.PURE_SOULS) && adversary != null && adversary.isHolding(BSMObjects.LICH_GEM) && adversary.getMainHandStack().getItem().equals(BWObjects.ATHAME)){
            for (ItemStack itemStack : adversary.getItemsEquipped()) {
                if (itemStack.getItem().equals(BSMObjects.LICH_GEM) && !LichGemItem.isSouled(itemStack)){
                    LichGemItem.setSouled(itemStack, true);
                    playSound(BWSoundEvents.ENTITY_GENERIC_PLING, 0.5F, MathHelper.nextFloat(random, 0.7F, 1.5F));
                    break;
                }
            }
        }
    }

    @Inject(method = "tryUseTotem", at = @At("RETURN"), cancellable = true)
    private void tryUseTotem(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && BSMTransformations.isLich(this, false)) {
            if(LichLogic.revive((LivingEntity) (Object) this, source, bsm_lastRevive)){
                cir.setReturnValue(true);
                bsm_lastRevive = 0;
            }
            updateCachedSouls();
        }
        if (Besmirchment.config.enableWerepyrism && cir.getReturnValue() && this instanceof TransformationAccessor && ((CurseAccessor) this).hasCurse(BWCurses.SUSCEPTIBILITY)) {
            TransformationAccessor transformationAccessor = (TransformationAccessor) this;
            if (transformationAccessor.getTransformation() == BWTransformations.WEREWOLF) { //no vampire because they can't use totems
                if (source.getSource() instanceof VampireEntity || (BewitchmentAPI.isVampire(source.getSource(), true) && BewitchmentAPI.isPledged(world, BWPledges.LILITH, source.getSource().getUuid()))) {
                    transformationAccessor.getTransformation().onRemoved((LivingEntity) (Object) this);
                    transformationAccessor.setTransformation(BSMTransformations.WEREPYRE);
                    transformationAccessor.getTransformation().onAdded((LivingEntity) (Object) this);
                    PlayerLookup.tracking(this).forEach(foundPlayer -> SpawnSmokeParticlesPacket.send(foundPlayer, this));
                    if ((Object) this instanceof PlayerEntity) {
                        SpawnSmokeParticlesPacket.send((PlayerEntity) (Object) this, this);
                    }
                    world.playSound(null, getBlockPos(), BWSoundEvents.ENTITY_GENERIC_CURSE, getSoundCategory(), getSoundVolume(), getSoundPitch());
                }
            } else if (transformationAccessor.getTransformation() == BWTransformations.HUMAN) {
                if (source.getSource() instanceof WerepyreEntity || (BSMTransformations.isWerepyre(source.getSource(), true) && BSMTransformations.hasWerepyrePledge((PlayerEntity) source.getSource()))) {
                    transformationAccessor.getTransformation().onRemoved((LivingEntity) (Object) this);
                    transformationAccessor.setTransformation(BSMTransformations.WEREPYRE);
                    transformationAccessor.getTransformation().onAdded((LivingEntity) (Object) this);
                    PlayerLookup.tracking(this).forEach(foundPlayer -> SpawnSmokeParticlesPacket.send(foundPlayer, this));
                    if ((Object) this instanceof PlayerEntity) {
                        SpawnSmokeParticlesPacket.send((PlayerEntity) (Object) this, this);
                    }
                    if (this instanceof WerepyreAccessor) {
                        int variant = -1;
                        if (source.getSource() instanceof WerepyreEntity) {
                            variant = source.getSource().getDataTracker().get(BWHostileEntity.VARIANT);
                        } else if (source.getSource() instanceof WerepyreAccessor && BSMTransformations.hasWerepyrePledge((PlayerEntity) source.getSource())) {
                            variant = ((WerepyreAccessor) source.getSource()).getWerepyreVariant();
                        }
                        if (variant > -1) {
                            ((WerepyreAccessor) this).setWerepyreVariant(variant);
                        }
                    }
                    world.playSound(null, getBlockPos(), BWSoundEvents.ENTITY_GENERIC_CURSE, getSoundCategory(), getSoundVolume(), getSoundPitch());
                }
            }
        }
    }

    @Inject(method = "isInSwimmingPose", at = @At("HEAD"), cancellable = true)
    private void isInSwimmingPose(CallbackInfoReturnable<Boolean> cir) {
        if (BSMTransformations.isLich(this, true)){
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "swingHand(Lnet/minecraft/util/Hand;)V", at = @At("HEAD"), cancellable = true)
    private void swingHand(Hand hand, CallbackInfo ci){
        if (BSMTransformations.isLich(this, true)){
            ci.cancel();
            if (world.isClient && bsm_lastRoll >= 20){
                bsm_lastRoll = 0;
            }
        }
    }

    @Inject(method = "tickMovement", at = @At("TAIL"))
    private void tickMovement(CallbackInfo ci){
        if (world.isClient){
            bsm_lastRoll++;
        }
    }

    @Override
    public int getLastRollTicks() {
        return bsm_lastRoll;
    }

    @Override
    public int getCachedSouls() {
        return bsm_cachedSouls;
    }

    @Override
    public void updateCachedSouls() {
        Pair<ServerWorld, PhylacteryBlockEntity> phylactery = LichLogic.getPhylactery((LivingEntity) (Object) this);
        if (phylactery != null) {
            this.bsm_cachedSouls = phylactery.getRight().souls;
        }else{
            this.bsm_cachedSouls = 0;
        }
        if ((Object) this instanceof PlayerEntity) {
            LichLogic.addAttributes((LivingEntity) (Object) this, bsm_cachedSouls);
        }
    }

    @Inject(method = "writeCustomDataToTag", at = @At("TAIL"))
    private void writeCustomDataToTag(CompoundTag tag, CallbackInfo ci) {
        tag.putInt("BSMLastRevive", bsm_lastRevive);
        tag.putInt("BSMSoulCache", bsm_cachedSouls);
    }

    @Inject(method = "readCustomDataFromTag", at = @At("TAIL"))
    private void readCustomDataFromTag(CompoundTag tag, CallbackInfo ci) {
        bsm_lastRevive = tag.getInt("BSMLastRevive");
        bsm_cachedSouls = tag.getInt("BSMSoulCache");
        if (world instanceof ServerWorld){
            updateCachedSouls();
        }
    }
}
