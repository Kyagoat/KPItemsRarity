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
            g.fill(area.left(), y + 1, area.right(), y + 2, RpgFrame.argb(RpgFrame.TEXT_FAINT, 0x60));
        }
    }

    /** Texte libre (lore, descriptions, textes des autres mods) : retourné à la largeur ; un paragraphe vide = ligne vide. */
    record Text(List<Component> paragraphs, int color) implements Elem {
        private static final int TEXT_LINE_H = 10;

        private List<FormattedCharSequence> lines(Font font, int wrapWidth) {
            List<FormattedCharSequence> out = new ArrayList<>();
            for (Component c : paragraphs) {
                if (c.getString().isEmpty()) out.add(FormattedCharSequence.EMPTY);
                else out.addAll(font.split(c, Math.max(40, wrapWidth)));
            }
            return out;
        }

        @Override
        public int height(Font font, int wrapWidth) {
            return lines(font, wrapWidth).size() * TEXT_LINE_H;
        }

        @Override
        public int width(Font font) {
            return 0;
        }

        @Override
        public void draw(GuiGraphics g, Font font, Area area, int y) {
            for (FormattedCharSequence line : lines(font, area.wrapWidth())) {
                g.drawString(font, line, area.left(), y, color, true);
                y += TEXT_LINE_H;
            }
        }
    }

    // ---------------------------------------------------------------- Encadrés

    /**
     * Encadré d'un passif : nom (couleur de rareté) et voyant d'état sur la première ligne, puis sa description
     * et sa formule, retournées à la largeur de la fenêtre. Plus pâle quand le passif n'est pas actif.
     */
    record Passive(Component name, Component state, Component desc, @Nullable Component formula,
                   int color, boolean on) implements Elem {
        private static final int PAD = 5;

        private List<FormattedCharSequence> descLines(Font font, int wrapWidth) {
            return font.split(desc, Math.max(60, wrapWidth - 10));
        }

        private List<FormattedCharSequence> formulaLines(Font font, int wrapWidth) {
            return formula == null ? List.of() : font.split(formula, Math.max(60, wrapWidth - 10));
        }

        @Override
        public int height(Font font, int wrapWidth) {
            return 4 + 11 + (descLines(font, wrapWidth).size() + formulaLines(font, wrapWidth).size()) * 10 + 4 + 3;
        }

        @Override
        public int width(Font font) {
            return 0;
        }

        @Override
        public void draw(GuiGraphics g, Font font, Area area, int y) {
            int x0 = area.left(), x1 = area.textRight(), y0 = y + 1, y1 = y + height(font, area.wrapWidth()) - 3;
            g.fill(x0, y0, x1, y1, RpgFrame.argb(color, on ? 0x24 : 0x12));
            RpgFrame.outline(g, x0, y0, x1 - x0, y1 - y0, RpgFrame.argb(color, on ? 0xB0 : 0x50));
            int ty = y0 + 4;
            g.drawString(font, name, x0 + PAD, ty, 0xFFFFFF, true);
            g.drawString(font, state, x1 - PAD - font.width(state), ty, 0xFFFFFF, true);
            ty += 11;
            for (FormattedCharSequence line : descLines(font, area.wrapWidth())) {
                g.drawString(font, line, x0 + PAD, ty, on ? 0xE8E8E8 : RpgFrame.TEXT_DIM, true);
                ty += 10;
            }
            for (FormattedCharSequence line : formulaLines(font, area.wrapWidth())) {
                g.drawString(font, line, x0 + PAD, ty, RpgFrame.TEXT_FAINT, true);
                ty += 10;
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
