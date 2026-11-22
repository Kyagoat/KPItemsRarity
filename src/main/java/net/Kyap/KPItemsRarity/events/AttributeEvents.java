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

import java.util.function.BiConsumer;

/**
 * Transforme les jets d'un objet (composant kpitemsrarity:stats) en vrais modificateurs d'attributs, recalculés
 * à chaque fois que le jeu les demande : rééquilibrer une stat dans les données change aussi les vieux objets.
 * Identifiant stable par stat ET par emplacement : kpitemsrarity:stat/<stat>/<emplacement>.
 */
@EventBusSubscriber(modid = KPItemsRarity.MOD_ID)
public final class AttributeEvents {
    private AttributeEvents() {}

    public static final String STAT_PREFIX = "stat/";

    @SubscribeEvent
    public static void onItemAttributes(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        ItemStats stats = stack.get(ModDataComponents.STATS);
        // Un accessoire ne donne rien en main ni en armure : ses stats passent par sa case Curios (CuriosCompat).
        if (stats == null || GearSlots.isAccessory(stack)) return;
        EquipmentSlotGroup group = GearSlots.slotGroupFor(stack);
        stats.pointsWithExo().forEach((statId, points) -> {
            StatDefinition stat = RarityData.stat(statId);
            if (stat == null) return;
            stat.attributeHolder().ifPresent(attribute -> {
                if (stat.base) replaceBaseValue(event, stat, attribute, points, group);
                else if (points != 0) event.addModifier(attribute, new AttributeModifier(modifierId(statId, group), points * stat.scale, stat.parsedOperation()), group);
            });
        });
    }

    /**
     * La ligne de base (Dégâts) remplace la valeur vanilla de l'objet, sous l'identifiant vanilla : les autres mods
     * (Better Combat...) la voient comme les dégâts de l'arme.
     */
    private static void replaceBaseValue(ItemAttributeModifierEvent event, StatDefinition stat, Holder<Attribute> attribute,
                                         int points, EquipmentSlotGroup group) {
        ResourceLocation baseId = baseModifierId(attribute);
        event.removeModifier(attribute, baseId);
        event.addModifier(attribute, new AttributeModifier(baseId, points * stat.scale + stat.offset, AttributeModifier.Operation.ADD_VALUE), group);
    }

    /** Les modificateurs d'un accessoire porté dans une case Curios (hors ligne de base), identifiés par sa case. */
    public static void forEachStatModifier(ItemStack stack, String slotKey, BiConsumer<Holder<Attribute>, AttributeModifier> sink) {
        ItemStats stats = stack.get(ModDataComponents.STATS);
        if (stats == null) return;
        stats.pointsWithExo().forEach((statId, points) -> {
            StatDefinition stat = RarityData.stat(statId);
            if (stat == null || stat.base || points == 0) return;
            stat.attributeHolder().ifPresent(attribute -> sink.accept(attribute,
                    new AttributeModifier(KPItemsRarity.id(STAT_PREFIX + statId + "/" + slotKey), points * stat.scale, stat.parsedOperation())));
        });
    }

    /** Identifiant vanilla de la valeur de base d'une arme pour cet attribut (dégâts, vitesse). */
    public static ResourceLocation baseModifierId(Holder<Attribute> attribute) {
        return attribute.is(Attributes.ATTACK_SPEED) ? Item.BASE_ATTACK_SPEED_ID : Item.BASE_ATTACK_DAMAGE_ID;
    }

    public static ResourceLocation modifierId(String statId, EquipmentSlotGroup group) {
        return KPItemsRarity.id(STAT_PREFIX + statId + "/" + group.getSerializedName());
    }

    /** Vrai pour les modificateurs posés par ce mod (jets, panoplies, conditions) : l'infobulle les montre à sa façon. */
    public static boolean isOurModifier(ResourceLocation id) {
        if (!id.getNamespace().equals(KPItemsRarity.MOD_ID)) return false;
        String path = id.getPath();
        return path.startsWith(STAT_PREFIX) || path.startsWith(SetBonusEvents.SET_PREFIX) || path.startsWith(SetBonusEvents.COND_PREFIX);
    }
}
