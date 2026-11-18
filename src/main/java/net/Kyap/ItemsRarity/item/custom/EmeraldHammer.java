package net.Kyap.ItemsRarity.item.custom;

import net.Kyap.ItemsRarity.item.ModToolTiers;
import net.Kyap.ItemsRarity.util.ModRarities.ModRarity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * Marteau d'émeraude : clic droit sur l'enclume améliorée pour forger.
 * Chaque forge lui retire 1 point de durabilité (puits de ressources).
 */
public class EmeraldHammer extends TieredItem {
    private static final double ATTACK_DAMAGE = 10.0;
    private static final double ATTACK_SPEED = -3.4;

    public EmeraldHammer() {
        super(ModToolTiers.EMERALD, new Item.Properties()
                .stacksTo(1)
                .durability(ModToolTiers.EMERALD.getUses())
                .rarity(ModRarity.EPIC.getRarity())
                .fireResistant()
                .attributes(createAttributes()));
    }

    private static ItemAttributeModifiers createAttributes() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(BASE_ATTACK_DAMAGE_ID, ATTACK_DAMAGE, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(BASE_ATTACK_SPEED_ID, ATTACK_SPEED, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }
}
