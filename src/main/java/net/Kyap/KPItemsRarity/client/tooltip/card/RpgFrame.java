package net.Kyap.KPItemsRarity.client.tooltip.card;

import net.Kyap.KPItemsRarity.rarity.ModRarity;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/**
 * Cadre commun des infobulles RPG et de la carte de panoplie.
 * Tout est dessiné avec des aplats (pas de texture) : le futur resource pack pourra le remplacer
 * en ajoutant des sprites, sans toucher à la mise en page.
 */
public final class RpgFrame {
    private RpgFrame() {}

    public static final int BG_TOP = 0xFF161B2A;
    public static final int BG_BOTTOM = 0xFF0D1019;
    public static final int HEADER = 0xFF1C2234;
    public static final int OUTLINE = 0xFF05070C;
    private static final int SHADOW = 0x55000000, SHEEN = 0x14FFFFFF, SCROLL_TRACK = 0x60000000, SCROLL_BAR_W = 3;
    /** Paliers de panoplie : 1 à 5. */
    public static final int MAX_TIER = 5;

    public static final int TEXT = 0xE9ECF2;
    public static final int TEXT_SOFT = 0xC3C9D4;
    public static final int TEXT_DIM = 0x8790A3;
    public static final int TEXT_FAINT = 0x5C6477;
    public static final int POSITIVE = 0x46E39A;
    public static final int NEGATIVE = 0xE85A5A;
    public static final int MAGIC = 0xC79BFF;
    /** Ligne exotique (forgemagie) : rose vif, une couleur qu'aucune rareté ni palier n'utilise. */
    public static final int EXO = 0xFF5FB8;
    /** Jet au-dessus du maximum théorique (forgemagie) : or. */
    public static final int OVERMAX = 0xFFC94A;
    public static final int GOLD = 0xE8C25A;
    public static final int DURABILITY = 0xC8A878;

    /** Couleur d'accent de chaque rareté (bordure, nom, lueur). */
    public static int rarityColor(ModRarity rarity) {
        return switch (rarity) {
            case COMMON -> 0xA9AFBA;
            case UNCOMMON -> 0x5FD35F;
            case RARE -> 0x4F8EFF;
            case EPIC -> 0xB65CFF;
            case LEGENDARY -> 0xFFA62B;
            case MYTHIC -> 0xFF4A5A;
        };
    }

    /** Légendaire et Mythique : la bordure "respire". */
    public static boolean pulses(ModRarity rarity) {
        return rarity == ModRarity.LEGENDARY || rarity == ModRarity.MYTHIC;
    }

    public static int argb(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    /** Niveau d'ornement du cadre : 0 Commun/Peu commun, 1 Rare/Épique, 2 Légendaire/Mythique. */
    public static int ornateLevel(ModRarity rarity) {
        return switch (rarity) {
            case COMMON, UNCOMMON -> 0;
            case RARE, EPIC -> 1;
            case LEGENDARY, MYTHIC -> 2;
        };
    }

    /**
     * Couleur d'un PALIER de panoplie : une famille de métaux, volontairement différente des raretés
     * (gris, vert, bleu, violet, orange, rouge) pour qu'on ne confonde jamais "palier" et "qualité du jet".
     */
    public static int tierColor(int tier) {
        return switch (clampTier(tier)) {
            case 1 -> 0xC8804F; // cuivre
            case 2 -> 0xD2D8E0; // argent
            case 3 -> 0xEAD9A0; // or pâle (champagne, loin de l'orange légendaire)
            case 4 -> 0x7FD0D6; // mithril (bleu-vert glacé, loin du bleu rare)
            default -> 0xB7A3B3; // netherite (cendre)
        };
    }

    private static int clampTier(int tier) {
        return Math.max(1, Math.min(MAX_TIER, tier));
    }

    /** Palier en pastilles "◆◆◆◇◇" : lisible sans dépendre de la couleur. */
    public static Component tierPips(int tier) {
        int t = clampTier(tier);
        int color = tierColor(t);
        return Component.literal("◆".repeat(t)).withStyle(s -> s.withColor(color))
                .append(Component.literal("◇".repeat(MAX_TIER - t)).withStyle(s -> s.withColor(TEXT_FAINT)));
    }

    /** Niveau d'ornement d'une panoplie selon son palier. */
    public static int ornateLevelForTier(int tier) {
        return tier >= 5 ? 2 : tier >= 3 ? 1 : 0;
    }

    public static void draw(GuiGraphics g, int x, int y, int w, int h, int headerHeight, int accent, boolean pulse) {
        draw(g, x, y, w, h, headerHeight, accent, pulse, 0);
    }

    /**
     * Le cadre complet : ombre, fond en dégradé, en-tête éventuel, contour sombre, bordure à la couleur
     * d'accent, puis les ornements selon le niveau (équerres d'angle ; + losanges ; + blasons).
     * @param headerHeight hauteur de la zone d'en-tête (plus claire, avec une lueur de la couleur d'accent), 0 = aucune
     * @param pulse        la bordure "respire" (Légendaire, Mythique)
     * @param ornate       niveau d'ornement, voir {@link #ornateLevel}
     */
    public static void draw(GuiGraphics g, int x, int y, int w, int h, int headerHeight, int accent, boolean pulse, int ornate) {
        float glow = pulse ? breathing() : 1.0f;
        g.fill(x + 2, y + 2, x + w + 3, y + h + 3, SHADOW);
        g.fillGradient(x, y, x + w, y + h, BG_TOP, BG_BOTTOM);
        if (headerHeight > 0) drawHeader(g, x, y, w, headerHeight, accent, glow);
        outline(g, x - 1, y - 1, w + 2, h + 2, OUTLINE);
        outline(g, x, y, w, h, argb(accent, (int) (0xB4 * glow)));
        g.fill(x + 1, y + 1, x + w - 1, y + 2, SHEEN);
        drawOrnaments(g, x, y, w, h, accent, ornate);
    }

    /** Entre 0.4 et 1, au rythme d'une respiration lente. */
    private static float breathing() {
        return 0.7f + 0.3f * (float) Math.sin(Util.getMillis() / 380.0);
    }

    private static void drawHeader(GuiGraphics g, int x, int y, int w, int headerHeight, int accent, float glow) {
        g.fill(x, y, x + w, y + headerHeight, HEADER);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + Math.min(headerHeight, 26), argb(accent, (int) (0x46 * glow)), argb(accent, 0));
        separator(g, x + 6, x + w - 6, y + headerHeight, accent);
    }

    private static void drawOrnaments(GuiGraphics g, int x, int y, int w, int h, int accent, int ornate) {
        int bright = argb(mix(accent, 0xFFFFFF, 0.35f), 0xFF);
        corners(g, x - 1, y - 1, w + 2, h + 2, bright);
        if (ornate >= 1) {
            diamond(g, x, y + h / 2, bright);
            diamond(g, x + w - 1, y + h / 2, bright);
            corners(g, x + 2, y + 2, w - 4, h - 4, argb(accent, 0x90)); // deuxième équerre, plus discrète
        }
        if (ornate >= 2) {
            int cx = x + w / 2;
            crest(g, cx, y, bright, argb(accent, 0xFF));
            crest(g, cx, y + h - 1, bright, argb(accent, 0xFF));
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
        g.fill(x1, y, x2, y + 1, argb(accent, 0x60));
        int cx = (x1 + x2) / 2;
        int c = argb(mix(accent, 0xFFFFFF, 0.2f), 0xD0);
        g.fill(cx - 1, y - 1, cx + 1, y + 2, c);
        g.fill(cx - 2, y, cx + 2, y + 1, c);
    }

    /** Petit titre de section : "Enchantements ─────────". */
    public static int sectionTitle(GuiGraphics g, Font font, Component title,
                                   int x, int y, int width, int color) {
        g.drawString(font, title, x, y, color, true);
        int lx = x + font.width(title) + 5;
        if (lx < x + width) g.fill(lx, y + 4, x + width, y + 5, argb(color, 0x50));
        return y + 12;
    }

    /** Équerre de 5 px partant de (x, y) vers (dx, dy), plus un point à l'intérieur. */
    private static void corner(GuiGraphics g, int x, int y, int dx, int dy, int color) {
        int x2 = x + dx * 5, y2 = y + dy * 5;
        g.fill(Math.min(x, x2), y, Math.max(x, x2) + 1, y + 1, color);
        g.fill(x, Math.min(y, y2), x + 1, Math.max(y, y2) + 1, color);
        int px = x + dx * 2, py = y + dy * 2;
        g.fill(px, py, px + 1, py + 1, color);
    }

    /**
     * Habillage d'une zone qui défile, partagé par l'infobulle et la carte de panoplie :
     * barre de défilement et flèches ▴ / ▾.
     * À appeler APRÈS le contenu, scissor désactivé.
     */
    public static void scrollChrome(GuiGraphics g, Font font, int x, int width, int barRight, int top, int view,
                                    int contentH, int offset, int max, int accent) {
        int bottom = top + view;
        int barX = barRight - SCROLL_BAR_W;
        int thumbH = Math.max(12, view * view / Math.max(1, contentH));
        int thumbY = top + (int) ((view - thumbH) * (max > 0 ? offset / (float) max : 0f));
        g.fill(barX, top, barX + SCROLL_BAR_W, bottom, SCROLL_TRACK);
        g.fill(barX + 1, top, barX + 2, bottom, argb(TEXT_FAINT, 0x60));
        g.fill(barX, thumbY, barX + SCROLL_BAR_W, thumbY + thumbH, argb(accent, 0xF0));
        g.fill(barX, thumbY, barX + 1, thumbY + thumbH, argb(mix(accent, 0xFFFFFF, 0.5f), 0xF0));
        int arrowX = x + (width - font.width("▾")) / 2;
        int arrowColor = argb(mix(accent, 0xFFFFFF, 0.25f), 0xFF);
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
