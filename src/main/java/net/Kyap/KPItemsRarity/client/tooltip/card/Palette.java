package net.Kyap.KPItemsRarity.client.tooltip.card;

import net.Kyap.KPItemsRarity.rarity.ModRarity;

/**
 * Les couleurs des fenêtres RPG (infobulles, carte de panoplie, fiche de personnage, forge), en 0xRRGGBB.
 * Une seule palette pour que tout le mod parle la même langue : vert = bonus, rouge = malus, or = au-dessus du
 * maximum, etc.
 */
public final class Palette {
    private Palette() {}

    // ---------------------------------------------------------------- Texte, du plus clair au plus discret

    public static final int TEXT = 0xE9ECF2;
    public static final int TEXT_SOFT = 0xC3C9D4;
    public static final int TEXT_DIM = 0x8790A3;
    public static final int TEXT_FAINT = 0x5C6477;

    // ---------------------------------------------------------------- Valeurs

    public static final int POSITIVE = 0x46E39A;
    public static final int NEGATIVE = 0xE85A5A;
    /** Jet au-dessus du maximum de sa fourchette (forgemagie). */
    public static final int OVERMAX = 0xFFC94A;
    /** Ligne exotique (forgemagie) : rose vif, qu'aucune rareté ni palier n'utilise. */
    public static final int EXO = 0xFF5FB8;
    /** Enchantements. */
    public static final int MAGIC = 0xC79BFF;
    public static final int DURABILITY = 0xC8A878;

    // ---------------------------------------------------------------- Raretés et paliers

    /** La couleur d'une rareté : cadre, nom, lueur. */
    public static int rarity(ModRarity rarity) {
        return switch (rarity) {
            case COMMON -> 0xA9AFBA;
            case UNCOMMON -> 0x5FD35F;
            case RARE -> 0x4F8EFF;
            case EPIC -> 0xB65CFF;
            case LEGENDARY -> 0xFFA62B;
            case MYTHIC -> 0xFF4A5A;
        };
    }

    /** Le plus haut palier de panoplie ; les paliers vont de 1 à MAX_TIER. */
    public static final int MAX_TIER = 5;

    /**
     * La couleur d'un PALIER de panoplie : une famille de métaux, volontairement loin des couleurs de rareté pour
     * qu'on ne confonde jamais "palier de la panoplie" et "qualité du jet".
     */
    public static int tier(int tier) {
        return switch (Math.max(1, Math.min(MAX_TIER, tier))) {
            case 1 -> 0xC8804F; // cuivre
            case 2 -> 0xD2D8E0; // argent
            case 3 -> 0xEAD9A0; // or pâle (loin de l'orange légendaire)
            case 4 -> 0x7FD0D6; // mithril (bleu-vert glacé, loin du bleu rare)
            default -> 0xB7A3B3; // netherite (cendre)
        };
    }

    // ---------------------------------------------------------------- Calculs

    /** La couleur avec une opacité (0 transparent, 255 opaque), pour GuiGraphics. */
    public static int argb(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    /** Mélange de a vers b : t = 0 donne a, t = 1 donne b. */
    public static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }
}
