package dev.mrsterner.besmirchment.mixin;

import dev.mrsterner.besmirchment.common.BSMConfig;
import dev.mrsterner.besmirchment.common.Besmirchment;
import dev.mrsterner.besmirchment.common.entity.ai.DemonAttackWithOwnerGoal;
import dev.mrsterner.besmirchment.common.entity.ai.DemonFollowOwnerGoal;
import dev.mrsterner.besmirchment.common.entity.ai.DemonSitGoal;
import dev.mrsterner.besmirchment.common.entity.ai.DemonTrackAttackerGoal;
import dev.mrsterner.besmirchment.common.entity.interfaces.TameableDemon;
import dev.mrsterner.besmirchment.common.registry.BSMObjects;
import moriyashiine.bewitchment.api.BewitchmentAPI;
import moriyashiine.bewitchment.api.entity.Pledgeable;
import moriyashiine.bewitchment.common.entity.DemonMerchant;
import moriyashiine.bewitchment.common.entity.living.DemonEntity;
import moriyashiine.bewitchment.common.entity.living.util.BWHostileEntity;
import moriyashiine.bewitchment.common.item.TaglockItem;
import moriyashiine.bewitchment.common.misc.BWUtil;
import moriyashiine.bewitchment.common.registry.BWMaterials;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.ServerConfigHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Util;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.UUID;

@Mixin(DemonEntity.class)
public abstract class DemonEntityMixin extends BWHostileEntity implements TameableDemon, DemonMerchant {
    protected DemonEntityMixin(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
    }

    private static final TrackedData<Byte> TAMEABLE_FLAGS = DataTracker.registerData(DemonEntity.class, TrackedDataHandlerRegistry.BYTE);
    private static final TrackedData<Optional<UUID>> OWNER_UUID = DataTracker.registerData(DemonEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private boolean sitting;

    @Nullable
    @Override
    public LivingEntity getTarget() {
        return super.getTarget();
    }

    @Inject(method = "initGoals", at = @At("HEAD"), cancellable = true) //look i did the bad
    private void initGoals(CallbackInfo ci) {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(2, new MeleeAttackGoal(this, 1, true));
        this.goalSelector.add(3, new WanderAroundFarGoal(this, 1));
        this.goalSelector.add(4, new LookAtEntityGoal(this, PlayerEntity.class, 8));
        this.goalSelector.add(5, new LookAroundGoal(this));
        this.targetSelector.add(0, new RevengeGoal(this));
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, LivingEntity.class, 10, true, false, entity -> !isTamed() && !(entity instanceof ArmorStandEntity) && BWUtil.getArmorPieces(entity, stack -> stack.getItem() instanceof ArmorItem && ((ArmorItem) stack.getItem()).getMaterial() == BWMaterials.BESMIRCHED_ARMOR) < 3 && (entity.getGroup() != BewitchmentAPI.DEMON || entity instanceof PlayerEntity)));

        this.goalSelector.add(1, new DemonSitGoal((DemonEntity) (TameableDemon) this));
        this.goalSelector.add(3, new DemonFollowOwnerGoal((DemonEntity) (TameableDemon) this, 1.0D, 10.0F, 2.0F, false));

        this.targetSelector.add(1, new DemonTrackAttackerGoal((DemonEntity) (TameableDemon) this));
        this.targetSelector.add(2, new DemonAttackWithOwnerGoal((DemonEntity) (TameableDemon) this));
        ci.cancel();
    }

    @Inject(method = "interactMob", at = @At("HEAD"), cancellable = true)
    private void interactMob(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        ItemStack itemStack = player.getStackInHand(hand);
        Item item = itemStack.getItem();
        if (!this.world.isClient && this.isTamed() && player.isSneaking()) { //add guard mode instead ig?
            if (world.random.nextFloat() < 0.1) {
                heal(2);
                this.world.sendEntityStatus(this, (byte) 7);
            }
            if (item == Items.CAKE) {
                heal(40);
                addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 1, 1200, true, true));
                if (!player.getAbilities().creativeMode) {
                    itemStack.decrement(1);
                }
                this.world.sendEntityStatus(this, (byte) 7);
                cir.setReturnValue(ActionResult.SUCCESS);
                return;
            }
            ActionResult actionResult = super.interactMob(player, hand);
            if ((!actionResult.isAccepted() || this.isBaby()) && this.isOwner(player)) {
                this.setSitting(!this.isSitting());
                player.sendMessage(Text.translatable(Besmirchment.MODID + ".message.demon_" + (isSitting() ? "sit" : "follow")), true);
                this.jumping = false;
                this.navigation.stop();
                this.setTarget(null);
                cir.setReturnValue(ActionResult.SUCCESS);
            }
        } else if (BSMConfig.enableTamableDemons && !isTamed() && item == BSMObjects.DEMONIC_DEED && TaglockItem.hasTaglock(itemStack) && !isAttacking()) {
            if (!player.getAbilities().creativeMode) {
                itemStack.damage(1, player, breakingPlayer -> itemStack.decrement(1));
            }
            setTamed(true);
            this.setOwnerUuid(TaglockItem.getTaglockUUID(itemStack));
            this.navigation.stop();
            this.setTarget(null);
            this.setSitting(true);
            this.world.sendEntityStatus(this, (byte) 7);
            cir.setReturnValue(ActionResult.SUCCESS);
        }
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    public void writeCustomDataToTag(NbtCompound tag, CallbackInfo ci) {
        if (this.getOwnerUuid() != null) {
            tag.putUuid("Owner", this.getOwnerUuid());
        }
        tag.putBoolean("Sitting", this.sitting);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    public void readCustomDataFromTag(NbtCompound tag, CallbackInfo ci) {
        super.readCustomDataFromNbt(tag);
        UUID ownerUUID;
        if (tag.containsUuid("Owner")) {
            ownerUUID = tag.getUuid("Owner");
        } else {
            String string = tag.getString("Owner");
            ownerUUID = ServerConfigHandler.getPlayerUuidByName(this.getServer(), string);
        }

        if (ownerUUID != null) {
            try {
                this.setOwnerUuid(ownerUUID);
                this.setTamed(true);
            } catch (Throwable var4) {
                this.setTamed(false);
            }
        }

        this.sitting = tag.getBoolean("Sitting");
    }

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    protected void initDataTracker(CallbackInfo ci) {
        dataTracker.startTracking(TAMEABLE_FLAGS, (byte) 0);
        dataTracker.startTracking(OWNER_UUID, Optional.empty());
    }

    @Unique
    public void showEmoteParticle(boolean positive) {
        ParticleEffect particleEffect = ParticleTypes.HEART;
        if (!positive) {
            particleEffect = ParticleTypes.SMOKE;
        }

        for (int i = 0; i < 7; ++i) {
            double d = this.random.nextGaussian() * 0.02D;
            double e = this.random.nextGaussian() * 0.02D;
            double f = this.random.nextGaussian() * 0.02D;
            this.world.addParticle(particleEffect, this.getParticleX(1.0D), this.getRandomBodyY() + 0.5D, this.getParticleZ(1.0D), d, e, f);
        }
    }
/*
    @Inject(method = "onDeath", at = @At("TAIL"))
    public void onDeath(DamageSource source, CallbackInfo ci) {
        if (!this.world.isClient && this.world.getGameRules().getBoolean(GameRules.SHOW_DEATH_MESSAGES) && this.getOwner() instanceof ServerPlayerEntity) {
            this.getOwner().sendSystemMessage(this.getDamageTracker().getDeathMessage(), Util.NIL_UUID);
        }
    }

 */

    @Override
    public boolean isSitting() {
        return this.sitting;
    }

    @Override
    public void setSitting(boolean sitting) {
        this.sitting = sitting;
    }

    @Override
    public boolean isOwner(LivingEntity entity) {
        return entity == this.getOwner();
    }

    @Override
    public boolean isTamed() {
        return (this.dataTracker.get(TAMEABLE_FLAGS) & 4) != 0;
    }

    @Override
    public void setTamed(boolean tamed) {
        byte b = this.dataTracker.get(TAMEABLE_FLAGS);
        if (tamed) {
            this.dataTracker.set(TAMEABLE_FLAGS, (byte) (b | 4));
        } else {
            this.dataTracker.set(TAMEABLE_FLAGS, (byte) (b & -5));
        }

        this.onTamedChanged();
    }

    @Nullable
    @Override
    public UUID getOwnerUuid() {
        return (UUID) ((Optional) this.dataTracker.get(OWNER_UUID)).orElse(null);
    }

    @Override
    public void setOwnerUuid(@Nullable UUID uuid) {
        this.dataTracker.set(OWNER_UUID, Optional.ofNullable(uuid));
    }

    @Override
    public void setOwner(PlayerEntity player) {
        this.setTamed(true);
        this.setOwnerUuid(player.getUuid());
    }

    @Nullable
    @Override
    public LivingEntity getOwner() {
        try {
            UUID uUID = this.getOwnerUuid();
            return uUID == null ? null : this.world.getPlayerByUuid(uUID);
        } catch (IllegalArgumentException var2) {
            return null;
        }
    }



}
