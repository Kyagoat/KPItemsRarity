package net.Kyap.ItemsRarity.forge;

import net.Kyap.ItemsRarity.component.ItemStats;
import net.Kyap.ItemsRarity.component.ModDataComponents;
import net.Kyap.ItemsRarity.component.RuneData;
import net.Kyap.ItemsRarity.data.ItemDefinition;
import net.Kyap.ItemsRarity.data.RarityData;
import net.Kyap.ItemsRarity.data.StatDefinition;
import net.Kyap.ItemsRarity.item.custom.RuneItem;
import net.Kyap.ItemsRarity.util.StatEngine;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Forgemagie, façon Dofus, en simple :
 *
 * FORGER : une rune ajoute des points à une ligne. Plus la ligne est déjà haute dans sa fourchette, plus
 * c'est dur (90 % au minimum de la fourchette, 35 % au maximum, 12 % au-dessus = "overmax", en or dans
 * l'infobulle, plafonné à +10 % au-dessus du max). Trois issues :
 *   - succès critique : la ligne monte, rien d'autre ne bouge ;
 *   - succès neutre   : la ligne monte, d'autres lignes baissent un peu ;
 *   - échec           : la ligne ne monte pas, d'autres lignes baissent, et tout le poids perdu va
 *                       dans le PUITS de l'objet.
 * Le PUITS rend les runes suivantes plus sûres ; s'il contient au moins le poids de la rune, elle réussit
 * à coup sûr et le puits paie la différence. Un malus (ligne négative) se forge pareil, jusqu'à 0.
 *
 * EXO : une rune d'une stat que l'objet n'a pas (et marquée "exo" dans kp_stats) tente d'ajouter une
 * ligne exotique (une seule par objet) : 3 % de base, plus avec le puits. Annoncée à tout le serveur.
 *
 * BRISER : un objet à stats rend des runes selon ses jets (40 à 80 % de leur poids) ; une ressource
 * listée dans kp_runes rend ses runes (RuneSources). PIERRES : flourite, soufre, bismuth montent la
 * rareté d'un cran avec une chance (35 / 60 / 90 %).
 *
 * Le "poids" d'un point vient de kp_stats ("weight"), le même que l'outil d'équilibrage.
 */
public final class ForgeMagic {
    private ForgeMagic() {}

    public enum Outcome { CRITICAL, NEUTRAL, FAILURE, EXO, EXO_FAILURE, INVALID }

    public record Result(Outcome outcome, List<Component> log) {
        public static Result invalidOf(String key, Object... args) {
            return invalid(key, args);
        }

        static Result invalid(String key, Object... args) {
            return new Result(Outcome.INVALID, List.of(Component.translatable(key, args).withStyle(ChatFormatting.GRAY)));
        }
    }

    private static final float AT_MIN = 0.90f, AT_MAX = 0.35f, OVERMAX = 0.12f, EXO_CHANCE = 0.03f;

    // ---------------------------------------------------------------- Poids et valeurs

    static double weight(StatDefinition stat) {
        return stat.weight > 0 ? stat.weight : 1.0;
    }

    /** Points qu'ajoute une rune : la rune de base vaut environ 1 de poids (au moins 1 point). */
    public static int runePoints(StatDefinition stat, int power) {
        int base = Math.max(1, Math.max(stat.rune_power, (int) Math.round(1.0 / weight(stat))));
        return base * power;
    }

    public static double runeWeight(StatDefinition stat, int power) {
        return runePoints(stat, power) * weight(stat);
    }

    /** Plafond d'une ligne en forgemagie : +10 % au-dessus du max (au moins +1), et 0 pour un malus. */
    public static int cap(int max) {
        return max <= 0 ? 0 : max + Math.max(1, Math.round(max * 0.10f));
    }

    /** Plafond d'une ligne exotique : l'équivalent d'une rune Pa. */
    public static int exoCap(StatDefinition stat) {
        return runePoints(stat, RuneData.PA);
    }

    // ---------------------------------------------------------------- Chance

    /** Chance de réussite (0 à 1) de cette rune sur cet objet, puits compris. -1 si la rune n'y va pas. */
    public static float chance(ItemStack item, RuneData rune) {
        Target t = target(item, rune);
        if (t == null) return -1f;
        float base;
        if (t.exo) {
            base = t.current >= t.cap ? 0f : t.newExo ? EXO_CHANCE : EXO_CHANCE * 2;
        } else if (t.current >= t.cap) {
            base = 0f;
        } else if (t.current >= t.max) {
            base = OVERMAX;
        } else {
            float ratio = Math.max(0f, Math.min(1f, (t.current - t.min) / (float) Math.max(1, t.max - t.min)));
            base = AT_MIN - (AT_MIN - AT_MAX) * ratio;
        }
        if (base <= 0f) return 0f;
        int well = t.stats.well();
        double rw = runeWeight(t.stat, rune.power());
        if (!t.exo && well >= rw) return 1f;
        return (float) Math.min(0.99, base + (1 - base) * Math.min(0.5, well / (rw * 4)));
    }

    /** Ce que vise une rune sur un objet (ligne existante, malus, ou exo). */
    private record Target(ItemDefinition def, ItemStats stats, StatDefinition stat, boolean exo, boolean newExo,
                          int current, int min, int max, int cap) {}

    private static Target target(ItemStack item, RuneData rune) {
        ItemDefinition def = RarityData.itemDef(item);
        ItemStats stats = item.get(ModDataComponents.STATS);
        StatDefinition stat = RarityData.stat(rune.stat());
        if (def == null || stats == null || stat == null || stat.base || !stat.isActive()) return null;
        if (stats.lines().containsKey(stat.id)) {
            int[] r = StatEngine.range(def, stat.id, stats.upgrades());
            if (r == null) return null;
            int v = stats.get(stat.id);
            return new Target(def, stats, stat, false, false, v, r[0], r[1], cap(r[1]));
        }
        if (stats.hasExo() && stats.exoStat().equals(stat.id)) {
            return new Target(def, stats, stat, true, false, stats.exoValue(), 0, exoCap(stat), exoCap(stat));
        }
        if (stat.exo && !stats.hasExo()) return new Target(def, stats, stat, true, true, 0, 0, exoCap(stat), exoCap(stat));
        return null;
    }

    // ---------------------------------------------------------------- Forger

    public static Result apply(ItemStack item, RuneData rune, RandomSource random) {
        StatEngine.ensureRolled(item, random);
        Target t = target(item, rune);
        if (t == null) return Result.invalid("forge.itemsrarity.invalid_rune");
        float chance = chance(item, rune);
        if (chance <= 0f) return Result.invalid("forge.itemsrarity.capped");

        StatDefinition stat = t.stat;
        int points = runePoints(stat, rune.power());
        double rw = runeWeight(stat, rune.power());
        Map<String, Integer> lines = new LinkedHashMap<>(t.stats.lines());
        List<Component> log = new ArrayList<>();
        int well = t.stats.well();
        String exoStat = t.stats.exoStat();
        int exoValue = t.stats.exoValue();
        float roll = random.nextFloat();
        boolean success = roll < chance;
        Outcome outcome;

        if (success) {
            int gained = Math.min(points, t.cap - t.current);
            if (t.exo) {
                exoStat = stat.id;
                exoValue = t.current + gained;
                outcome = Outcome.EXO;
            } else {
                lines.put(stat.id, t.current + gained);
                // Le puits a payé la rune : succès garanti, sans perte
                if (well >= rw) {
                    well -= (int) Math.ceil(rw);
                    outcome = Outcome.CRITICAL;
                    log.add(Component.translatable("forge.itemsrarity.well_used", (int) Math.ceil(rw)).withStyle(ChatFormatting.AQUA));
                } else {
                    outcome = roll < chance * 0.5f ? Outcome.CRITICAL : Outcome.NEUTRAL;
                }
            }
            log.add(0, line(stat, gained, outcome == Outcome.EXO ? 0xFF5FB8 : 0x7FE08A));
            if (outcome == Outcome.NEUTRAL) loseWeight(lines, stat.id, rw * 0.6, random, log);
        } else {
            outcome = t.exo ? Outcome.EXO_FAILURE : Outcome.FAILURE;
            double lost = loseWeight(lines, stat.id, rw * (1.0 + random.nextDouble()), random, log);
            int toWell = (int) Math.round(lost);
            if (toWell > 0) {
                well += toWell;
                log.add(Component.translatable("forge.itemsrarity.well_gain", toWell).withStyle(ChatFormatting.AQUA));
            }
        }
        log.add(0, Component.translatable("forge.itemsrarity.outcome." + outcome.name().toLowerCase(java.util.Locale.ROOT))
                .withStyle(switch (outcome) {
                    case CRITICAL, EXO -> ChatFormatting.GREEN;
                    case NEUTRAL -> ChatFormatting.YELLOW;
                    default -> ChatFormatting.RED;
                }));
        ItemStats updated = new ItemStats(lines, exoStat, exoValue, t.stats.upgrades(), well);
        StatEngine.setStats(item, t.def, updated);
        return new Result(outcome, log);
    }

    /** Fait perdre ~weightToLose de poids aux autres lignes positives, au hasard. @return poids réellement perdu */
    private static double loseWeight(Map<String, Integer> lines, String exclude, double weightToLose, RandomSource random, List<Component> log) {
        Map<String, Integer> losses = new LinkedHashMap<>();
        double lost = 0;
        for (int guard = 0; guard < 200 && lost < weightToLose; guard++) {
            List<String> candidates = new ArrayList<>();
            for (var e : lines.entrySet()) {
                StatDefinition sd = RarityData.stat(e.getKey());
                if (!e.getKey().equals(exclude) && e.getValue() > 0 && sd != null && !sd.base) candidates.add(e.getKey());
            }
            if (candidates.isEmpty()) break;
            String pick = candidates.get(random.nextInt(candidates.size()));
            StatDefinition sd = RarityData.stat(pick);
            lines.put(pick, lines.get(pick) - 1);
            losses.merge(pick, 1, Integer::sum);
            lost += weight(sd);
        }
        losses.forEach((id, n) -> log.add(line(RarityData.stat(id), -n, 0xFF6B6B)));
        return lost;
    }

    private static Component line(StatDefinition stat, int points, int color) {
        return Component.literal(stat.formatValue(points, true) + " ").withStyle(s -> s.withColor(color))
                .append(stat.name().copy().withStyle(ChatFormatting.GRAY));
    }

    // ---------------------------------------------------------------- Pierres de rareté

    public static Result applyStone(ItemStack item, float chance, RandomSource random) {
        if (RarityData.itemDef(item) == null) return Result.invalid("forge.itemsrarity.no_item");
        if (StatEngine.upgrades(item) >= StatEngine.MAX_UPGRADES) return Result.invalid("forge.itemsrarity.max_rarity");
        if (random.nextFloat() < chance && StatEngine.upgradeRarity(item, random)) {
            return new Result(Outcome.CRITICAL, List.of(Component.translatable("forge.itemsrarity.stone_success",
                    Component.translatable("rarity.itemsrarity." + StatEngine.rarityOf(item).getId())).withStyle(ChatFormatting.GOLD)));
        }
        return new Result(Outcome.FAILURE, List.of(Component.translatable("forge.itemsrarity.stone_failure").withStyle(ChatFormatting.RED)));
    }

    // ---------------------------------------------------------------- Briser

    /** Runes rendues par un objet à stats (40 à 80 % du poids de chaque ligne positive, exo compris). */
    public static List<ItemStack> breakItem(ItemStack item, RandomSource random) {
        List<ItemStack> out = new ArrayList<>();
        ItemStats stats = item.get(ModDataComponents.STATS);
        if (stats == null) return out;
        Map<String, Integer> lines = new LinkedHashMap<>(stats.lines());
        if (stats.hasExo()) lines.merge(stats.exoStat(), stats.exoValue(), Integer::sum);
        lines.forEach((id, value) -> {
            StatDefinition stat = RarityData.stat(id);
            if (stat == null || stat.base || value <= 0) return;
            double units = value / (double) runePoints(stat, RuneData.BASE) * (0.4 + 0.4 * random.nextDouble());
            int n = (int) units + (random.nextDouble() < units - (int) units ? 1 : 0);
            addRunes(out, id, n);
        });
        return out;
    }

    /** Runes rendues par une pile de ressources (kp_runes), chaque objet cassé séparément. */
    public static List<ItemStack> breakResource(ItemStack stack, RandomSource random) {
        List<ItemStack> out = new ArrayList<>();
        var drops = RuneSources.dropsFor(stack);
        for (int i = 0; i < stack.getCount(); i++) {
            for (RuneSources.Drop d : drops) {
                if (RarityData.stat(d.stat()) == null) continue;
                for (int c = 0; c < d.count(); c++) {
                    if (random.nextFloat() < d.chance()) merge(out, RuneItem.of(d.stat(), d.power(), 1));
                }
            }
        }
        return out;
    }

    /** n runes de base -> le moins d'objets possible (Ra = 10, Pa = 3). */
    private static void addRunes(List<ItemStack> out, String stat, int n) {
        int ra = n / RuneData.RA, rest = n % RuneData.RA;
        int pa = rest / RuneData.PA, base = rest % RuneData.PA;
        if (ra > 0) merge(out, RuneItem.of(stat, RuneData.RA, ra));
        if (pa > 0) merge(out, RuneItem.of(stat, RuneData.PA, pa));
        if (base > 0) merge(out, RuneItem.of(stat, RuneData.BASE, base));
    }

    private static void merge(List<ItemStack> out, ItemStack add) {
        for (ItemStack s : out) {
            if (ItemStack.isSameItemSameComponents(s, add) && s.getCount() + add.getCount() <= s.getMaxStackSize()) {
                s.grow(add.getCount());
                return;
            }
        }
        out.add(add);
    }
}
