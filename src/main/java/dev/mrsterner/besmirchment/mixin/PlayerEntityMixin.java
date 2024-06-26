package dev.mrsterner.besmirchment.mixin;

import dev.mrsterner.besmirchment.common.BSMConfig;
import dev.mrsterner.besmirchment.common.Besmirchment;
import dev.mrsterner.besmirchment.common.entity.interfaces.DyeableEntity;
import dev.mrsterner.besmirchment.common.registry.BSMTransformations;
import dev.mrsterner.besmirchment.common.transformation.LichAccessor;
import dev.mrsterner.besmirchment.common.transformation.WerepyreAccessor;
import dev.mrsterner.besmirchment.common.transformation.WerepyreTransformation;
import moriyashiine.bewitchment.api.BewitchmentAPI;
import moriyashiine.bewitchment.api.component.BloodComponent;
import moriyashiine.bewitchment.api.component.MagicComponent;
import moriyashiine.bewitchment.api.component.TransformationComponent;
import moriyashiine.bewitchment.api.event.AllowVampireBurn;
import moriyashiine.bewitchment.api.event.AllowVampireHeal;
import moriyashiine.bewitchment.common.component.entity.RespawnTimerComponent;
import moriyashiine.bewitchment.common.network.packet.TransformationAbilityPacket;
import moriyashiine.bewitchment.common.registry.BWComponents;
import moriyashiine.bewitchment.common.registry.BWDamageSources;
import moriyashiine.bewitchment.common.registry.BWObjects;
import moriyashiine.bewitchment.common.registry.BWStatusEffects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings("ConstantConditions")
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin extends LivingEntity implements DyeableEntity, WerepyreAccessor {
    private static final TrackedData<Integer> JUMP_TICKS = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> COLOR = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private static final TrackedData<Integer> WEREPYRE_VARIANT = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public float bsmJumpBeginProgress = 0;

    @Shadow @Nullable
    public abstract ItemEntity dropItem(ItemStack stack, boolean throwRandomly, boolean retainOwnership);

    protected PlayerEntityMixin(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
    }

    @Override
    public void setWerepyreVariant(int variant) {
        dataTracker.set(WEREPYRE_VARIANT, variant);
    }

    @Override
    public int getWerepyreVariant() {
        return dataTracker.get(WEREPYRE_VARIANT);
    }

    @Override
    public void setLastJumpTicks(int ticks) {
        dataTracker.set(JUMP_TICKS, ticks);
    }

    @Override
    public int getLastJumpTicks() {
        return dataTracker.get(JUMP_TICKS);
    }

    @Override
    public void setColor(int color) {
        dataTracker.set(COLOR, color);
    }

    @Override
    public int getColor() {
        return dataTracker.get(COLOR);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void tick(CallbackInfo ci){
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (getLastJumpTicks() < 200){
            setLastJumpTicks(getLastJumpTicks() + 1);
        }
        if (world.isClient){
            if (getLastJumpTicks() > 20) {
                if (bsmJumpBeginProgress > 0) {
                    bsmJumpBeginProgress -= 0.1;
                }
            }else if (bsmJumpBeginProgress < 1){
                bsmJumpBeginProgress += 0.1;
            }
        } else {
            if (BSMTransformations.isLich(this, false) && ((LichAccessor) this).getCachedSouls() == 0){
                BWComponents.MAGIC_COMPONENT.get(player).setMagic(0);
            }
            if (age % 20 == 0){
                if (BSMTransformations.isLich(this, true)) {
                    addStatusEffect(new StatusEffectInstance(BWStatusEffects.ETHEREAL, 40, 0, true, false, false));
                    if (!BewitchmentAPI.drainMagic((PlayerEntity) (Object) this, 1, false)) {
                        TransformationAbilityPacket.useAbility((PlayerEntity) (Object) this, true);
                    }
                }
                if (BSMTransformations.isWerepyre(this, true)){
                    boolean beelzebubPledge = BSMTransformations.hasWerepyrePledge((PlayerEntity) (Object) this);
                    //BSMTransformations.handleNourish((PlayerEntity) (Object) this);

                    if (BWComponents.TRANSFORMATION_COMPONENT.get(player).isAlternateForm() && BWComponents.RESPAWN_TIMER_COMPONENT.get(player).getRespawnTimer() <= 0 && world.isDay() && !world.isRaining() && world.isSkyVisible(getBlockPos())
                            && AllowVampireBurn.EVENT.invoker().allowBurn((PlayerEntity) (Object) this)) {
                        setOnFireFor(8);
                    }
                    WerepyreTransformation.handleStats((PlayerEntity) (Object) this, (BWComponents.TRANSFORMATION_COMPONENT.get(player).isAlternateForm()), beelzebubPledge);
                    HungerManager hungerManager = ((PlayerEntity) (Object) this).getHungerManager();
                    if (BWComponents.BLOOD_COMPONENT.get(player).getBlood() > 0 && AllowVampireHeal.EVENT.invoker().allowHeal((PlayerEntity) (Object) this, beelzebubPledge)) {
                        if (age % (beelzebubPledge ? 30 : 40) == 0) {
                            if (getHealth() < getMaxHealth()) {
                                heal(1);
                                hungerManager.addExhaustion(3);
                            }
                            if ((hungerManager.isNotFull() || hungerManager.getSaturationLevel() < 10) && (BWComponents.BLOOD_COMPONENT.get(player)).drainBlood(1, false)) {
                                hungerManager.add(1, 20);
                            }
                        }
                    }
                }
            }
            if (isSneaking() && BewitchmentAPI.getFamiliar((PlayerEntity) (Object)this) == EntityType.CHICKEN){
                if (!isOnGround() && !hasStatusEffect(StatusEffects.SLOW_FALLING)){
                    addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 20, 0, false, false, false));
                }
                if (random.nextFloat() < BSMConfig.chickenFamiliarEggChance){
                    dropItem(new ItemStack(Items.EGG), true, true);
                }
            }
        }


    }

    @Override
    public float getLastJumpProgress() {
        return bsmJumpBeginProgress;
    }

    @Inject(method = "onDeath", at = @At("TAIL"))
    private void onDeath(DamageSource source, CallbackInfo ci){
        if (BewitchmentAPI.getFamiliar((PlayerEntity) (Object)this) == EntityType.PIG){
            dropItem(new ItemStack(source.isFire() ? Items.COOKED_PORKCHOP : Items.PORKCHOP, random.nextInt(6) + 1), true, true);
        }
    }

    @Inject(method = "eatFood", at = @At("HEAD"))
    private void eat(World world, ItemStack stack, CallbackInfoReturnable<ItemStack> callbackInfo) {
        if (!world.isClient) {
            if (BewitchmentAPI.getFamiliar((PlayerEntity) (Object)this) == EntityType.PIG) {
                addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0, true, false));
                addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 100, 0, true, false));
            }
            FoodComponent foodComponent = stack.getItem().getFoodComponent();
            if (foodComponent != null) {
                boolean werepyre = BSMTransformations.isWerepyre(this, true);
                if (werepyre) {
                    addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 100, 1));
                    addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 1));
                    addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 1));
                    addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 1));
                }
                if (werepyre && (stack.getItem() == BWObjects.GARLIC || stack.getItem() == BWObjects.GRILLED_GARLIC || stack.getItem() == BWObjects.GARLIC_BREAD)) {
                    damage(BWDamageSources.MAGIC_COPY, Float.MAX_VALUE);
                }
            }
        }
    }

    @Inject(method = "canFoodHeal", at = @At("RETURN"), cancellable = true)
    private void canFoodHeal(CallbackInfoReturnable<Boolean> callbackInfo) {
        if (callbackInfo.getReturnValue() && BewitchmentAPI.isVampire(this, true)) {
            callbackInfo.setReturnValue(false);
        }
    }

    @Inject(method = "handleFallDamage", at = @At("HEAD"), cancellable = true)
    private void handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource, CallbackInfoReturnable<Boolean> callbackInfo) {
        if (isSneaking() && (Object) this instanceof PlayerEntity && BewitchmentAPI.getFamiliar((PlayerEntity) (Object) this) == EntityType.CHICKEN) {
            callbackInfo.setReturnValue(false);
        }
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void readCustomDataFromTag(NbtCompound tag, CallbackInfo callbackInfo) {
        setColor(tag.getInt("BSMColor"));
        setLastJumpTicks(tag.getInt("BSMLastJumpTicks"));
        setWerepyreVariant(tag.getInt("BSMWerepyreVariant"));
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void writeCustomDataToTag(NbtCompound tag, CallbackInfo callbackInfo) {
        tag.putInt("BSMColor", getColor());
        tag.putInt("BSMLastJumpTicks", getLastJumpTicks());
        tag.putInt("BSMWerepyreVariant", getWerepyreVariant());
    }

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void initDataTracker(CallbackInfo callbackInfo) {
        dataTracker.startTracking(COLOR, -1);
        dataTracker.startTracking(JUMP_TICKS, 0);
        dataTracker.startTracking(WEREPYRE_VARIANT, 0);
    }
}
