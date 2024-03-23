package dev.mrsterner.besmirchment.mixin;

import dev.mrsterner.besmirchment.client.renderer.LichRollAccessor;
import dev.mrsterner.besmirchment.common.block.entity.PhylacteryBlockEntity;
import dev.mrsterner.besmirchment.common.entity.LichGemItem;
import dev.mrsterner.besmirchment.common.entity.WerepyreEntity;
import dev.mrsterner.besmirchment.common.registry.BSMContracts;
import dev.mrsterner.besmirchment.common.registry.BSMObjects;
import dev.mrsterner.besmirchment.common.registry.BSMTags;
import dev.mrsterner.besmirchment.common.registry.BSMTransformations;
import dev.mrsterner.besmirchment.common.transformation.LichAccessor;
import dev.mrsterner.besmirchment.common.transformation.LichLogic;
import dev.mrsterner.besmirchment.common.transformation.WerepyreAccessor;
import moriyashiine.bewitchment.api.BewitchmentAPI;
import moriyashiine.bewitchment.api.component.BloodComponent;
import moriyashiine.bewitchment.api.component.ContractsComponent;
import moriyashiine.bewitchment.api.component.TransformationComponent;
import moriyashiine.bewitchment.api.registry.Transformation;
import moriyashiine.bewitchment.client.network.packet.SpawnSmokeParticlesPacket;
import moriyashiine.bewitchment.common.BWConfig;
import moriyashiine.bewitchment.common.Bewitchment;
import moriyashiine.bewitchment.common.entity.living.VampireEntity;
import moriyashiine.bewitchment.common.entity.living.util.BWHostileEntity;
import moriyashiine.bewitchment.common.network.packet.TransformationAbilityPacket;
import moriyashiine.bewitchment.common.registry.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Pair;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3f;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings("ConstantConditions")
@Mixin(value = LivingEntity.class, priority = 999)
public abstract class LivingEntityMixin extends Entity implements LichRollAccessor, LichAccessor {
    private int bsm_lastRevive = 0;
    private int bsm_cachedSouls = 0;
    private int bsm_lastRoll = 100;

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

    @Shadow
    public abstract float getYaw(float tickDelta);

    @Shadow
    public abstract boolean hasStatusEffect(StatusEffect effect);

    @Shadow public abstract boolean isSleeping();

    public LivingEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @ModifyVariable(method = "applyArmorToDamage", at = @At("HEAD"))
    private float modifyDamage1(float amount, DamageSource source) {
        if (source.isProjectile()) {
            if (source.getAttacker() instanceof PlayerEntity player) {
                ContractsComponent accessor = BWComponents.CONTRACTS_COMPONENT.get(player);
                if (accessor.hasContract(BSMContracts.CONQUEST)) {
                    return amount * 2;
                }
            }
        }
        return amount;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void tick(CallbackInfo ci) {
        if (!world.isClient) {
            if (bsm_lastRevive < 1000 && isAlive()) {
                bsm_lastRevive++;
            }
            LivingEntity livingEntity = (LivingEntity) (Object) this;
            int damage = 0;
            if (livingEntity.getMainHandStack().getItem() == BWObjects.GARLIC && BSMTransformations.isWerepyre(this, true)) {
                damage++;
            }
            if (livingEntity.getOffHandStack().getItem() == BWObjects.GARLIC && BSMTransformations.isWerepyre(this, true)) {
                damage++;
            }
            if (damage > 0) {
                damage(BWDamageSources.MAGIC_COPY, damage);
            }
            //mathematically equal to not changing the blood at all
            if (getType().isIn(BWTags.HAS_BLOOD) && BSMTransformations.isWerepyre(this, true) && random.nextFloat() < (isSleeping() ? 1 / 50f : 1 / 500f)) {
                BWComponents.BLOOD_COMPONENT.get(livingEntity).drainBlood(1, false);
            }
        }
    }

    @Inject(method = "onKilledBy", at = @At("RETURN"))
    private void onKilledBy(LivingEntity adversary, CallbackInfo ci) {
        if (getType().isIn(BSMTags.PURE_SOULS) && adversary != null && adversary.isHolding(BSMObjects.LICH_GEM)) {
            for (ItemStack itemStack : adversary.getItemsEquipped()) {
                if (itemStack.getItem().equals(BSMObjects.LICH_GEM) && !LichGemItem.isSouled(itemStack)) {
                    LichGemItem.setSouled(itemStack, true);
                    playSound(BWSoundEvents.ENTITY_GENERIC_PLING, 0.5F, MathHelper.nextFloat(random, 0.7F, 1.5F));
                    break;
                }
            }
        }
    }

    @Inject(method = "eatFood", at = @At("HEAD"))
    private void eatFood(World world, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        LivingEntity livingEntity = (LivingEntity) (Object) this;
        if (livingEntity instanceof PlayerEntity player && BSMTransformations.isLich(this, false) && hasStatusEffect(StatusEffects.WEAKNESS)) {
            if (stack.getItem().equals(Items.GOLDEN_APPLE)) {
                playSound(BWSoundEvents.ENTITY_GENERIC_TRANSFORM, 1, 1);
                BWComponents.TRANSFORMATION_COMPONENT.get(player).setAlternateForm(false);
                BWComponents.TRANSFORMATION_COMPONENT.get(player).getTransformation().onRemoved((PlayerEntity) (Object) this);
                BWComponents.TRANSFORMATION_COMPONENT.get(player).setTransformation(BWTransformations.HUMAN);
                BWComponents.TRANSFORMATION_COMPONENT.get(player).getTransformation().onAdded((PlayerEntity) (Object) this);
                if (world.isClient) {
                    for (int i = 0; i < 20; i++) {
                        Vec3f vecf = new Vec3f(Vec3d.unpackRgb(16711680));
                        //TODO check vecf for right color
                        world.addParticle(new DustParticleEffect(vecf,1F), getParticleX(1), getRandomBodyY(), getParticleZ(1), 0, 0, 0);
                    }
                }
            }
        }
    }

    @Inject(method = "tryUseTotem", at = @At("RETURN"), cancellable = true)
    private void tryUseTotem(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && BSMTransformations.isLich(this, false)) {
            if (LichLogic.revive((LivingEntity) (Object) this, source, bsm_lastRevive)) {
                cir.setReturnValue(true);
                bsm_lastRevive = 0;
            }
            updateCachedSouls();
        }
        if (!world.isClient) {
                if (cir.getReturnValue() && (Object) this instanceof PlayerEntity playerEntity && (BWComponents.CURSES_COMPONENT.get(playerEntity).hasCurse(BWCurses.SUSCEPTIBILITY) || !BWConfig.enableCurses)) {
                    TransformationComponent transformationComponent = BWComponents.TRANSFORMATION_COMPONENT.get(playerEntity);
                    Transformation transformation = transformationComponent.getTransformation();
                    if (transformation == BWTransformations.WEREWOLF || transformation == BWTransformations.HUMAN) { //no vampires
                        boolean sourceVampire = source.getSource() instanceof VampireEntity || (BewitchmentAPI.isVampire(source.getSource(), true) && source.getSource() instanceof PlayerEntity && BewitchmentAPI.isPledged((PlayerEntity) source.getSource(), BWPledges.LILITH));
                        boolean sourceWerepyre = source.getSource() instanceof WerepyreEntity || (BSMTransformations.isWerepyre(source.getSource(), true) && BSMTransformations.hasWerepyrePledge((PlayerEntity) source.getSource()));
                        if ((transformation == BWTransformations.WEREWOLF && sourceVampire) || (transformation == BWTransformations.HUMAN && sourceWerepyre)) {
                            transformationComponent.getTransformation().onRemoved(playerEntity);
                            transformationComponent.setTransformation(BSMTransformations.WEREPYRE);
                            transformationComponent.getTransformation().onAdded(playerEntity);
                            PlayerLookup.tracking(playerEntity).forEach(foundPlayer -> SpawnSmokeParticlesPacket.send(foundPlayer, playerEntity));
                            SpawnSmokeParticlesPacket.send(playerEntity, playerEntity);
                            playerEntity.world.playSound(null, playerEntity.getBlockPos(), BWSoundEvents.ENTITY_GENERIC_CURSE, playerEntity.getSoundCategory(), 1, 1);
                            int variant = -1;
                            if (source.getSource() instanceof WerepyreEntity) {
                                variant = source.getSource().getDataTracker().get(BWHostileEntity.VARIANT);
                            } else if (source.getSource() instanceof WerepyreAccessor && BSMTransformations.hasWerepyrePledge((PlayerEntity) source.getSource())) {
                                variant = ((WerepyreAccessor) source.getSource()).getWerepyreVariant();
                            }
                            if (variant > -1) {
                                ((WerepyreAccessor) playerEntity).setWerepyreVariant(variant);
                            }
                        }
                    }
                }
        }
    }

    @Inject(method = "isInSwimmingPose", at = @At("HEAD"), cancellable = true)
    private void isInSwimmingPose(CallbackInfoReturnable<Boolean> cir) {
        if (BSMTransformations.isLich(this, true)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "swingHand(Lnet/minecraft/util/Hand;)V", at = @At("HEAD"), cancellable = true)
    private void swingHand(Hand hand, CallbackInfo ci) {
        if (BSMTransformations.isLich(this, true)) {
            ci.cancel();
            if (world.isClient && bsm_lastRoll >= 20) {
                bsm_lastRoll = 0;
            }
        }
    }

    @Inject(method = "tickMovement", at = @At("TAIL"))
    private void tickMovement(CallbackInfo ci) {
        if (world.isClient) {
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
        } else {
            this.bsm_cachedSouls = 0;
        }
        if (BSMTransformations.isLich(this, false)) {
            if ((Object) this instanceof PlayerEntity) {
                LichLogic.addAttributes((LivingEntity) (Object) this, bsm_cachedSouls);
            }
        }
       if (BSMTransformations.isLich(this, true)) {
            TransformationAbilityPacket.useAbility((PlayerEntity) (Object) this, true);
        }
    }

    @Inject(method = "getGroup", at = @At("HEAD"), cancellable = true)
    private void getGroup(CallbackInfoReturnable<EntityGroup> cir) {
        if (BSMTransformations.isLich(this, false)) {
            cir.setReturnValue(EntityGroup.UNDEAD);
        }
    }

    @Inject(method = "handleFallDamage", at = @At("HEAD"), cancellable = true)
    private void handleFallDamage(float fallDistance, float damageMultiplier,DamageSource damageSource, CallbackInfoReturnable<Boolean> callbackInfo) {
        if (BSMTransformations.isWerepyre(this, false) && fallDistance <= 12) {
            callbackInfo.setReturnValue(false);
        }
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void writeCustomDataToTag(NbtCompound tag, CallbackInfo ci) {
        tag.putInt("BSMLastRevive", bsm_lastRevive);
        tag.putInt("BSMSoulCache", bsm_cachedSouls);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void readCustomDataFromTag(NbtCompound tag, CallbackInfo ci) {
        bsm_lastRevive = tag.getInt("BSMLastRevive");
        bsm_cachedSouls = tag.getInt("BSMSoulCache");
        if (world instanceof ServerWorld) {
            updateCachedSouls();
        }
    }
}
