package net.Kyap.KPItemsRarity.client.tooltip.item;

import net.Kyap.KPItemsRarity.client.tooltip.card.Elem;
import net.Kyap.KPItemsRarity.client.tooltip.card.RpgFrame;
import net.Kyap.KPItemsRarity.client.tooltip.text.Texts;
import net.Kyap.KPItemsRarity.component.ItemStats;
import net.Kyap.KPItemsRarity.data.ItemDefinition;
import net.Kyap.KPItemsRarity.data.RarityData;
import net.Kyap.KPItemsRarity.data.RollRange;
import net.Kyap.KPItemsRarity.data.StatDefinition;
import net.Kyap.KPItemsRarity.events.AttributeEvents;
import net.Kyap.KPItemsRarity.stats.ItemRolls;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Toutes les lignes de stats de l'objet, écrites de la même façon et dans le même ordre pour tous les objets :
 *
 *   +7 % Force (5 - 8)      jet tiré : valeur, nom, fourchette ; or au-dessus du maximum (forgemagie)
 *   +1 - +3 % Critique      jet possible (objet pas encore tiré : menu créatif, JEI)
 *   +3 Robustesse           attribut vanilla de l'objet, montré comme la stat du mod qui lui correspond
 *                           (ajouté au jet si l'objet tire aussi cette stat : une seule ligne)
 *
 * Toujours un signe, vert pour un bonus, rouge pour un malus ; le nom en gris clair. L'ordre est celui des stats
 * ("order" dans kp_stats, le même que la fiche de personnage). Maj : une courte explication sous chaque stat.
 */
final class StatRows {
    private StatRows() {}

    /** Largeur où couper l'explication d'une stat (Maj). */
    private static final int HELP_WIDTH = 150;
    private static final Comparator<Entry> ORDER = Comparator.comparingInt((Entry e) -> e.stat().order).thenComparing(e -> e.stat().id);

    /**
     * Une ligne à écrire : la stat, sa valeur, et sa fourchette de jet (null si aucune).
     * {@code possible} : l'objet n'est pas tiré, la ligne montre seulement la fourchette.
     */
    private record Entry(StatDefinition stat, int value, @Nullable RollRange range, boolean possible) {}

    static void add(List<Elem> body, ItemStack stack, @Nullable ItemDefinition def, @Nullable ItemStats stats, Font font) {
        List<Entry> entries = new ArrayList<>();
        List<Elem> unknown = new ArrayList<>();
        if (stats != null) collectRolled(entries, unknown, def, stats);
        else if (def != null) collectPossible(entries, def);
        List<Elem> otherAttributes = collectVanilla(entries, stack);
        entries.sort(ORDER);

        int first = body.size();
        for (Entry e : entries) {
            body.add(line(e));
            addHelp(body, font, e.stat());
        }
        body.addAll(otherAttributes);
        body.addAll(unknown);
        if (stats != null && !Screen.hasShiftDown()) addShiftHint(body.subList(first, body.size()), font);
    }

    // ---------------------------------------------------------------- Collecte

    /** Les jets de l'objet ; une stat que le jeu ne connaît plus est gardée à part, en gris, à la fin. */
    private static void collectRolled(List<Entry> entries, List<Elem> unknown, @Nullable ItemDefinition def, ItemStats stats) {
        stats.lines().forEach((statId, value) -> {
            StatDefinition stat = RarityData.stat(statId);
            if (stat == null) {
                unknown.add(new Elem.Line(Component.literal(value + " " + statId).withStyle(ChatFormatting.DARK_GRAY)));
            } else if (!stat.base) { // la ligne de base (Dégâts) est en grand dans l'en-tête
                entries.add(new Entry(stat, value, def != null ? ItemRolls.range(def, statId, stats.upgrades()) : null, false));
            }
        });
    }

    private static void collectPossible(List<Entry> entries, ItemDefinition def) {
        for (String statId : def.stats.keySet()) {
            StatDefinition stat = RarityData.stat(statId);
            RollRange range = def.range(statId);
            if (stat != null && !stat.base && range != null) entries.add(new Entry(stat, range.max(), range, true));
        }
    }

    /**
     * Les attributs vanilla de l'objet (robustesse et recul de la netherite...), hors dégâts, vitesse et armure qui
     * sont dans l'en-tête. Ceux qu'une stat du mod représente sont rangés avec elle ; les autres sont rendus à part.
     */
    private static List<Elem> collectVanilla(List<Entry> entries, ItemStack stack) {
        List<Elem> others = new ArrayList<>();
        for (ItemAttributeModifiers.Entry e : stack.getAttributeModifiers().modifiers()) {
            AttributeModifier modifier = e.modifier();
            Holder<Attribute> attribute = e.attribute();
            if (AttributeEvents.isOurModifier(modifier.id()) || modifier.amount() == 0) continue;
            if (attribute.equals(Attributes.ATTACK_DAMAGE) || attribute.equals(Attributes.ATTACK_SPEED) || attribute.equals(Attributes.ARMOR)) continue;
            StatDefinition stat = statFor(attribute, modifier.operation());
            if (stat != null) addVanilla(entries, stat, (int) Math.round(modifier.amount() / stat.scale));
            else others.add(otherAttributeLine(attribute, modifier));
        }
        return others;
    }

    /**
     * Les points vanilla d'une stat que l'objet tire aussi (Ancrage de la netherite...) s'ajoutent à son jet : une
     * seule ligne avec le total, et la fourchette décalée d'autant. Sinon, une ligne à elle.
     */
    private static void addVanilla(List<Entry> entries, StatDefinition stat, int points) {
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (e.stat() != stat) continue;
            RollRange r = e.range() == null ? null : new RollRange(e.range().min() + points, e.range().max() + points);
            entries.set(i, new Entry(stat, e.value() + points, r, e.possible()));
            return;
        }
        entries.add(new Entry(stat, points, null, false));
    }

    /** La stat du mod qui pose cet attribut de cette façon, s'il y en a une. */
    @Nullable
    private static StatDefinition statFor(Holder<Attribute> attribute, AttributeModifier.Operation operation) {
        for (StatDefinition stat : RarityData.statsSorted()) {
            if (!stat.base && stat.parsedOperation() == operation && stat.attributeHolder().map(attribute::equals).orElse(false)) return stat;
        }
        return null;
    }

    // ---------------------------------------------------------------- Mise en forme

    /** "+7 % Force (5 - 8)", "+1 - +3 % Critique", "-5 % Soins" : la seule façon d'écrire une stat. */
    private static Elem.Line line(Entry e) {
        StatDefinition stat = e.stat();
        if (!stat.isActive()) {
            // Stat d'un mod absent : barrée, sans effet.
            return new Elem.Line(Component.literal(valueText(e) + " ").append(stat.name()).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.STRIKETHROUGH));
        }
        int color = valueColor(e);
        MutableComponent text = Component.literal(valueText(e) + " ").withStyle(s -> s.withColor(color))
                .append(stat.name().withStyle(s -> s.withColor(RpgFrame.TEXT_SOFT)));
        // Fourchette juste après la stat (pas collée à droite) : l'infobulle reste étroite.
        if (!e.possible() && e.range() != null) {
            text.append(Component.literal(" (" + e.range().format(stat) + ")").withStyle(s -> s.withColor(RpgFrame.TEXT_FAINT)));
        }
        return new Elem.Line(text);
    }

    /** "+7 %" pour un jet ; "+1 - +3 %" pour une fourchette (un seul nombre si min = max). */
    private static String valueText(Entry e) {
        StatDefinition stat = e.stat();
        RollRange r = e.range();
        if (!e.possible() || r == null || r.min() == r.max()) return stat.formatValue(e.possible() && r != null ? r.min() : e.value(), true);
        String unit = "percent".equalsIgnoreCase(stat.format) ? " %" : "";
        return number(stat, r.min()) + " - " + number(stat, r.max()) + unit;
    }

    /** Un nombre signé au format de la stat, sans l'unité. */
    private static String number(StatDefinition stat, int points) {
        String sign = points > 0 ? "+" : "";
        return "tenths".equalsIgnoreCase(stat.format) ? sign + String.format(Locale.ROOT, "%.1f", points / 10.0) : sign + points;
    }

    /** Vert pour un bonus, rouge pour un malus, or au-dessus du maximum du jet. */
    private static int valueColor(Entry e) {
        if (!e.possible() && e.range() != null && e.value() > e.range().max()) return RpgFrame.OVERMAX;
        // Une fourchette est un malus si elle ne monte jamais au-dessus de 0 ("-1 - 0 Vie").
        boolean malus = e.possible() && e.range() != null ? e.range().max() <= 0 && e.range().min() < 0 : e.value() < 0;
        return malus ? RpgFrame.NEGATIVE : RpgFrame.POSITIVE;
    }

    /** Un attribut vanilla sans stat du mod pour le représenter : même style, nom tel que le jeu le donne. */
    private static Elem.Line otherAttributeLine(Holder<Attribute> attribute, AttributeModifier modifier) {
        double amount = modifier.amount();
        String sign = amount > 0 ? "+" : "";
        String value = modifier.operation() == AttributeModifier.Operation.ADD_VALUE
                ? sign + Texts.number(amount)
                : sign + Texts.number(amount * 100) + " %";
        int color = amount < 0 ? RpgFrame.NEGATIVE : RpgFrame.POSITIVE;
        return new Elem.Line(Component.literal(value + " ").withStyle(s -> s.withColor(color))
                .append(Component.literal(capitalized(Component.translatable(attribute.value().getDescriptionId()).getString()))
                        .withStyle(s -> s.withColor(RpgFrame.TEXT_SOFT))));
    }

    private static String capitalized(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    // ---------------------------------------------------------------- Aide (Maj)

    /** Maj enfoncée : une courte explication grisée sous la stat, coupée en lignes étroites. */
    private static void addHelp(List<Elem> body, Font font, StatDefinition stat) {
        if (!Screen.hasShiftDown()) return;
        String key = "stat." + stat.namespace + "." + stat.id + ".desc";
        if (!Language.getInstance().has(key)) return;
        for (var part : font.getSplitter().splitLines(Component.translatable(key), HELP_WIDTH, Style.EMPTY)) {
            body.add(new Elem.Line(Component.literal(part.getString()).withStyle(st -> st.withColor(RpgFrame.TEXT_FAINT).withItalic(true)), 8));
        }
    }

    /** Indice discret "[Maj] : ce que fait chaque stat", sur la ligne la plus courte (là où il tient le mieux). */
    private static void addShiftHint(List<Elem> lines, Font font) {
        int best = -1, bestW = Integer.MAX_VALUE;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i) instanceof Elem.Line line && line.right().getString().isEmpty() && font.width(line.left()) < bestW) {
                best = i;
                bestW = font.width(line.left());
            }
        }
        if (best >= 0 && lines.get(best) instanceof Elem.Line line) {
            lines.set(best, line.withHint(Component.translatable("tooltip.kpitemsrarity.rpg.help_hint").withStyle(st -> st.withColor(RpgFrame.TEXT_FAINT))));
        }
    }
}
