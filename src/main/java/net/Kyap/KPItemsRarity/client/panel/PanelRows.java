package net.Kyap.KPItemsRarity.client.panel;

import net.Kyap.KPItemsRarity.compat.BetterCombatCompat;
import net.Kyap.KPItemsRarity.data.RarityData;
import net.Kyap.KPItemsRarity.data.StatDefinition;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Les lignes de la fiche : TOUTES les caractéristiques du personnage, rangées par famille, même à 0
 * (en gris, repliées par défaut : on voit ce qu'on peut obtenir). Valeur "totale" (base comprise : 5 % de
 * critique, 40 d'endurance...) ou "bonus" (ce qu'apportent équipement, panoplies, potions, autres mods), selon
 * la stat (StatDefinition.showsTotal). Chaque ligne garde son détail pour le survol.
 */
final class PanelRows {
    private PanelRows() {}

    /** Ordre des familles : ce qui sert le plus souvent en haut. */
    private static final List<String> CATEGORIES = List.of("combat", "defense", "mobility", "harvest", "utility", "magic");
    private static final Map<String, String> CATEGORY_ICONS = Map.of("combat", "⚔", "defense", "⛨", "mobility", "➶",
            "harvest", "⛏", "utility", "✧", "magic", "✦");
    private static final Map<String, Integer> CATEGORY_COLORS = Map.of("combat", 0x9A3B2E, "defense", 0x3B5E9A, "mobility", 0x2E8A5A,
            "harvest", 0x8A6A2E, "utility", 0x6A5A8A, "magic", 0x7A3B9A);
    /** Stats déjà dans le bandeau du haut. */
    private static final Set<String> IN_BAND = Set.of("vitality", "resistance");
    private static final int RANGE_COLOR = 0x7FD1C7;

    /** Une ligne de la fiche. */
    sealed interface Row permits Header, StatLine, Toggle {}

    /** Titre de famille ("⚔ Combat") avec son filet. */
    record Header(Component label, int color) implements Row {}

    /** Comment afficher une stat : normale, à 0 (grisée) ou en négatif (malus). */
    enum Shade { NORMAL, ZERO, MALUS }

    record StatLine(Component label, String value, int color, Shade shade, List<StatDetail> details) implements Row {}

    /** "Afficher / masquer les stats inactives". */
    record Toggle(Component label) implements Row {}

    static List<Row> build(LivingEntity entity, boolean showAll) {
        Map<String, List<Row>> active = new LinkedHashMap<>(), idle = new LinkedHashMap<>();
        for (String category : CATEGORIES) {
            active.put(category, new ArrayList<>());
            idle.put(category, new ArrayList<>());
        }
        for (StatDefinition stat : RarityData.statsSorted()) {
            if (stat.base || IN_BAND.contains(stat.id)) continue;
            var holder = stat.attributeHolder();
            if (holder.isEmpty()) continue; // mod absent : on n'affiche pas
            AttributeInstance instance = entity.getAttribute(holder.get());
            if (instance == null) continue;
            StatValue v = StatValue.of(stat, instance);
            String category = stat.category.toLowerCase(Locale.ROOT);
            Shade shade = v.gained() == 0 && v.points() == 0 ? Shade.ZERO : v.gained() < 0 ? Shade.MALUS : Shade.NORMAL;
            Row row = new StatLine(stat.name(), stat.formatValue(v.points(), !v.total() && v.points() != 0), stat.rgb(), shade,
                    details(entity, stat, instance, v));
            (v.gained() != 0 ? active : idle).computeIfAbsent(category, k -> new ArrayList<>()).add(row);
        }
        if (entity instanceof Player p) active.get("combat").add(0, attackRange(p));
        return arrange(active, idle, showAll);
    }

    /** Valeur affichée d'une stat (en points), sa base, et ce qui a été gagné en plus. */
    private record StatValue(int points, int basePoints, int gained, boolean total) {
        static StatValue of(StatDefinition stat, AttributeInstance instance) {
            double bonus = 0;
            for (AttributeModifier modifier : instance.getModifiers()) {
                if (modifier.operation() == stat.parsedOperation()) bonus += modifier.amount();
            }
            int bonusPoints = (int) Math.round(bonus / stat.scale);
            boolean total = stat.showsTotal();
            int points = total ? (int) Math.round(instance.getValue() / stat.scale) : bonusPoints;
            int basePoints = (int) Math.round(instance.getBaseValue() / stat.scale);
            return new StatValue(points, basePoints, total ? points - basePoints : bonusPoints, total);
        }
    }

    /** Survol d'une stat : son nom, ce qu'elle fait (une ligne courte), sa base, puis QUI la donne. */
    private static List<StatDetail> details(LivingEntity entity, StatDefinition stat, AttributeInstance instance, StatValue v) {
        List<StatDetail> details = new ArrayList<>();
        details.add(new StatDetail.Text(stat.name().withStyle(st -> st.withColor(stat.rgb()))));
        String descKey = "stat." + stat.namespace + "." + stat.id + ".desc";
        if (Language.getInstance().has(descKey)) {
            details.add(new StatDetail.Text(Component.translatable(descKey).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
        }
        if (v.total()) {
            details.add(new StatDetail.Text(Component.translatable("panel.kpitemsrarity.detail.base", stat.formatValue(v.basePoints(), false))
                    .withStyle(ChatFormatting.DARK_GRAY)));
        }
        details.addAll(StatSources.sources(entity, stat, instance));
        return details;
    }

    /** Ce qui bouge d'abord par famille, puis le reste (si demandé), et la ligne pour tout afficher / masquer. */
    private static List<Row> arrange(Map<String, List<Row>> active, Map<String, List<Row>> idle, boolean showAll) {
        List<Row> rows = new ArrayList<>();
        int hidden = 0;
        for (String category : active.keySet()) {
            List<Row> on = active.get(category), off = idle.getOrDefault(category, List.of());
            if (on.isEmpty() && (off.isEmpty() || !showAll)) {
                hidden += off.size();
                continue;
            }
            rows.add(new Header(Component.literal(CATEGORY_ICONS.getOrDefault(category, "•") + " ")
                    .append(Component.translatable("panel.kpitemsrarity.category." + category)), CATEGORY_COLORS.getOrDefault(category, 0x7A7A7A)));
            rows.addAll(on);
            if (showAll) rows.addAll(off);
            else hidden += off.size();
        }
        if (hidden > 0 || showAll) {
            rows.add(new Toggle(showAll ? Component.translatable("panel.kpitemsrarity.hide_idle")
                    : Component.translatable("panel.kpitemsrarity.show_idle", hidden)));
        }
        return rows;
    }

    /** Portée d'attaque réelle avec l'arme en main (celle de Better Combat s'il est installé). */
    private static Row attackRange(Player player) {
        double range = BetterCombatCompat.attackRange(player);
        if (range <= 0) range = player.entityInteractionRange();
        String value = String.format(Locale.ROOT, "%.2f", range).replace('.', ',') + " m";
        List<StatDetail> details = List.of(
                new StatDetail.Text(Component.translatable("panel.kpitemsrarity.attack_range").withStyle(s -> s.withColor(RANGE_COLOR))),
                new StatDetail.Text(Component.translatable("panel.kpitemsrarity.attack_range.detail", player.getMainHandItem().getHoverName())
                        .withStyle(ChatFormatting.GRAY)));
        return new StatLine(Component.translatable("panel.kpitemsrarity.attack_range"), value, RANGE_COLOR, Shade.NORMAL, details);
    }
}
