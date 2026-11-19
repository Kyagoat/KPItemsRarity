package net.Kyap.KPItemsRarity.client.tooltip;

import net.Kyap.KPItemsRarity.client.tooltip.TooltipModel.*;
import net.minecraft.client.gui.Font;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.*;
import net.neoforged.fml.ModList;

import java.util.*;
import java.util.regex.Pattern;

/** Petits outils de texte des infobulles : relire une ligne vanilla, recoller, couper, retourner. */
public final class TooltipText {
    private TooltipText() {}

    /** Ligne prête à dessiner -> texte avec ses styles (pour la relire et la remettre à nos couleurs). */
    public static Component toComponent(FormattedCharSequence seq) {
        MutableComponent out = Component.empty();
        StringBuilder buf = new StringBuilder();
        Style[] current = {Style.EMPTY};
        seq.accept((index, style, codePoint) -> {
            if (!style.equals(current[0]) && buf.length() > 0) {
                out.append(Component.literal(buf.toString()).withStyle(current[0]));
                buf.setLength(0);
            }
            current[0] = style;
            buf.appendCodePoint(codePoint);
            return true;
        });
        if (buf.length() > 0) out.append(Component.literal(buf.toString()).withStyle(current[0]));
        return out;
    }

    static final Pattern STAT_LINE = Pattern.compile("^\\s*([+-]?\\d+(?:[.,]\\d+)?\\s*%?)\\s+(.+)$");

    /** Ligne d'un autre mod qui ressemble à une stat ("3 Attack Range") : mêmes couleurs que nos stats. */
    static Component asStatLine(Component line) {
        var m = STAT_LINE.matcher(line.getString());
        if (!m.matches()) return line.copy().withStyle(s -> s.withColor(RpgFrame.TEXT_SOFT));
        String value = m.group(1).trim();
        // Même écriture que nos stats : "+3 Portée d'attaque"
        if (!value.startsWith("+") && !value.startsWith("-")) value = "+" + value;
        int color = value.startsWith("-") ? RpgFrame.NEGATIVE : RpgFrame.POSITIVE;
        return Component.literal(value + " ").withStyle(s -> s.withColor(color))
                .append(Component.literal(m.group(2)).withStyle(s -> s.withColor(RpgFrame.TEXT_SOFT)));
    }

    /**
     * Les mods coupent souvent leur description en plusieurs lignes au milieu d'une phrase.
     * On recolle les lignes qui ne finissent pas une phrase, pour que le texte suive la largeur de l'infobulle.
     * Les explications sont en gris clair, comme les nôtres.
     */
    static List<Component> joinBrokenLines(List<Component> lines) {
        List<Component> out = new ArrayList<>();
        StringBuilder current = null;
        for (Component c : lines) {
            String text = c.getString().trim();
            if (current == null) current = new StringBuilder(text);
            else current.append(' ').append(text);
            if (text.matches(".*[.!?:;)\\]]$")) {
                out.add(Component.literal(current.toString()).withStyle(st -> st.withColor(RpgFrame.TEXT_DIM)));
                current = null;
            }
        }
        if (current != null) out.add(Component.literal(current.toString()).withStyle(st -> st.withColor(RpgFrame.TEXT_DIM)));
        return out;
    }

    /** Nom affiché du mod de l'objet (la ligne "Items Rarity" ajoutée par JEI & co). */
    static String modName(ItemStack stack) {
        String ns = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        return ModList.get().getModContainerById(ns).map(c -> c.getModInfo().getDisplayName()).orElse("");
    }

    /** Coupe un texte trop long avec "…" (largeur fixe de l'infobulle). */
    /** "Left Alt" -> "Alt", "Alt gauche" -> "Alt" : plus court dans l'infobulle. */
    static Component shortKey(Component key) {
        String text = key.getString().replaceAll("(?i)\\b(left|right|gauche|droite?)\\b", "").trim();
        return Component.literal(text.isEmpty() ? key.getString() : text);
    }

    static List<FormattedCharSequence> wrap(Font font, List<Component> lines, int width) {
        List<FormattedCharSequence> out = new ArrayList<>();
        for (Component c : lines) {
            if (c.getString().isEmpty()) out.add(FormattedCharSequence.EMPTY);
            else out.addAll(font.split(c, Math.max(40, width)));
        }
        return out;
    }
}
