package net.Kyap.ItemsRarity.data;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Map;
import java.util.TreeMap;

/**
 * Une panoplie : data/<ns>/kp_sets/<nom>.json
 * {
 *   "bonuses": {
 *     "2": { "vitality": 2 },
 *     "5": { "vitality": 6, "guard": 15 }
 *   }
 * }
 * Chaque palier donne le bonus TOTAL (comme Dofus) : avec 4 pièces on a le bonus "4" (ou le plus haut ≤ 4).
 * Les pièces sont tous les objets dont la définition (kp_items) pointe vers cette panoplie.
 * Nom affiché : clé de traduction set.<ns>.<nom>.
 */
public class SetDefinition {
    public transient String id;

    public Map<String, Map<String, Integer>> bonuses = Map.of();
    /** Lore de la carte (clé de traduction ou texte). Par défaut : lore.set.<mod>.<panoplie> (voir LoreText). */
    public String lore;
    /** Passifs débloqués à partir d'un nombre de pièces : { "5": ["kpsets:toile"] }. */
    public Map<String, java.util.List<String>> passives = Map.of();

    /** Passifs actifs avec n pièces (tous les paliers atteints, cumulés). */
    public java.util.List<String> activePassives(int pieces) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (passives == null) return out;
        passives.forEach((k, ids) -> {
            try {
                if (Integer.parseInt(k.trim()) <= pieces && ids != null) out.addAll(ids);
            } catch (NumberFormatException ignored) {
            }
        });
        return out;
    }

    /** Palier (nombre de pièces) qui débloque ce passif, ou -1. */
    public int passiveThreshold(String passiveId) {
        if (passives == null) return -1;
        for (var e : passives.entrySet()) {
            if (e.getValue() != null && e.getValue().contains(passiveId)) {
                try {
                    return Integer.parseInt(e.getKey().trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return -1;
    }

    /** Paliers triés : nombre de pièces -> bonus. */
    public TreeMap<Integer, Map<String, Integer>> thresholds() {
        TreeMap<Integer, Map<String, Integer>> sorted = new TreeMap<>();
        if (bonuses != null) {
            bonuses.forEach((k, v) -> {
                try {
                    sorted.put(Integer.parseInt(k.trim()), v);
                } catch (NumberFormatException ignored) {
                }
            });
        }
        return sorted;
    }

    /** Bonus actif pour n pièces (le palier le plus haut atteint), vide sinon. */
    public Map<String, Integer> activeBonus(int pieces) {
        var entry = thresholds().floorEntry(pieces);
        return entry == null ? Map.of() : entry.getValue();
    }

    public MutableComponent name() {
        String[] parts = id.split(":", 2);
        return parts.length == 2
                ? Component.translatable("set." + parts[0] + "." + parts[1])
                : Component.translatable("set." + id);
    }
}
