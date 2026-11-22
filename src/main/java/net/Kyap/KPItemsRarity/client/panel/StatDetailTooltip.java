package net.Kyap.KPItemsRarity.client.panel;

import net.Kyap.KPItemsRarity.client.tooltip.card.FrameStyle;
import net.Kyap.KPItemsRarity.client.tooltip.card.Palette;
import net.Kyap.KPItemsRarity.client.tooltip.card.RpgFrame;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Infobulle d'une stat de la fiche, dessinée à la main (même cadre que les infobulles d'objets) : texte coupé
 * en lignes étroites, et pour chaque source "valeur  [icône] nom".
 */
final class StatDetailTooltip {
    private StatDetailTooltip() {}

    /** Icône de 10 px dans une petite case de 12 px (le même cadre que les pièces de la carte de panoplie). */
    private static final int ICON = 10, BOX = 12, ICON_LINE = 14;
    private static final int PAD = 5, TEXT_WIDTH = 170;

    private static final int LINE = 10;
    private static final int BONUS = 0x7FE08A, MALUS = 0xFF6B6B, BONUS_BG = 0x4040FF80, MALUS_BG = 0x40FF4040;

    static void render(GuiGraphics g, Font font, List<StatDetail> details, int mouseX, int mouseY) {
        int w = 0, h = 0;
        for (StatDetail d : details) {
            w = Math.max(w, width(font, d));
            h += height(font, d);
        }
        int boxW = w + 2 * PAD, boxH = h + 2 * PAD - 1;
        int x = mouseX + 12, y = mouseY - 12;
        if (x + boxW > g.guiWidth() - 4) x = Math.max(4, mouseX - 16 - boxW);
        if (y + boxH > g.guiHeight() - 4) y = g.guiHeight() - 4 - boxH;
        y = Math.max(4, y);

        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        RpgFrame.draw(g, x, y, boxW, boxH, 0, FrameStyle.plain(Palette.TEXT_FAINT));
        int lineY = y + PAD;
        for (StatDetail d : details) {
            switch (d) {
                case StatDetail.Text text -> drawText(g, font, text, x + PAD, lineY);
                case StatDetail.Source src -> drawSource(g, font, src, x + PAD, lineY);
            }
            lineY += height(font, d);
        }
        g.pose().popPose();
    }

    private static int width(Font font, StatDetail d) {
        return switch (d) {
            case StatDetail.Source src -> font.width(src.value()) + 4 + (src.icon().isEmpty() ? 0 : BOX + 3) + font.width(src.name());
            case StatDetail.Text text -> {
                int w = 0;
                for (FormattedCharSequence part : font.split(text.text(), TEXT_WIDTH)) w = Math.max(w, font.width(part));
                yield w;
            }
        };
    }

    private static int height(Font font, StatDetail d) {
        return switch (d) {
            case StatDetail.Source src -> src.icon().isEmpty() ? LINE : ICON_LINE;
            case StatDetail.Text text -> font.split(text.text(), TEXT_WIDTH).size() * LINE;
        };
    }

    private static void drawText(GuiGraphics g, Font font, StatDetail.Text text, int x, int y) {
        for (FormattedCharSequence part : font.split(text.text(), TEXT_WIDTH)) {
            g.drawString(font, part, x, y, 0xFFFFFF, true);
            y += LINE;
        }
    }

    /** "+3  [icône] Casque en bois" : valeur, icône de l'objet dans sa petite case, nom. */
    private static void drawSource(GuiGraphics g, Font font, StatDetail.Source src, int x, int y) {
        ItemStack icon = src.icon();
        boolean hasIcon = !icon.isEmpty();
        int textY = y + (hasIcon ? 3 : 0);
        g.drawString(font, src.value(), x, textY, 0xFFFFFF, true);
        x += font.width(src.value()) + 4;
        if (hasIcon) {
            drawIconBox(g, icon, x, y, src.malus());
            x += BOX + 3;
        }
        g.drawString(font, src.name(), x, textY, 0xFFFFFF, true);
    }

    /** Petite case : fond teinté et liseré à la couleur de la valeur (vert bonus, rouge malus), l'objet réduit dedans. */
    private static void drawIconBox(GuiGraphics g, ItemStack icon, int x, int y, boolean malus) {
        g.fill(x, y, x + BOX, y + BOX, malus ? MALUS_BG : BONUS_BG);
        RpgFrame.outline(g, x, y, BOX, BOX, Palette.argb(malus ? MALUS : BONUS, 0xB0));
        g.pose().pushPose();
        g.pose().translate(x + 1, y + 1, 0);
        g.pose().scale(ICON / 16f, ICON / 16f, 1f);
        g.renderFakeItem(icon, 0, 0);
        g.pose().popPose();
    }
}
