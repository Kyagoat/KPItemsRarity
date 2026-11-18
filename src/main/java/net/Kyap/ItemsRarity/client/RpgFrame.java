package net.Kyap.ItemsRarity.client;

import net.Kyap.ItemsRarity.util.ModRarities.ModRarity;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

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

    /** Couleur d'une panoplie selon son palier (1 bronze, 2 acier, 3 or, 4 améthyste, 5 braise). */
    /**
     * Couleur d'un PALIER de panoplie : une famille de métaux, volontairement différente des raretés
     * (gris, vert, bleu, violet, orange, rouge) pour qu'on ne confonde jamais "palier" et "qualité du jet".
     * Cuivre, argent, or pâle, mithril, netherite.
     */
    public static int tierColor(int tier) {
        return switch (Math.max(1, Math.min(5, tier))) {
            case 1 -> 0xC8804F; // cuivre
            case 2 -> 0xD2D8E0; // argent
            case 3 -> 0xEAD9A0; // or pâle (champagne, loin de l'orange légendaire)
            case 4 -> 0x7FD0D6; // mithril (bleu-vert glacé, loin du bleu rare)
            default -> 0xB7A3B3; // netherite (cendre)
        };
    }

    /** Palier en pastilles "◆◆◆◇◇" : lisible sans dépendre de la couleur. */
    public static net.minecraft.network.chat.Component tierPips(int tier) {
        int t = Math.max(1, Math.min(5, tier));
        int color = tierColor(t);
        return net.minecraft.network.chat.Component.literal("◆".repeat(t)).withStyle(s -> s.withColor(color))
                .append(net.minecraft.network.chat.Component.literal("◇".repeat(5 - t)).withStyle(s -> s.withColor(TEXT_FAINT)));
    }

    /** Niveau d'ornement d'une panoplie selon son palier. */
    public static int ornateLevelForTier(int tier) {
        return tier >= 5 ? 2 : tier >= 3 ? 1 : 0;
    }

    public static void draw(GuiGraphics g, int x, int y, int w, int h, int headerHeight, int accent, boolean pulse) {
        draw(g, x, y, w, h, headerHeight, accent, pulse, 0);
    }

    /**
     * @param headerHeight hauteur de la zone d'en-tête (plus claire, avec une lueur de la couleur d'accent), 0 = aucune
     */
    public static void draw(GuiGraphics g, int x, int y, int w, int h, int headerHeight, int accent, boolean pulse, int ornate) {
        float p = pulse ? 0.7f + 0.3f * (float) Math.sin(Util.getMillis() / 380.0) : 1.0f;

        // Ombre portée
        g.fill(x + 2, y + 2, x + w + 3, y + h + 3, 0x55000000);

        // Fond
        g.fillGradient(x, y, x + w, y + h, BG_TOP, BG_BOTTOM);
        if (headerHeight > 0) {
            g.fill(x, y, x + w, y + headerHeight, HEADER);
            g.fillGradient(x + 1, y + 1, x + w - 1, y + Math.min(headerHeight, 26), argb(accent, (int) (0x46 * p)), argb(accent, 0));
            separator(g, x + 6, x + w - 6, y + headerHeight, accent);
        }

        // Contour sombre puis bordure à la couleur de la rareté
        g.fill(x - 1, y - 1, x + w + 1, y, OUTLINE);
        g.fill(x - 1, y + h, x + w + 1, y + h + 1, OUTLINE);
        g.fill(x - 1, y, x, y + h, OUTLINE);
        g.fill(x + w, y, x + w + 1, y + h, OUTLINE);

        int border = argb(accent, (int) (0xB4 * p));
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y + 1, x + 1, y + h - 1, border);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, border);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x14FFFFFF);

        // Ornements d'angle
        int bright = argb(mix(accent, 0xFFFFFF, 0.35f), 0xFF);
        corner(g, x - 1, y - 1, 1, 1, bright);
        corner(g, x + w, y - 1, -1, 1, bright);
        corner(g, x - 1, y + h, 1, -1, bright);
        corner(g, x + w, y + h, -1, -1, bright);

        if (ornate >= 1) {
            // Losanges au milieu des côtés
            diamond(g, x, y + h / 2, bright);
            diamond(g, x + w - 1, y + h / 2, bright);
            // Deuxième équerre, plus discrète
            int soft = argb(accent, 0x90);
            corner(g, x + 2, y + 2, 1, 1, soft);
            corner(g, x + w - 3, y + 2, -1, 1, soft);
            corner(g, x + 2, y + h - 3, 1, -1, soft);
            corner(g, x + w - 3, y + h - 3, -1, -1, soft);
        }
        if (ornate >= 2) {
            // Blason en haut et en bas, au centre
            int cx = x + w / 2;
            crest(g, cx, y, bright, argb(accent, 0xFF));
            crest(g, cx, y + h - 1, bright, argb(accent, 0xFF));
        }
    }

    private static void diamond(GuiGraphics g, int cx, int cy, int color) {
        g.fill(cx - 1, cy - 2, cx + 2, cy + 3, color);
        g.fill(cx - 2, cy - 1, cx + 3, cy + 2, color);
        g.fill(cx, cy - 3, cx + 1, cy + 4, color);
    }

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
    public static int sectionTitle(GuiGraphics g, net.minecraft.client.gui.Font font, net.minecraft.network.chat.Component title,
                                   int x, int y, int width, int color) {
        g.drawString(font, title, x, y, color, true);
        int lx = x + font.width(title) + 5;
        if (lx < x + width) g.fill(lx, y + 4, x + width, y + 5, argb(color, 0x50));
        return y + 12;
    }

    private static void corner(GuiGraphics g, int x, int y, int dx, int dy, int color) {
        // Équerre de 5 px + un point à l'intérieur
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
        int barX = barRight - 3;
        int barH = Math.max(12, view * view / Math.max(1, contentH));
        int barY = top + (int) ((view - barH) * (max > 0 ? offset / (float) max : 0f));
        g.fill(barX, top, barX + 3, bottom, 0x60000000);
        g.fill(barX + 1, top, barX + 2, bottom, argb(TEXT_FAINT, 0x60));
        g.fill(barX, barY, barX + 3, barY + barH, argb(accent, 0xF0));
        g.fill(barX, barY, barX + 1, barY + barH, argb(mix(accent, 0xFFFFFF, 0.5f), 0xF0));
        int arrowX = x + (width - font.width("▾")) / 2;
        int color = argb(mix(accent, 0xFFFFFF, 0.25f), 0xFF);
        if (offset < max) g.drawString(font, "▾", arrowX, bottom - 1, color, true);
        if (offset > 0) g.drawString(font, "▴", arrowX, top - 4, color, true);
    }
}
