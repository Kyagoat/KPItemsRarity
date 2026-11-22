package net.Kyap.KPItemsRarity.client.panel;

import net.Kyap.KPItemsRarity.KPItemsRarity;
import net.Kyap.KPItemsRarity.client.tooltip.card.Elem;
import net.Kyap.KPItemsRarity.data.RarityData;
import net.Kyap.KPItemsRarity.data.StatDefinition;
import net.Kyap.KPItemsRarity.events.AttributeEvents;
import net.Kyap.KPItemsRarity.events.SetBonusEvents;
import net.Kyap.KPItemsRarity.stats.EquippedStats;
import net.Kyap.KPItemsRarity.stats.GearSlots;
import net.Kyap.KPItemsRarity.stats.SetLayout;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * D'où viennent les stats de la fiche : une ligne par source (pièce portée, panoplie, accessoire, reste),
 * avec l'icône de l'objet, pour l'infobulle d'une stat.
 */
final class StatSources {
    private StatSources() {}

    private static final int MAX_SOURCES = 8;
    private static final int BONUS = 0x7FE08A, MALUS = 0xFF6B6B;

    /** Une source de points : son nom, combien elle donne, et les icônes à montrer (plusieurs pour une panoplie). */
    private record Source(Component name, int points, List<ItemStack> icons) {}

    /**
     * D'où vient une stat : chaque pièce portée, chaque panoplie, chaque accessoire, puis le reste
     * (effets de potion, autres mods) regroupé. Une ligne par source, la plus forte en premier.
     */
    static List<Elem> sources(LivingEntity player, StatDefinition stat, AttributeInstance instance) {
        List<Source> sources = collect(player, stat, instance);
        if (sources.isEmpty()) {
            return List.of(PanelRows.text(Component.translatable("panel.kpitemsrarity.source.none").withStyle(ChatFormatting.DARK_GRAY)));
        }
        sources.sort((a, b) -> Integer.compare(Math.abs(b.points()), Math.abs(a.points())));
        List<Elem> out = new ArrayList<>();
        for (Source src : sources.subList(0, Math.min(MAX_SOURCES, sources.size()))) out.add(line(stat, src));
        if (sources.size() > MAX_SOURCES) out.add(PanelRows.text(Component.literal("…").withStyle(ChatFormatting.DARK_GRAY)));
        return out;
    }

    /** Range chaque modificateur de l'attribut : pièce portée, panoplie, autre stat du mod (ignorée) ou "autre". */
    private static List<Source> collect(LivingEntity player, StatDefinition stat, AttributeInstance instance) {
        List<Source> sources = new ArrayList<>();
        double other = 0;
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (modifier.operation() != stat.parsedOperation()) continue;
            int points = (int) Math.round(modifier.amount() / stat.scale);
            ResourceLocation id = modifier.id();
            String path = id.getPath();
            boolean ours = id.getNamespace().equals(KPItemsRarity.MOD_ID);
            if (ours && path.startsWith(AttributeEvents.STAT_PREFIX + stat.id + "/")) {
                String slot = path.substring(path.lastIndexOf('/') + 1);
                sources.add(new Source(slotSource(player, slot, path), points, one(slotStack(player, slot))));
            } else if (ours && path.startsWith(SetBonusEvents.SET_PREFIX) && path.endsWith("/" + stat.id)) {
                sources.add(new Source(setSource(player, path), points, setIcons(player, path)));
            } else if (!(ours && path.startsWith(AttributeEvents.STAT_PREFIX))) {
                // (une autre de nos stats sur le même attribut, ex. Force et Dégâts, est comptée avec elle)
                other += modifier.amount();
            }
        }
        int otherPoints = (int) Math.round(other / stat.scale);
        if (otherPoints != 0) sources.add(new Source(Component.translatable("panel.kpitemsrarity.source.other"), otherPoints, List.of()));
        return sources;
    }

    /** "+3  [icône] Casque en bois", vert pour un bonus, rouge pour un malus. */
    private static Elem line(StatDefinition stat, Source src) {
        int color = src.points() < 0 ? MALUS : BONUS;
        Component value = Component.literal(stat.formatValue(src.points(), true)).withStyle(st -> st.withColor(color));
        return new Elem.ItemLine(value, src.icons(), src.name().copy().withStyle(ChatFormatting.GRAY), color);
    }

    private static List<ItemStack> one(ItemStack stack) {
        return stack.isEmpty() ? List.of() : List.of(stack);
    }

    /** L'objet porté dans un groupe d'emplacements ("head", "mainhand"...), ou vide. */
    private static ItemStack slotStack(LivingEntity player, String slot) {
        for (EquipmentSlotGroup group : EquipmentSlotGroup.values()) {
            if (!group.getSerializedName().equals(slot)) continue;
            for (EquipmentSlot equip : EquipmentSlot.values()) {
                if (group.test(equip) && !player.getItemBySlot(equip).isEmpty()) return player.getItemBySlot(equip);
            }
        }
        return ItemStack.EMPTY;
    }

    /** Icônes d'une panoplie : toutes ses pièces portées (équipement puis accessoires), qui défilent. */
    private static List<ItemStack> setIcons(LivingEntity player, String path) {
        List<ItemStack> worn = new ArrayList<>(GearSlots.wornGear(player));
        worn.addAll(GearSlots.wornAccessories(player));
        List<ItemStack> out = new ArrayList<>();
        Set<Item> seen = new HashSet<>();
        for (ItemStack stack : worn) {
            var def = RarityData.itemDef(stack);
            if (def == null || !def.hasSet() || !seen.add(stack.getItem())) continue;
            String key = SetBonusEvents.modifierId(def.set, "x").getPath();
            if (path.startsWith(key.substring(0, key.length() - 1))) out.add(stack);
        }
        return out;
    }

    /** Nom de l'objet porté dans l'emplacement d'où vient un modificateur (casque, main, accessoire Curios...). */
    private static Component slotSource(LivingEntity player, String slot, String path) {
        ItemStack worn = slotStack(player, slot);
        if (!worn.isEmpty()) return worn.getHoverName();
        if (slot.startsWith("curio_") || path.contains("/curio_")) return Component.translatable("panel.kpitemsrarity.source.accessory");
        return Component.literal(slot);
    }

    private static Component setSource(LivingEntity player, String path) {
        // set/<namespace>_<set>/<stat> : on retrouve la panoplie portée qui correspond
        for (var entry : EquippedStats.countSetPieces(player).entrySet()) {
            var set = RarityData.set(entry.getKey());
            String key = SetBonusEvents.modifierId(entry.getKey(), "x").getPath();
            if (set != null && path.startsWith(key.substring(0, key.length() - 1))) {
                return set.name().copy().append(" (" + entry.getValue() + "/" + SetLayout.of(entry.getKey()).wearable() + ")");
            }
        }
        return Component.translatable("panel.kpitemsrarity.source.set");
    }
}
