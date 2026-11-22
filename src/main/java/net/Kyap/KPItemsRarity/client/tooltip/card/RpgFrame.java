package net.Kyap.KPItemsRarity.client.tooltip.card;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/**
 * Les dessins communs des fenêtres RPG : le cadre (ombre, fond, en-tête, bordure, ornements), les séparateurs,
 * les titres de section, la barre de défilement, et le texte coupé avec "…". Tout est fait d'aplats, sans texture :
 * un resource pack pourra plus tard poser des sprites par-dessus sans toucher à la mise en page.
 */
public final class RpgFrame {
    private RpgFrame() {}

    private static final int BACKGROUND_TOP = 0xFF161B2A, BACKGROUND_BOTTOM = 0xFF0D1019, HEADER = 0xFF1C2234, OUTLINE = 0xFF05070C;
    private static final int SHADOW = 0x55000000, SHEEN = 0x14FFFFFF, SCROLL_TRACK = 0x60000000, SCROLL_BAR_W = 3;

    /**
     * Le cadre complet : ombre, fond en dégradé, en-tête éventuel, contour sombre, bordure à la couleur du style,
     * puis ses ornements.
     * @param headerHeight hauteur de l'en-tête (plus clair, avec une lueur de la couleur du cadre), 0 = aucun
     */
    public static void draw(GuiGraphics g, int x, int y, int w, int h, int headerHeight, FrameStyle style) {
        float glow = style.pulse() ? breathing() : 1.0f;
        g.fill(x + 2, y + 2, x + w + 3, y + h + 3, SHADOW);
        g.fillGradient(x, y, x + w, y + h, BACKGROUND_TOP, BACKGROUND_BOTTOM);
        if (headerHeight > 0) drawHeader(g, x, y, w, headerHeight, style.accent(), glow);
        outline(g, x - 1, y - 1, w + 2, h + 2, OUTLINE);
        outline(g, x, y, w, h, Palette.argb(style.accent(), (int) (0xB4 * glow)));
        g.fill(x + 1, y + 1, x + w - 1, y + 2, SHEEN);
        drawOrnaments(g, x, y, w, h, style.accent(), style.ornaments());
    }

    /** Entre 0.4 et 1, au rythme d'une respiration lente. */
    private static float breathing() {
        return 0.7f + 0.3f * (float) Math.sin(Util.getMillis() / 380.0);
    }

    private static void drawHeader(GuiGraphics g, int x, int y, int w, int headerHeight, int accent, float glow) {
        g.fill(x, y, x + w, y + headerHeight, HEADER);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + Math.min(headerHeight, 26), Palette.argb(accent, (int) (0x46 * glow)), Palette.argb(accent, 0));
        separator(g, x + 6, x + w - 6, y + headerHeight, accent);
    }

    private static void drawOrnaments(GuiGraphics g, int x, int y, int w, int h, int accent, int ornaments) {
        int bright = Palette.argb(Palette.mix(accent, 0xFFFFFF, 0.35f), 0xFF);
        corners(g, x - 1, y - 1, w + 2, h + 2, bright);
        if (ornaments >= 1) {
            diamond(g, x, y + h / 2, bright);
            diamond(g, x + w - 1, y + h / 2, bright);
            corners(g, x + 2, y + 2, w - 4, h - 4, Palette.argb(accent, 0x90)); // deuxième équerre, plus discrète
        }
        if (ornaments >= 2) {
            int cx = x + w / 2;
            crest(g, cx, y, bright, Palette.argb(accent, 0xFF));
            crest(g, cx, y + h - 1, bright, Palette.argb(accent, 0xFF));
        }
    }

    /** Un contour d'un pixel à l'intérieur du rectangle (x, y, w, h). */
    public static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** Une équerre dans chacun des quatre coins du rectangle. */
    private static void corners(GuiGraphics g, int x, int y, int w, int h, int color) {
        int right = x + w - 1, bottom = y + h - 1;
        corner(g, x, y, 1, 1, color);
        corner(g, right, y, -1, 1, color);
        corner(g, x, bottom, 1, -1, color);
        corner(g, right, bottom, -1, -1, color);
    }

    /** Petit losange de 7 px centré sur (cx, cy). */
    private static void diamond(GuiGraphics g, int cx, int cy, int color) {
        g.fill(cx - 1, cy - 2, cx + 2, cy + 3, color);
        g.fill(cx - 2, cy - 1, cx + 3, cy + 2, color);
        g.fill(cx, cy - 3, cx + 1, cy + 4, color);
    }

    /** Blason : une barre et un losange, centrés sur (cx, cy). */
    private static void crest(GuiGraphics g, int cx, int cy, int bright, int accent) {
        g.fill(cx - 7, cy, cx + 8, cy + 1, accent);
        g.fill(cx - 3, cy - 1, cx + 4, cy + 2, accent);
        g.fill(cx - 1, cy - 2, cx + 2, cy + 3, bright);
        g.fill(cx, cy - 3, cx + 1, cy + 4, bright);
    }

    /** Trait fin avec un petit losange au centre. */
    public static void separator(GuiGraphics g, int x1, int x2, int y, int accent) {
        g.fill(x1, y, x2, y + 1, Palette.argb(accent, 0x60));
        int cx = (x1 + x2) / 2;
        int c = Palette.argb(Palette.mix(accent, 0xFFFFFF, 0.2f), 0xD0);
        g.fill(cx - 1, y - 1, cx + 1, y + 2, c);
        g.fill(cx - 2, y, cx + 2, y + 1, c);
    }

    /** Petit titre de section : "Enchantements ─────────". */
    public static void sectionTitle(GuiGraphics g, Font font, Component title, int x, int y, int width, int color) {
        g.drawString(font, title, x, y, color, true);
        int lineX = x + font.width(title) + 5;
        if (lineX < x + width) g.fill(lineX, y + 4, x + width, y + 5, Palette.argb(color, 0x50));
    }

    /** Équerre de 5 px partant de (x, y) vers (dx, dy), plus un point à l'intérieur. */
    private static void corner(GuiGraphics g, int x, int y, int dx, int dy, int color) {
        int x2 = x + dx * 5, y2 = y + dy * 5;
        g.fill(Math.min(x, x2), y, Math.max(x, x2) + 1, y + 1, color);
        g.fill(x, Math.min(y, y2), x + 1, Math.max(y, y2) + 1, color);
        int px = x + dx * 2, py = y + dy * 2;
        g.fill(px, py, px + 1, py + 1, color);
    }

    /** Barre de défilement et flèches ▴ / ▾ d'une zone qui défile, à dessiner APRÈS son contenu (découpe levée). */
    public static void scrollChrome(GuiGraphics g, Font font, int x, int width, int barRight, int top, int view,
                                    int contentH, int offset, int max, int accent) {
        int bottom = top + view;
        int barX = barRight - SCROLL_BAR_W;
        int thumbH = Math.max(12, view * view / Math.max(1, contentH));
        int thumbY = top + (int) ((view - thumbH) * (max > 0 ? offset / (float) max : 0f));
        g.fill(barX, top, barX + SCROLL_BAR_W, bottom, SCROLL_TRACK);
        g.fill(barX + 1, top, barX + 2, bottom, Palette.argb(Palette.TEXT_FAINT, 0x60));
        g.fill(barX, thumbY, barX + SCROLL_BAR_W, thumbY + thumbH, Palette.argb(accent, 0xF0));
        g.fill(barX, thumbY, barX + 1, thumbY + thumbH, Palette.argb(Palette.mix(accent, 0xFFFFFF, 0.5f), 0xF0));
        int arrowX = x + (width - font.width("▾")) / 2;
        int arrowColor = Palette.argb(Palette.mix(accent, 0xFFFFFF, 0.25f), 0xFF);
        if (offset < max) g.drawString(font, "▾", arrowX, bottom - 1, arrowColor, true);
        if (offset > 0) g.drawString(font, "▴", arrowX, top - 4, arrowColor, true);
    }

    /** Le texte tel quel s'il tient dans maxWidth, sinon coupé avec "…". */
    public static FormattedCharSequence fit(Font font, Component text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text.getVisualOrderText();
        var cut = font.substrByWidth(text, Math.max(0, maxWidth - font.width("…")));
        return Language.getInstance().getVisualOrder(
                FormattedText.composite(cut, FormattedText.of("…")));
    }
}
