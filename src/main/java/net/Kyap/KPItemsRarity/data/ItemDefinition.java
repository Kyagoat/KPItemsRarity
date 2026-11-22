package net.Kyap.KPItemsRarity.data;

import net.Kyap.KPItemsRarity.rarity.ModRarity;
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
 *   "set": "kpitemsrarity:iron",
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

    private transient ModRarity minimumRarity = ModRarity.COMMON;

    /** Complète la définition après lecture de son fichier (champs absents ou vides, rareté). */
    void resolve(String file) {
        id = file;
        if (items == null) items = List.of();
        if (stats == null) stats = new LinkedHashMap<>();
        if (passives == null) passives = List.of();
        if (conditions == null) conditions = new LinkedHashMap<>();
        ModRarity parsed = ModRarity.fromId(rarity);
        minimumRarity = parsed != null ? parsed : ModRarity.COMMON;
    }

    public boolean hasConditions() {
        return !conditions.isEmpty();
    }

    /** La rareté la plus basse que l'objet peut avoir (champ "rarity"). */
    public ModRarity minimumRarity() {
        return minimumRarity;
    }

    /** La fourchette écrite dans le fichier ([min, max] ou [valeur]), remise dans l'ordre ; null si la stat n'y est pas. */
    @Nullable
    public RollRange range(String stat) {
        int[] r = stats.get(stat);
        if (r == null || r.length == 0) return null;
        int a = r[0], b = r.length > 1 ? r[1] : r[0];
        return new RollRange(Math.min(a, b), Math.max(a, b));
    }

    public boolean hasSet() {
        return set != null && !set.isBlank();
    }
}
