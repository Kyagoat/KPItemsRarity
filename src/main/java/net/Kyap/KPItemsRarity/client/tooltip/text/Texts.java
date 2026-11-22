package net.Kyap.KPItemsRarity.client.tooltip.text;

import net.Kyap.KPItemsRarity.client.tooltip.card.Palette;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/** Petites mises en forme de texte communes aux fenêtres : nombres, noms de touches, palier de panoplie. */
public final class Texts {
    private Texts() {}

    private static final Pattern KEY_SIDE = Pattern.compile("(?i)\\b(left|right|gauche|droite?)\\b");

    /** "3" pour 3.0, "1.5" sinon (une décimale au plus). */
    public static String number(double v) {
        return Math.abs(v - Math.rint(v)) < 0.05 ? String.valueOf((long) Math.rint(v)) : String.format(Locale.ROOT, "%.1f", v);
    }

    /** "Left Alt" -> "Alt", "Alt gauche" -> "Alt" : plus court dans une fenêtre. */
    public static Component shortKey(Component key) {
        String text = KEY_SIDE.matcher(key.getString()).replaceAll("").trim();
        return Component.literal(text.isEmpty() ? key.getString() : text);
    }

    /** Le palier d'une panoplie en pastilles "◆◆◆◇◇" : lisible sans dépendre de la couleur. */
    public static Component tierPips(int tier) {
        int shown = Math.max(1, Math.min(Palette.MAX_TIER, tier));
        return Component.literal("◆".repeat(shown)).withStyle(style -> style.withColor(Palette.tier(shown)))
                .append(Component.literal("◇".repeat(Palette.MAX_TIER - shown)).withStyle(style -> style.withColor(Palette.TEXT_FAINT)));
    }
}
