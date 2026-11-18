package net.Kyap.ItemsRarity.data;

import net.Kyap.ItemsRarity.util.ModRarities.ModRarity;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Les lignes de stats d'un objet : data/<ns>/kp_items/<nom>.json
 * {
 *   "items": ["minecraft:iron_sword"],
 *   "tier": 2,
 *   "rarity": "common",          (rareté MINIMUM ; la vraie dépend des jets et des crans de forge)
 *   "set": "itemsrarity:iron_guard",
 *   "stats": { "strength": [6, 10], "critical": [1, 2] },
 *   "conditions": { "max_armor": 8 }   (facultatif)
 * }
 */
public class ItemDefinition {
    public transient String id;

    public List<String> items = List.of();
    public int tier = 1;
    public String rarity = "common";
    public String set;
    /** Emplacement d'accessoire à venir ("amulet", "ring", "relic"), vide pour l'équipement vanilla. */
    public String slot;
    /** Petite description affichée dans l'infobulle (clé de traduction ou texte). Par défaut : lore.<mod>.<objet>. */
    public String lore;
    public Map<String, int[]> stats = new LinkedHashMap<>();
    /** Passifs portés par l'objet (ids de kp_passives). */
    public List<String> passives = List.of();
    /**
     * Conditions d'équipement : l'objet ne donne ses stats et ses passifs que si elles sont remplies.
     * "max_armor" (Armure totale), "max_health" (Vie max, en PV), "max_healing" (Soins, en %).
     */
    public Map<String, Double> conditions = new LinkedHashMap<>();

    public boolean hasConditions() {
        return conditions != null && !conditions.isEmpty();
    }

    public ModRarity parsedRarity() {
        ModRarity r = ModRarity.fromId(rarity);
        return r != null ? r : ModRarity.COMMON;
    }

    @Nullable
    public int[] range(String stat) {
        int[] r = stats.get(stat);
        if (r == null || r.length == 0) return null;
        int a = r[0], b = r.length > 1 ? r[1] : r[0];
        return new int[]{Math.min(a, b), Math.max(a, b)};
    }

    public boolean hasSet() {
        return set != null && !set.isBlank();
    }
}
