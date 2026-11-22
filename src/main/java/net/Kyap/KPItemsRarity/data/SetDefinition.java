package net.Kyap.KPItemsRarity.data;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
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
    public Map<String, List<String>> passives = Map.of();

    /** Les paliers par nombre de pièces, lus une fois au chargement (les clés du fichier sont du texte). */
    private transient NavigableMap<Integer, Map<String, Integer>> bonusTiers = new TreeMap<>();
    private transient NavigableMap<Integer, List<String>> passiveTiers = new TreeMap<>();

    /** Complète la panoplie après lecture de son fichier. */
    void resolve(String file) {
        id = file;
        bonusTiers = byPieceCount(bonuses);
        passiveTiers = byPieceCount(passives);
    }

    /** { "2": x, "5": y } -> 2 -> x, 5 -> y ; une clé qui n'est pas un nombre est ignorée. */
    private static <V> NavigableMap<Integer, V> byPieceCount(@Nullable Map<String, V> byText) {
        TreeMap<Integer, V> sorted = new TreeMap<>();
        if (byText == null) return sorted;
        byText.forEach((pieces, value) -> {
            try {
                if (value != null) sorted.put(Integer.parseInt(pieces.trim()), value);
            } catch (NumberFormatException ignored) {
                // clé mal écrite : palier ignoré
            }
        });
        return Collections.unmodifiableNavigableMap(sorted);
    }

    /** Paliers triés : nombre de pièces -> bonus TOTAL de ce palier. */
    public NavigableMap<Integer, Map<String, Integer>> thresholds() {
        return bonusTiers;
    }

    /** Bonus actif avec n pièces (celui du plus haut palier atteint), vide sinon. */
    public Map<String, Integer> activeBonus(int pieces) {
        var tier = bonusTiers.floorEntry(pieces);
        return tier == null ? Map.of() : tier.getValue();
    }

    /** Paliers des passifs : nombre de pièces -> passifs débloqués à ce palier. */
    public NavigableMap<Integer, List<String>> passiveTiers() {
        return passiveTiers;
    }

    /** Passifs actifs avec n pièces : ceux de tous les paliers atteints. */
    public List<String> activePassives(int pieces) {
        List<String> out = new ArrayList<>();
        passiveTiers.headMap(pieces, true).values().forEach(out::addAll);
        return out;
    }

    public MutableComponent name() {
        String[] parts = id.split(":", 2);
        return parts.length == 2
                ? Component.translatable("set." + parts[0] + "." + parts[1])
                : Component.translatable("set." + id);
    }
}
