package net.Kyap.KPItemsRarity.events;

import net.Kyap.KPItemsRarity.KPItemsRarity;
import net.Kyap.KPItemsRarity.component.ItemStats;
import net.Kyap.KPItemsRarity.component.ModDataComponents;
import net.Kyap.KPItemsRarity.data.RarityData;
import net.Kyap.KPItemsRarity.data.StatDefinition;
import net.Kyap.KPItemsRarity.stats.GearSlots;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Transforme les jets (composant kpitemsrarity:stats) en vrais modificateurs d'attributs.
 * ID stable par stat ET par emplacement : kpitemsrarity:stat/<stat>/<emplacement>.
 * Le tooltip vanilla de ces modificateurs est masqué (TooltipHandler affiche les jets à la place).
 */
@EventBusSubscriber(modid = KPItemsRarity.MOD_ID)
public final class AttributeEvents {
    private AttributeEvents() {}

    public static final String STAT_PREFIX = "stat/";

    @SubscribeEvent
    public static void onItemAttributes(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        ItemStats stats = stack.get(ModDataComponents.STATS);
        if (stats == null) return;
        // Accessoire : rien en main ou en armure, ses stats passent par la case Curios (CuriosCompat)
        if (GearSlots.isAccessory(stack)) return;

        Map<String, Integer> points = new LinkedHashMap<>(stats.lines());
        if (stats.hasExo()) points.merge(stats.exoStat(), stats.exoValue(), Integer::sum);

        EquipmentSlotGroup group = GearSlots.slotGroupFor(stack);
        points.forEach((statId, value) -> {
            StatDefinition def = RarityData.stat(statId);
            if (def != null) def.attributeHolder().ifPresent(attribute -> addStat(event, statId, def, attribute, value, group));
        });
    }

    private static void addStat(ItemAttributeModifierEvent event, String statId, StatDefinition def, Holder<Attribute> attribute,
                                int value, EquipmentSlotGroup group) {
        if (def.base) {
            // Ligne de base (Dégâts) : remplace la valeur vanilla de l'objet, avec l'ID vanilla,
            // pour que les autres mods (Better Combat...) la voient comme les dégâts de l'arme.
            ResourceLocation baseId = baseModifierId(attribute);
            event.removeModifier(attribute, baseId);
            event.addModifier(attribute, new AttributeModifier(baseId, value * def.scale + def.offset, AttributeModifier.Operation.ADD_VALUE), group);
        } else if (value != 0) {
            event.addModifier(attribute, new AttributeModifier(modifierId(statId, group), value * def.scale, def.parsedOperation()), group);
        }
    }

    /**
     * Modificateurs d'un objet à jets pour un emplacement donné (hors ligne de base) :
     * utilisé pour les accessoires portés dans une case Curios.
     */
    public static void forEachStatModifier(ItemStack stack, String slotKey,
                                           BiConsumer<Holder<Attribute>, AttributeModifier> sink) {
        ItemStats stats = stack.get(ModDataComponents.STATS);
        if (stats == null) return;
        Map<String, Integer> points = new LinkedHashMap<>(stats.lines());
        if (stats.hasExo()) points.merge(stats.exoStat(), stats.exoValue(), Integer::sum);
        points.forEach((statId, value) -> {
            StatDefinition def = RarityData.stat(statId);
            if (def == null || def.base || value == 0) return;
            def.attributeHolder().ifPresent(attribute -> sink.accept(attribute,
                    new AttributeModifier(KPItemsRarity.id(STAT_PREFIX + statId + "/" + slotKey), value * def.scale, def.parsedOperation())));
        });
    }

    /** ID vanilla de la valeur de base d'une arme pour cet attribut (dégâts, vitesse...). */
    public static ResourceLocation baseModifierId(Holder<Attribute> attribute) {
        if (attribute.is(Attributes.ATTACK_SPEED)) return Item.BASE_ATTACK_SPEED_ID;
        return Item.BASE_ATTACK_DAMAGE_ID;
    }

    public static ResourceLocation modifierId(String statId, EquipmentSlotGroup group) {
        return KPItemsRarity.id(STAT_PREFIX + statId + "/" + group.getSerializedName());
    }

    /** Vrai pour les modificateurs posés par ce mod (jets et panoplies) : à cacher du tooltip vanilla. */
    public static boolean isOurModifier(ResourceLocation id) {
        return id.getNamespace().equals(KPItemsRarity.MOD_ID)
                && (id.getPath().startsWith(STAT_PREFIX) || id.getPath().startsWith(SetBonusEvents.SET_PREFIX)
                || id.getPath().startsWith(SetBonusEvents.COND_PREFIX));
    }
}
