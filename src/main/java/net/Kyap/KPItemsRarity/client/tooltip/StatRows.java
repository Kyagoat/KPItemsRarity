package net.Kyap.KPItemsRarity.client.tooltip;

import net.Kyap.KPItemsRarity.client.tooltip.TooltipModel.Elem;
import net.Kyap.KPItemsRarity.client.tooltip.TooltipModel.Row;
import net.Kyap.KPItemsRarity.client.tooltip.TooltipModel.RowElem;
import net.Kyap.KPItemsRarity.data.RarityData;
import net.Kyap.KPItemsRarity.data.RollRange;
import net.Kyap.KPItemsRarity.data.StatDefinition;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Les lignes de stats de l'objet : valeur, fourchette, aide (Maj) et jets possibles d'un objet pas encore tiré. */
final class StatRows {
    private StatRows() {}

    /** Maj enfoncée : une courte explication grisée sous la stat (coupée en lignes étroites). */
    static void addStatHelp(List<Elem> body, Font font, StatDefinition stat) {
        if (stat == null || !Screen.hasShiftDown()) return;
        String key = "stat." + stat.namespace + "." + stat.id + ".desc";
        if (!Language.getInstance().has(key)) return;
        Component desc = Component.translatable(key);
        for (var part : font.getSplitter().splitLines(desc, 150, Style.EMPTY)) {
            body.add(new RowElem(new Row(Component.literal(part.getString())
                    .withStyle(st -> st.withColor(RpgFrame.TEXT_FAINT).withItalic(true)), Component.empty(), 8)));
        }
    }

    static Row statRow(String statId, int value, @Nullable RollRange range) {
        StatDefinition stat = RarityData.stat(statId);
        if (stat == null) return new Row(Component.literal(value + " " + statId).withStyle(ChatFormatting.DARK_GRAY), Component.empty(), 0);
        if (!stat.isActive()) {
            return new Row(Component.literal(stat.formatValue(value, true) + " ").append(stat.name())
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.STRIKETHROUGH), Component.empty(), 0);
        }
        // Au-dessus du maximum théorique (forgemagie) : la valeur passe en or
        boolean overmax = range != null && value > range.max();
        int valueColor = overmax ? RpgFrame.OVERMAX : value < 0 ? RpgFrame.NEGATIVE : RpgFrame.POSITIVE;
        MutableComponent left = Component.literal(stat.formatValue(value, true) + " ").withStyle(s -> s.withColor(valueColor))
                .append(stat.name().withStyle(s -> s.withColor(RpgFrame.TEXT_SOFT)));
        if (range != null) {
            // Fourchette juste après la stat (pas collée à droite) : l'infobulle reste étroite.
            left.append(Component.literal(" (" + range.format(stat) + ")").withStyle(s -> s.withColor(RpgFrame.TEXT_FAINT)));
        }
        return new Row(left, Component.empty(), 0);
    }

    static Row possibleRow(StatDefinition stat, RollRange r) {
        String unit = "percent".equalsIgnoreCase(stat.format) ? " %" : "";
        Component left = Component.literal(r.format(stat) + unit + " ").withStyle(s -> s.withColor(RpgFrame.TEXT_DIM))
                .append(stat.name().withStyle(s -> s.withColor(RpgFrame.TEXT_DIM)));
        return new Row(left, Component.empty(), 0);
    }
}
