package net.Kyap.KPItemsRarity.client.tooltip.card;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Un texte retourné à la ligne à une largeur donnée. Le découpage est gardé tant que la largeur ne change pas :
 * il coûte cher, et une fenêtre se redessine à chaque image avec la même largeur.
 */
final class Wrapped {
    private final Component text;
    private final int minWidth;
    private int width = -1;
    private List<FormattedCharSequence> lines = List.of();

    /** @param minWidth largeur en dessous de laquelle on ne coupe pas (un mot par ligne serait illisible) */
    Wrapped(Component text, int minWidth) {
        this.text = text;
        this.minWidth = minWidth;
    }

    /** Les lignes à cette largeur ; un texte vide donne une ligne vide (un saut de paragraphe). */
    List<FormattedCharSequence> lines(Font font, int width) {
        if (width != this.width) {
            this.width = width;
            this.lines = text.getString().isEmpty() ? List.of(FormattedCharSequence.EMPTY) : font.split(text, Math.max(minWidth, width));
        }
        return lines;
    }
}
