package dev.mrsterner.besmirchment.common.transformation;

import dev.mrsterner.besmirchment.common.entity.WerepyreEntity;
import moriyashiine.bewitchment.api.registry.Transformation;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;

import java.util.UUID;

public class WerepyreTransformation extends Transformation {
    private static final EntityAttributeModifier WEREPYRE_ATTACK_DAMAGE_MODIFIER_1 = new EntityAttributeModifier(UUID.fromString("37afd151-f4a0-4a0d-af20-38347f497ece"), "Transformation modifier", 14, EntityAttributeModifier.Operation.ADDITION);
    private static final EntityAttributeModifier WEREPYRE_ARMOR_TOUGHNESS_MODIFIER_1 = new EntityAttributeModifier(UUID.fromString("ece66a59-7131-410f-9e96-f6854dfc0d32"), "Transformation modifier", 20, EntityAttributeModifier.Operation.ADDITION);
    private static final EntityAttributeModifier WEREPYRE_MOVEMENT_SPEED_MODIFIER_1 = new EntityAttributeModifier(UUID.fromString("90299a19-b09f-4a03-a125-fa0af17bc591"), "Transformation modifier", 0.16, EntityAttributeModifier.Operation.ADDITION);

    @Override
    public void onAdded(PlayerEntity entity) {
        if (entity instanceof WerepyreAccessor) {
            int variant = 0;
            if (entity.getRandom().nextInt(8192) != 0) {
                variant = 1 + entity.getRandom().nextInt(WerepyreEntity.getVariantsStatic() - 1);
            }
            ((WerepyreAccessor) entity).setWerepyreVariant(variant);
        }
    }

    public static void handleStats(PlayerEntity player, boolean alternateForm, boolean hasWerepyrePledge) {
        EntityAttributeInstance attackDamageAttribute = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        EntityAttributeInstance armorToughnessAttribute = player.getAttributeInstance(EntityAttributes.GENERIC_ARMOR_TOUGHNESS);
        EntityAttributeInstance movementSpeedAttribute = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        boolean shouldHave = alternateForm && hasWerepyrePledge;
        if (shouldHave && !attackDamageAttribute.hasModifier(WEREPYRE_ATTACK_DAMAGE_MODIFIER_1)) {
            attackDamageAttribute.addPersistentModifier(WEREPYRE_ATTACK_DAMAGE_MODIFIER_1);
            armorToughnessAttribute.addPersistentModifier(WEREPYRE_ARMOR_TOUGHNESS_MODIFIER_1);
            movementSpeedAttribute.addPersistentModifier(WEREPYRE_MOVEMENT_SPEED_MODIFIER_1);
        }
        else if (!shouldHave && attackDamageAttribute.hasModifier(WEREPYRE_ATTACK_DAMAGE_MODIFIER_1)) {
            attackDamageAttribute.removeModifier(WEREPYRE_ATTACK_DAMAGE_MODIFIER_1);
            armorToughnessAttribute.removeModifier(WEREPYRE_ARMOR_TOUGHNESS_MODIFIER_1);
            movementSpeedAttribute.removeModifier(WEREPYRE_MOVEMENT_SPEED_MODIFIER_1);
        }
    }
}
