package net.Kyap.KPItemsRarity.client.tooltip.text;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Petites mises en forme de texte communes aux fenêtres : nombres et noms de touches. */
public final class Texts {
    private Texts() {}

    /** "3" pour 3.0, "1.5" sinon (une décimale au plus). */
    public static String number(double v) {
        return Math.abs(v - Math.rint(v)) < 0.05 ? String.valueOf((long) Math.rint(v)) : String.format(Locale.ROOT, "%.1f", v);
    }

    /** "Left Alt" -> "Alt", "Alt gauche" -> "Alt" : plus court dans une fenêtre. */
    public static Component shortKey(Component key) {
        String text = key.getString().replaceAll("(?i)\\b(left|right|gauche|droite?)\\b", "").trim();
        return Component.literal(text.isEmpty() ? key.getString() : text);
    }
}
