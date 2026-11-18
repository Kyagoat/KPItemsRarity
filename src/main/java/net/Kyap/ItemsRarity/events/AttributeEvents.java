package net.Kyap.ItemsRarity.events;

import net.Kyap.ItemsRarity.ItemsRarity;
import net.Kyap.ItemsRarity.component.ItemStats;
import net.Kyap.ItemsRarity.data.RarityData;
import net.Kyap.ItemsRarity.data.StatDefinition;
import net.Kyap.ItemsRarity.util.StatEngine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Transforme les jets (composant itemsrarity:stats) en vrais modificateurs d'attributs.
 * ID stable par stat ET par emplacement : itemsrarity:stat/<stat>/<emplacement>.
 * Le tooltip vanilla de ces modificateurs est masqué (TooltipHandler affiche les jets à la place).
 */
@EventBusSubscriber(modid = ItemsRarity.MOD_ID)
public final class AttributeEvents {
    private AttributeEvents() {}

    public static final String STAT_PREFIX = "stat/";

    @SubscribeEvent
    public static void onItemAttributes(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        ItemStats stats = stack.get(net.Kyap.ItemsRarity.component.ModDataComponents.STATS);
        if (stats == null) return;
        // Accessoire : rien en main ou en armure, ses stats passent par la case Curios (CuriosCompat)
        if (StatEngine.isAccessory(stack)) return;

        Map<String, Integer> points = new LinkedHashMap<>(stats.lines());
        if (stats.hasExo()) points.merge(stats.exoStat(), stats.exoValue(), Integer::sum);

        EquipmentSlotGroup group = StatEngine.slotGroupFor(stack);
        points.forEach((statId, value) -> {
            StatDefinition def = RarityData.stat(statId);
            if (def == null) return;
            def.attributeHolder().ifPresent(attribute -> {
                if (def.base) {
                    // Ligne de base (Dégâts) : remplace la valeur vanilla de l'objet, avec l'ID vanilla,
                    // pour que les autres mods (Better Combat...) la voient comme les dégâts de l'arme.
                    ResourceLocation baseId = baseModifierId(attribute);
                    event.removeModifier(attribute, baseId);
                    event.addModifier(attribute,
                            new AttributeModifier(baseId, value * def.scale + def.offset, AttributeModifier.Operation.ADD_VALUE),
                            group);
                    return;
                }
                if (value == 0) return;
                event.addModifier(attribute,
                        new AttributeModifier(modifierId(statId, group), value * def.scale, def.parsedOperation()),
                        group);
            });
        });
    }

    /**
     * Modificateurs d'un objet à jets pour un emplacement donné (hors ligne de base) :
     * utilisé pour les accessoires portés dans une case Curios.
     */
    public static void forEachStatModifier(ItemStack stack, String slotKey,
                                           java.util.function.BiConsumer<net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>, AttributeModifier> sink) {
        ItemStats stats = stack.get(net.Kyap.ItemsRarity.component.ModDataComponents.STATS);
        if (stats == null) return;
        Map<String, Integer> points = new LinkedHashMap<>(stats.lines());
        if (stats.hasExo()) points.merge(stats.exoStat(), stats.exoValue(), Integer::sum);
        points.forEach((statId, value) -> {
            StatDefinition def = RarityData.stat(statId);
            if (def == null || def.base || value == 0) return;
            def.attributeHolder().ifPresent(attribute -> sink.accept(attribute,
                    new AttributeModifier(ItemsRarity.id(STAT_PREFIX + statId + "/" + slotKey), value * def.scale, def.parsedOperation())));
        });
    }

    /** ID vanilla de la valeur de base d'une arme pour cet attribut (dégâts, vitesse...). */
    public static ResourceLocation baseModifierId(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        if (attribute.is(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED)) return net.minecraft.world.item.Item.BASE_ATTACK_SPEED_ID;
        return net.minecraft.world.item.Item.BASE_ATTACK_DAMAGE_ID;
    }

    public static ResourceLocation modifierId(String statId, EquipmentSlotGroup group) {
        return ItemsRarity.id(STAT_PREFIX + statId + "/" + group.getSerializedName());
    }

    /** Vrai pour les modificateurs posés par ce mod (jets et panoplies) : à cacher du tooltip vanilla. */
    public static boolean isOurModifier(ResourceLocation id) {
        return id.getNamespace().equals(ItemsRarity.MOD_ID)
                && (id.getPath().startsWith(STAT_PREFIX) || id.getPath().startsWith(SetBonusEvents.SET_PREFIX)
                || id.getPath().startsWith(SetBonusEvents.COND_PREFIX));
    }
}
