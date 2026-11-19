package net.Kyap.KPItemsRarity.client.tooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Éléments du corps de l'infobulle RPG. Chacun sait sa hauteur, la largeur qu'il demande et comment se
 * dessiner dans la zone du corps.
 */
final class TooltipModel {
    private TooltipModel() {}

    /** Hauteur d'une ligne de texte du corps. */
    static final int LINE = 11;

    /**
     * Zone du corps : bords gauche et droit, largeur utile, place gardée à droite pour la barre de
     * défilement (0 sans défilement) et largeur où retourner le texte des passifs.
     */
    record Area(int left, int right, int inner, int gutter, int wrapWidth) {}

    /** Une ligne : texte à gauche, texte grisé aligné à droite (fourchette...), ou l'indice "[Maj]". */
    record Row(Component left, Component right, int indent, boolean hint) {
        Row(Component left, Component right, int indent) {
            this(left, right, indent, false);
        }

        /** La même ligne avec l'indice "[Maj] pour le détail" à droite. */
        Row withHint(Component hintText) {
            return new Row(left, hintText, indent, true);
        }
    }

    sealed interface Elem permits RowElem, TitleElem, GapElem, ImageElem, PassiveElem {
        int height(Font font, int wrapWidth);

        /** Largeur que le contenu demande à l'infobulle (0 : il s'adapte). */
        int width(Font font);

        void draw(GuiGraphics g, Font font, Area area, int y);
    }

    record RowElem(Row row) implements Elem {
        @Override
        public int height(Font font, int wrapWidth) {
            return LINE;
        }

        @Override
        public int width(Font font) {
            int w = row.indent() + font.width(row.left());
            int rw = row.hint() ? 0 : font.width(row.right());
            return rw > 0 ? w + 12 + rw : w;
        }

        @Override
        public void draw(GuiGraphics g, Font font, Area area, int y) {
            Component rt = row.right();
            // Indice "[Maj]" : seulement s'il tient à côté de la ligne
            if (row.hint() && font.width(row.left()) + row.indent() + 8 + font.width(rt) > area.inner() - area.gutter()) rt = Component.empty();
            int leftMax = area.inner() - area.gutter() - row.indent() - (font.width(rt) > 0 ? font.width(rt) + 6 : 0);
            g.drawString(font, RpgFrame.fit(font, row.left(), leftMax), area.left() + row.indent(), y, 0xFFFFFF, true);
            if (font.width(rt) > 0) g.drawString(font, rt, area.right() - area.gutter() - font.width(rt), y, 0xFFFFFF, true);
        }
    }

    record TitleElem(Component title, int color) implements Elem {
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

    record GapElem(int height) implements Elem {
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

    /** Image d'un autre mod (emplacements d'appât de Tide, contenu d'une sacoche...), dessinée dans le corps. */
    record ImageElem(ClientTooltipComponent component) implements Elem {
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

    /**
     * Encadré d'un passif : nom (couleur de rareté) et voyant d'état sur la première ligne,
     * puis sa description, retournée à la ligne selon la largeur de l'infobulle.
     */
    record PassiveElem(Component name, Component state, Component desc, @Nullable Component formula,
                       int color, boolean on) implements Elem {
        private List<FormattedCharSequence> descLines(Font font, int wrapWidth) {
            return font.split(desc, Math.max(60, wrapWidth - 10));
        }

        private List<FormattedCharSequence> lines(Font font, int wrapWidth) {
            List<FormattedCharSequence> out = new ArrayList<>(descLines(font, wrapWidth));
            if (formula != null) out.addAll(font.split(formula, Math.max(60, wrapWidth - 10)));
            return out;
        }

        @Override
        public int height(Font font, int wrapWidth) {
            return 4 + 11 + lines(font, wrapWidth).size() * 10 + 4 + 3;
        }

        @Override
        public int width(Font font) {
            return 0;
        }

        @Override
        public void draw(GuiGraphics g, Font font, Area area, int y) {
            int bx0 = area.left(), bx1 = area.right() - area.gutter(), by0 = y + 1, by1 = y + height(font, area.wrapWidth()) - 3;
            g.fill(bx0, by0, bx1, by1, RpgFrame.argb(color, on ? 0x24 : 0x12));
            int edge = RpgFrame.argb(color, on ? 0xB0 : 0x50);
            g.fill(bx0, by0, bx1, by0 + 1, edge);
            g.fill(bx0, by1 - 1, bx1, by1, edge);
            g.fill(bx0, by0, bx0 + 1, by1, edge);
            g.fill(bx1 - 1, by0, bx1, by1, edge);
            int ty = by0 + 4;
            g.drawString(font, name, bx0 + 5, ty, 0xFFFFFF, true);
            g.drawString(font, state, bx1 - 5 - font.width(state), ty, 0xFFFFFF, true);
            ty += 11;
            List<FormattedCharSequence> lines = lines(font, area.wrapWidth());
            int descCount = descLines(font, area.wrapWidth()).size();
            for (int i = 0; i < lines.size(); i++) {
                int textColor = i < descCount ? (on ? 0xE8E8E8 : RpgFrame.TEXT_DIM) : RpgFrame.TEXT_FAINT;
                g.drawString(font, lines.get(i), bx0 + 5, ty, textColor, true);
                ty += 10;
            }
        }
    }
}
