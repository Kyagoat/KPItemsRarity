package net.Kyap.KPItemsRarity.client.tooltip.card;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Un élément du corps d'une fenêtre (infobulle d'objet ou carte de panoplie). Chacun sait sa hauteur, la largeur
 * qu'il demande et comment se dessiner : le moteur ({@link CardRenderer}) n'a plus qu'à les empiler.
 *
 *   Line     une ligne de texte, avec un texte grisé aligné à droite (ou l'indice "[Maj]")
 *   Title    un petit titre de section avec son trait ("Enchantements ───")
 *   Gap      un espace vide
 *   Rule     un trait de séparation
 *   Text     un texte libre, retourné à la largeur de la fenêtre (lore, descriptions)
 *   Passive  l'encadré d'un passif
 *   Image    une image d'un autre mod (emplacements d'appât de Tide, contenu d'une sacoche...)
 */
public sealed interface Elem permits Elem.Line, Elem.Title, Elem.Gap, Elem.Rule, Elem.Text, Elem.Passive, Elem.Image {

    /** Hauteur d'une ligne de texte du corps. */
    int LINE_H = 11;

    int height(Font font, int wrapWidth);

    /** Largeur que le contenu demande à la fenêtre (0 : il s'adapte à la largeur). */
    int width(Font font);

    void draw(GuiGraphics g, Font font, Area area, int y);

    /**
     * Zone du corps : bords gauche et droit, largeur utile, place gardée à droite pour la barre de défilement
     * (0 sans défilement) et largeur où retourner le texte.
     */
    record Area(int left, int right, int inner, int gutter, int wrapWidth) {
        int textRight() {
            return right - gutter;
        }
    }

    // ---------------------------------------------------------------- Texte

    /** Une ligne : texte à gauche (décalé de {@code indent}), texte à droite, ou l'indice "[Maj]" s'il tient. */
    record Line(Component left, Component right, int indent, boolean hint) implements Elem {
        public Line(Component left) {
            this(left, Component.empty(), 0, false);
        }

        public Line(Component left, Component right) {
            this(left, right, 0, false);
        }

        public Line(Component left, int indent) {
            this(left, Component.empty(), indent, false);
        }

        /** La même ligne avec un indice ("[Maj] pour le détail") à droite, affiché seulement s'il tient. */
        public Line withHint(Component hintText) {
            return new Line(left, hintText, indent, true);
        }

        @Override
        public int height(Font font, int wrapWidth) {
            return LINE_H;
        }

        @Override
        public int width(Font font) {
            int w = indent + font.width(left);
            int rw = hint ? 0 : font.width(right);
            return rw > 0 ? w + 12 + rw : w;
        }

        @Override
        public void draw(GuiGraphics g, Font font, Area area, int y) {
            Component rt = right;
            int room = area.inner() - area.gutter();
            if (hint && font.width(left) + indent + 8 + font.width(rt) > room) rt = Component.empty();
            int rightW = font.width(rt);
            int leftMax = room - indent - (rightW > 0 ? rightW + 6 : 0);
            g.drawString(font, RpgFrame.fit(font, left, leftMax), area.left() + indent, y, 0xFFFFFF, true);
            if (rightW > 0) g.drawString(font, rt, area.textRight() - rightW, y, 0xFFFFFF, true);
        }
    }

    /** Petit titre de section, suivi d'un trait jusqu'au bord. */
    record Title(Component title, int color) implements Elem {
        @Override
        public int height(Font font, int wrapWidth) {
            return 12;
        }

        @Override
        public int width(Font font) {
            return font.width(title) + 20;
        }

        @Override
        public void draw(GuiGraphics g, Font font, Area area, int y) {
            RpgFrame.sectionTitle(g, font, title, area.left(), y, area.inner(), color);
        }
    }

    record Gap(int height) implements Elem {
        /** L'espace habituel entre deux sections. */
        public static final Gap SECTION = new Gap(3);

        @Override
        public int height(Font font, int wrapWidth) {
            return height;
        }

        @Override
        public int width(Font font) {
            return 0;
        }

        @Override
        public void draw(GuiGraphics g, Font font, Area area, int y) {}
    }

    /** Trait fin de séparation (avant le texte libre du bas). */
    record Rule() implements Elem {
        @Override
        public int height(Font font, int wrapWidth) {
            return 6;
        }

        @Override
        public int width(Font font) {
            return 0;
        }

        @Override
        public void draw(GuiGraphics g, Font font, Area area, int y) {
            g.fill(area.left(), y + 1, area.right(), y + 2, Palette.argb(Palette.TEXT_FAINT, 0x60));
        }
    }

    /** Texte libre (lore, descriptions, textes des autres mods), retourné à la largeur ; un paragraphe vide = ligne vide. */
    final class Text implements Elem {
        private static final int LINE_H = 10, MIN_WIDTH = 40;
        private final List<Wrapped> paragraphs = new ArrayList<>();
        private final int color;

        public Text(List<Component> paragraphs, int color) {
            for (Component paragraph : paragraphs) this.paragraphs.add(new Wrapped(paragraph, MIN_WIDTH));
            this.color = color;
        }

        @Override
        public int height(Font font, int wrapWidth) {
            int lines = 0;
            for (Wrapped paragraph : paragraphs) lines += paragraph.lines(font, wrapWidth).size();
            return lines * LINE_H;
        }

        @Override
        public int width(Font font) {
            return 0;
        }

        @Override
        public void draw(GuiGraphics g, Font font, Area area, int y) {
            for (Wrapped paragraph : paragraphs) {
                for (FormattedCharSequence line : paragraph.lines(font, area.wrapWidth())) {
                    g.drawString(font, line, area.left(), y, color, true);
                    y += LINE_H;
                }
            }
        }
    }

    // ---------------------------------------------------------------- Encadrés

    /**
     * Encadré d'un passif : nom et voyant d'état sur la première ligne, puis sa description et (Maj) sa formule,
     * retournées à la largeur. Plus pâle quand le passif n'est pas actif.
     */
    final class Passive implements Elem {
        private static final int PAD = 5, NAME_H = 11, LINE_H = 10, MIN_WIDTH = 60;
        /** Marge au-dessus et en dessous du contenu, espace sous l'encadré. */
        private static final int INSIDE_TOP = 4, INSIDE_BOTTOM = 4, BELOW = 3;
        private final Component name, state;
        private final Wrapped description;
        @Nullable private final Wrapped formula;
        private final int color;
        private final boolean on;

        /**
         * @param state   voyant à droite du nom (actif, en recharge...)
         * @param formula réglages bruts, montrés avec Maj (null sinon)
         * @param on      le passif agit en ce moment
         */
        public Passive(Component name, Component state, Component description, @Nullable Component formula, int color, boolean on) {
            this.name = name;
            this.state = state;
            this.description = new Wrapped(description, MIN_WIDTH);
            this.formula = formula == null ? null : new Wrapped(formula, MIN_WIDTH);
            this.color = color;
            this.on = on;
        }

        private List<FormattedCharSequence> descriptionLines(Font font, int wrapWidth) {
            return description.lines(font, wrapWidth - 2 * PAD);
        }

        private List<FormattedCharSequence> formulaLines(Font font, int wrapWidth) {
            return formula == null ? List.of() : formula.lines(font, wrapWidth - 2 * PAD);
        }

        @Override
        public int height(Font font, int wrapWidth) {
            int lines = descriptionLines(font, wrapWidth).size() + formulaLines(font, wrapWidth).size();
            return INSIDE_TOP + NAME_H + lines * LINE_H + INSIDE_BOTTOM + BELOW;
        }

        @Override
        public int width(Font font) {
            return 0;
        }

        @Override
        public void draw(GuiGraphics g, Font font, Area area, int y) {
            int x0 = area.left(), x1 = area.textRight(), y0 = y + 1, y1 = y + height(font, area.wrapWidth()) - BELOW;
            g.fill(x0, y0, x1, y1, Palette.argb(color, on ? 0x24 : 0x12));
            RpgFrame.outline(g, x0, y0, x1 - x0, y1 - y0, Palette.argb(color, on ? 0xB0 : 0x50));
            int textY = y0 + INSIDE_TOP;
            g.drawString(font, name, x0 + PAD, textY, 0xFFFFFF, true);
            g.drawString(font, state, x1 - PAD - font.width(state), textY, 0xFFFFFF, true);
            textY += NAME_H;
            for (FormattedCharSequence line : descriptionLines(font, area.wrapWidth())) {
                g.drawString(font, line, x0 + PAD, textY, on ? 0xE8E8E8 : Palette.TEXT_DIM, true);
                textY += LINE_H;
            }
            for (FormattedCharSequence line : formulaLines(font, area.wrapWidth())) {
                g.drawString(font, line, x0 + PAD, textY, Palette.TEXT_FAINT, true);
                textY += LINE_H;
            }
        }
    }

    /** Image d'un autre mod, dessinée telle quelle dans le corps. */
    record Image(ClientTooltipComponent component) implements Elem {
        @Override
        public int height(Font font, int wrapWidth) {
            return component.getHeight() + 3;
        }

        @Override
        public int width(Font font) {
            return component.getWidth(font);
        }

        @Override
        public void draw(GuiGraphics g, Font font, Area area, int y) {
            component.renderImage(font, area.left(), y + 1, g);
        }
    }
}
