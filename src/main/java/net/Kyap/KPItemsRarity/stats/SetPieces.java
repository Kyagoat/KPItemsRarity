package net.Kyap.KPItemsRarity.stats;

import net.Kyap.KPItemsRarity.data.ItemDefinition;
import net.Kyap.KPItemsRarity.data.RarityData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.neoforged.neoforge.common.ItemAbilities;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Les pièces d'une panoplie et les cases qu'elles occupent : combien on peut en porter à la fois, lesquelles
 * sont "au choix" (épée ou hache, deux casques...), dans quel ordre les montrer, et le palier de la panoplie.
 */
public final class SetPieces {
    private SetPieces() {}

    /**
     * Nombre de pièces portables en même temps, affiché "n/total" : deux pièces qui prennent la même case
     * (épée et hache, deux casques...) n'en comptent qu'une ; les anneaux ont deux cases.
     */
    public static int pieceCount(String setId) {
        Map<String, Integer> perKind = new HashMap<>();
        for (String id : RarityData.setPieces(setId)) perKind.merge(slotKind(id), 1, Integer::sum);
        int total = 0;
        for (var e : perKind.entrySet()) total += Math.min(e.getValue(), capacity(e.getKey()));
        return total;
    }

    /** Case occupée par une pièce : type d'armure, main droite, main gauche ou case d'accessoire. */
    public static String slotKind(String id) {
        ItemStack stack = stackOf(id);
        if (stack == null) return id;
        if (stack.getItem() instanceof ArmorItem armor) return armor.getType().getName();
        ItemDefinition def = RarityData.itemDef(stack);
        if (def != null && def.slot != null && GearSlots.ACCESSORY_SLOTS.contains(def.slot)) return def.slot;
        return GearSlots.slotGroupFor(stack) == EquipmentSlotGroup.OFFHAND ? "offhand" : "mainhand";
    }

    /** Nombre de cases de ce type (deux anneaux, une case pour tout le reste). */
    public static int capacity(String kind) {
        return "ring".equals(kind) ? 2 : 1;
    }

    /**
     * Pièces de la même panoplie qui se disputent la même case que celle-ci (sans elle) : on n'en porte
     * qu'une à la fois. Vide si la pièce a sa case pour elle seule.
     */
    public static List<String> alternatives(String setId, String id) {
        String kind = slotKind(id);
        List<String> same = new ArrayList<>();
        for (String other : sortedPieces(setId)) if (slotKind(other).equals(kind)) same.add(other);
        if (same.size() <= capacity(kind)) return List.of();
        same.remove(id);
        return same;
    }

    /**
     * Cases à montrer : chaque pièce seule, sauf les pièces au choix d'un emplacement unique
     * (épée / hache, deux casques...) réunies dans une même case. Les anneaux (deux cases) restent séparés.
     */
    public static List<List<String>> slotGroups(String setId, List<String> pieceIds) {
        List<List<String>> groups = new ArrayList<>();
        Map<String, List<String>> shared = new HashMap<>();
        for (String id : pieceIds) {
            String kind = slotKind(id);
            if (capacity(kind) == 1 && !alternatives(setId, id).isEmpty()) {
                List<String> group = shared.get(kind);
                if (group == null) {
                    group = new ArrayList<>();
                    shared.put(kind, group);
                    groups.add(group);
                }
                group.add(id);
            } else {
                groups.add(new ArrayList<>(List.of(id)));
            }
        }
        return groups;
    }

    /** Noms des pièces séparés par " / " (pour "au choix avec ..."). */
    public static Component names(List<String> ids) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < ids.size(); i++) {
            ItemStack stack = stackOf(ids.get(i));
            if (stack == null) continue;
            if (i > 0) out.append(" / ");
            out.append(stack.getHoverName());
        }
        return out;
    }

    /**
     * Pièces dans un ordre logique : armes (corps à corps, puis arcs), main gauche, accessoires,
     * puis bottes, jambières, plastron, casque. Les pièces qui prennent la même case restent côte à côte.
     */
    public static List<String> sortedPieces(String setId) {
        List<String> ids = new ArrayList<>(RarityData.setPieces(setId));
        Map<String, Integer> kindRank = new HashMap<>();
        for (String id : ids) kindRank.merge(slotKind(id), pieceRank(id), Math::min);
        ids.sort(Comparator.<String>comparingInt(id -> kindRank.get(slotKind(id)))
                .thenComparing(SetPieces::slotKind)
                .thenComparingInt(SetPieces::pieceRank));
        return ids;
    }

    private static int pieceRank(String id) {
        ItemStack stack = stackOf(id);
        if (stack == null) return 99;
        Item item = stack.getItem();
        if (item instanceof ArmorItem armor) {
            return switch (armor.getType()) {
                case BOOTS -> 10;
                case LEGGINGS -> 11;
                case CHESTPLATE -> 12;
                case HELMET -> 13;
                default -> 14;
            };
        }
        if (item instanceof BowItem || item instanceof CrossbowItem) return 1;
        if (item instanceof ShieldItem || stack.canPerformAction(ItemAbilities.SHIELD_BLOCK)) return 2;
        ItemDefinition def = RarityData.itemDef(stack);
        if (def != null && def.slot != null && !def.slot.isBlank()) {
            return switch (def.slot) {
                case "amulet" -> 3;
                case "ring" -> 4;
                case "relic" -> 5;
                default -> 6;
            };
        }
        if (item instanceof SwordItem || item instanceof AxeItem || item instanceof MaceItem || item instanceof TridentItem) return 0;
        return 7; // outils et autres
    }

    /** Palier d'une panoplie = palier le plus haut de ses pièces. */
    public static int tierOfSet(String setId) {
        return tierOf(sortedPieces(setId));
    }

    public static int tierOf(List<String> pieceIds) {
        int tier = 1;
        for (String id : pieceIds) {
            ItemStack stack = stackOf(id);
            ItemDefinition def = stack != null ? RarityData.itemDef(stack) : null;
            if (def != null) tier = Math.max(tier, def.tier);
        }
        return tier;
    }

    /** Palier de la première pièce qui a une définition (sous-titre de la carte), ou -1 s'il n'y en a pas. */
    public static int firstTier(List<String> pieceIds) {
        for (String id : pieceIds) {
            ItemStack stack = stackOf(id);
            ItemDefinition def = stack != null ? RarityData.itemDef(stack) : null;
            if (def != null) return def.tier;
        }
        return -1;
    }

    /** L'objet d'un identifiant "mod:objet", ou null si l'identifiant est invalide. */
    public static ItemStack stackOf(String id) {
        ResourceLocation loc = ResourceLocation.tryParse(id);
        return loc == null ? null : new ItemStack(BuiltInRegistries.ITEM.get(loc));
    }
}
