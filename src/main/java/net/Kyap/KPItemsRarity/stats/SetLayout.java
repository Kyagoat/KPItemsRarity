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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Comment se présentent les pièces d'une panoplie : dans quel ordre, dans quelles cases (les pièces "au choix" —
 * épée ou hache, deux casques — partagent une case), combien on peut en porter à la fois, et son palier.
 * Calculé une fois par panoplie, et refait quand les données changent (/reload, connexion à un serveur).
 */
public final class SetLayout {
    /** Une pièce : son identifiant "mod:objet", l'objet à montrer, et le genre de case qu'elle occupe. */
    public record Piece(String id, ItemStack stack, String slot) {}

    private static final SetLayout EMPTY = new SetLayout(List.of());
    /** Panoplie -> sa disposition, et la liste de pièces des données d'où elle a été calculée. */
    private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();

    private record Cached(List<String> source, SetLayout layout) {}

    private final List<Piece> pieces;
    private final List<List<Piece>> slots;
    private final Map<String, List<Piece>> bySlot;
    private final int wearable;
    private final int tier;
    private final int firstTier;

    /** La disposition d'une panoplie (vide si elle n'a aucune pièce). */
    public static SetLayout of(String setId) {
        List<String> ids = RarityData.setPieces(setId);
        Cached cached = CACHE.get(setId);
        if (cached != null && cached.source() == ids) return cached.layout();
        SetLayout layout = ids.isEmpty() ? EMPTY : new SetLayout(ids);
        CACHE.put(setId, new Cached(ids, layout));
        return layout;
    }

    private SetLayout(List<String> ids) {
        List<Piece> pieces = new ArrayList<>();
        for (String id : ids) {
            ResourceLocation item = ResourceLocation.tryParse(id);
            if (item == null) continue;
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(item));
            pieces.add(new Piece(id, stack, slotOf(id, stack)));
        }
        this.pieces = List.copyOf(sorted(pieces));
        this.bySlot = new LinkedHashMap<>();
        for (Piece piece : this.pieces) bySlot.computeIfAbsent(piece.slot(), slot -> new ArrayList<>()).add(piece);
        this.slots = groupBySlot();
        int wearable = 0;
        for (var slot : bySlot.entrySet()) wearable += Math.min(slot.getValue().size(), capacity(slot.getKey()));
        this.wearable = wearable;
        int tier = 1, firstTier = -1;
        for (Piece piece : this.pieces) {
            ItemDefinition def = RarityData.itemDef(piece.stack());
            if (def == null) continue;
            tier = Math.max(tier, def.tier);
            if (firstTier < 0) firstTier = def.tier;
        }
        this.tier = tier;
        this.firstTier = firstTier;
    }

    /** Les pièces dans l'ordre d'affichage. */
    public List<Piece> pieces() {
        return pieces;
    }

    /** Une case par emplacement ; les pièces au choix d'un emplacement unique y sont réunies, les anneaux restent séparés. */
    public List<List<Piece>> slots() {
        return slots;
    }

    /** Combien de pièces on peut porter en même temps (le "total" de "2/5"). */
    public int wearable() {
        return wearable;
    }

    /** Le palier de la panoplie : le plus haut de ses pièces. */
    public int tier() {
        return tier;
    }

    /** Le palier de la première pièce qui a une définition (sous-titre de la carte), ou -1 s'il n'y en a pas. */
    public int firstTier() {
        return firstTier;
    }

    /** Les pièces qui se disputent la même case que celle-ci (sans elle) ; vide si elle a sa case pour elle seule. */
    public List<Piece> alternatives(String pieceId) {
        for (List<Piece> sameSlot : bySlot.values()) {
            if (sameSlot.size() <= capacity(sameSlot.getFirst().slot())) continue;
            List<Piece> others = new ArrayList<>(sameSlot);
            if (others.removeIf(piece -> piece.id().equals(pieceId))) return others;
        }
        return List.of();
    }

    @Nullable
    public Piece piece(String pieceId) {
        for (Piece piece : pieces) if (piece.id().equals(pieceId)) return piece;
        return null;
    }

    /** Noms des pièces séparés par " / " (pour "au choix avec ..."). */
    public static Component names(List<Piece> pieces) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < pieces.size(); i++) {
            if (i > 0) out.append(" / ");
            out.append(pieces.get(i).stack().getHoverName());
        }
        return out;
    }

    // ---------------------------------------------------------------- Calcul

    private List<List<Piece>> groupBySlot() {
        List<List<Piece>> groups = new ArrayList<>();
        Map<String, List<Piece>> shared = new HashMap<>();
        for (Piece piece : pieces) {
            boolean choice = capacity(piece.slot()) == 1 && bySlot.get(piece.slot()).size() > 1;
            if (!choice) {
                groups.add(List.of(piece));
                continue;
            }
            List<Piece> group = shared.get(piece.slot());
            if (group == null) {
                group = new ArrayList<>();
                shared.put(piece.slot(), group);
                groups.add(group);
            }
            group.add(piece);
        }
        return groups;
    }

    /** Le genre de case : type d'armure, case d'accessoire, main gauche ou main droite. */
    private static String slotOf(String id, ItemStack stack) {
        if (stack.getItem() instanceof ArmorItem armor) return armor.getType().getName();
        ItemDefinition def = RarityData.itemDef(stack);
        if (def != null && def.slot != null && GearSlots.ACCESSORY_SLOTS.contains(def.slot)) return def.slot;
        if (stack.isEmpty()) return id; // objet d'un mod absent : une case à lui
        return GearSlots.slotGroupFor(stack) == EquipmentSlotGroup.OFFHAND ? "offhand" : "mainhand";
    }

    /** Nombre de cases de ce genre : deux anneaux, une case pour tout le reste. */
    private static int capacity(String slot) {
        return "ring".equals(slot) ? 2 : 1;
    }

    /**
     * Armes (corps à corps, puis arcs), main gauche, accessoires, puis bottes, jambières, plastron, casque. Les pièces
     * d'une même case restent côte à côte, au rang de la mieux placée.
     */
    private static List<Piece> sorted(List<Piece> pieces) {
        Map<String, Integer> slotRank = new HashMap<>();
        for (Piece piece : pieces) slotRank.merge(piece.slot(), rank(piece.stack()), Math::min);
        List<Piece> sorted = new ArrayList<>(pieces);
        sorted.sort(Comparator.<Piece>comparingInt(piece -> slotRank.get(piece.slot()))
                .thenComparing(Piece::slot)
                .thenComparingInt(piece -> rank(piece.stack())));
        return sorted;
    }

    private static int rank(ItemStack stack) {
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
}
