package net.Kyap.KPItemsRarity.stats;

import net.Kyap.KPItemsRarity.component.ItemStats;
import net.Kyap.KPItemsRarity.component.ModDataComponents;
import net.Kyap.KPItemsRarity.data.ItemDefinition;
import net.Kyap.KPItemsRarity.data.RarityData;
import net.Kyap.KPItemsRarity.data.RollRange;
import net.Kyap.KPItemsRarity.data.StatDefinition;
import net.Kyap.KPItemsRarity.rarity.ItemRarity;
import net.Kyap.KPItemsRarity.rarity.ModRarity;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Les jets d'un objet (composant kpitemsrarity:stats) : les tirer dans leurs fourchettes, les retirer quand
 * la définition change, les monter d'un cran de rareté. Chaque écriture recalcule la rareté de l'objet.
 */
public final class ItemRolls {
    private ItemRolls() {}

    /** Nombre maximum de crans de rareté gagnés à la forge. */
    public static final int MAX_UPGRADES = 2;

    /** L'objet a-t-il une définition (donc des stats possibles) ? */
    public static boolean hasDefinition(ItemStack stack) {
        return RarityData.itemDef(stack) != null;
    }

    @Nullable
    public static ItemStats stats(ItemStack stack) {
        return stack.get(ModDataComponents.STATS);
    }

    public static int upgrades(ItemStack stack) {
        ItemStats stats = stats(stack);
        return stats == null ? 0 : stats.upgrades();
    }

    /**
     * Fourchette d'une ligne en tenant compte des crans de rareté gagnés :
     * à chaque cran, le minimum devient l'ancien maximum et le maximum monte de 10 % (au moins +1).
     * Les lignes de base (Dégâts) et les malus ne bougent pas.
     */
    @Nullable
    public static RollRange range(ItemDefinition def, String statId, int upgrades) {
        RollRange r = def.range(statId);
        if (r == null || upgrades <= 0) return r;
        StatDefinition stat = RarityData.stat(statId);
        if ((stat != null && stat.base) || r.max() <= 0) return r;
        for (int i = 0; i < upgrades; i++) r = r.upgraded();
        return r;
    }

    @Nullable
    public static RollRange range(ItemStack stack, String statId) {
        ItemDefinition def = RarityData.itemDef(stack);
        return def == null ? null : range(def, statId, upgrades(stack));
    }

    /** Donne ses jets à un objet qui n'en a pas encore (craft, drop, coffre, échange...). */
    public static void ensureRolled(ItemStack stack, RandomSource random) {
        if (stack.isEmpty()) return;
        ItemDefinition def = RarityData.itemDef(stack);
        if (def == null) return;
        ItemStats stats = stats(stack);
        // Déjà tiré ET à jour : rien à faire. Si la définition a changé de lignes (rééquilibrage, nouvelle
        // ligne Dégâts...), l'objet est retiré automatiquement, en gardant son exo et ses crans de rareté.
        if (stats != null && stats.lines().keySet().equals(expectedLines(def))) {
            // Objets d'avant la rareté dynamique (ou définition rééquilibrée) : on recalcule la rareté.
            ModRarity expected = ItemRarity.rolled(def, stats);
            if (stack.get(ModDataComponents.RARITY) != expected) stack.set(ModDataComponents.RARITY, expected);
            return;
        }
        roll(stack, def, random);
    }

    private static Set<String> expectedLines(ItemDefinition def) {
        Set<String> keys = new HashSet<>();
        for (String stat : def.stats.keySet()) if (RarityData.stat(stat) != null && def.range(stat) != null) keys.add(stat);
        return keys;
    }

    /** (Re)tire toutes les lignes dans leurs fourchettes. Garde l'exo et les crans de rareté. */
    public static void roll(ItemStack stack, ItemDefinition def, RandomSource random) {
        int upgrades = upgrades(stack);
        store(stack, def, lines(def, upgrades, r -> r.roll(random)), upgrades);
    }

    /** Met toutes les lignes au maximum (commande de test). */
    public static void maxOut(ItemStack stack, ItemDefinition def) {
        int upgrades = upgrades(stack);
        store(stack, def, lines(def, upgrades, RollRange::max), upgrades);
    }

    /** Une valeur par ligne de la définition, choisie dans sa fourchette. Stat d'un mod absent : pas de ligne. */
    private static Map<String, Integer> lines(ItemDefinition def, int upgrades, ToIntFunction<RollRange> pick) {
        Map<String, Integer> lines = new LinkedHashMap<>();
        def.stats.keySet().forEach(stat -> {
            RollRange r = range(def, stat, upgrades);
            if (r != null && RarityData.stat(stat) != null) lines.put(stat, pick.applyAsInt(r));
        });
        return lines;
    }

    /**
     * Monte la rareté d'un cran : chaque ligne passe au maximum de sa fourchette actuelle (sauf si elle
     * le dépasse déjà grâce à la forgemagie), puis la fourchette s'élargit vers le haut.
     * @return false si l'objet est déjà au plafond (2 crans = Mythique).
     */
    public static boolean upgradeRarity(ItemStack stack, RandomSource random) {
        ItemDefinition def = RarityData.itemDef(stack);
        if (def == null) return false;
        ensureRolled(stack, random);
        ItemStats stats = stats(stack);
        int upgrades = stats.upgrades();
        if (upgrades >= MAX_UPGRADES) return false;
        Map<String, Integer> lines = new LinkedHashMap<>(stats.lines());
        for (String stat : def.stats.keySet()) {
            RollRange r = range(def, stat, upgrades);
            if (r == null || !lines.containsKey(stat)) continue;
            StatDefinition sd = RarityData.stat(stat);
            if (sd != null && sd.base) continue;
            lines.put(stat, Math.max(lines.get(stat), r.max()));
        }
        store(stack, def, lines, upgrades + 1);
        return true;
    }

    /** Remplace les jets d'un objet (forgemagie) et recalcule sa rareté. */
    public static void setStats(ItemStack stack, ItemDefinition def, ItemStats stats) {
        stack.set(ModDataComponents.STATS, stats);
        stack.set(ModDataComponents.RARITY, ItemRarity.rolled(def, stats));
    }

    private static void store(ItemStack stack, ItemDefinition def, Map<String, Integer> lines, int upgrades) {
        ItemStats old = stack.get(ModDataComponents.STATS);
        ItemStats stats = new ItemStats(lines,
                old != null ? old.exoStat() : "",
                old != null ? old.exoValue() : 0,
                upgrades,
                old != null ? old.well() : 0);
        stack.set(ModDataComponents.STATS, stats);
        stack.set(ModDataComponents.RARITY, ItemRarity.rolled(def, stats));
        stack.remove(ModDataComponents.LEGACY_EFFECTS);
    }
}
