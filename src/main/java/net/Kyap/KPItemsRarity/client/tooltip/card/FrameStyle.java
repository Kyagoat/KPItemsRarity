package net.Kyap.KPItemsRarity.client.tooltip.card;

import net.Kyap.KPItemsRarity.rarity.ModRarity;

/**
 * L'allure du cadre d'une fenêtre.
 *
 * @param accent    couleur du cadre (rareté, palier de panoplie)
 * @param pulse     le cadre "respire" doucement
 * @param ornaments 0 : équerres d'angle ; 1 : + losanges sur les côtés et deuxième équerre ; 2 : + blasons
 */
public record FrameStyle(int accent, boolean pulse, int ornaments) {

    /** Un cadre simple, sans ornement ni respiration (petites fenêtres d'aide). */
    public static FrameStyle plain(int accent) {
        return new FrameStyle(accent, false, 0);
    }

    /** Selon la rareté de l'objet : Rare et Épique ornés, Légendaire et Mythique ornés et vivants. */
    public static FrameStyle of(ModRarity rarity) {
        int accent = Palette.rarity(rarity);
        return switch (rarity) {
            case COMMON, UNCOMMON -> new FrameStyle(accent, false, 0);
            case RARE, EPIC -> new FrameStyle(accent, false, 1);
            case LEGENDARY, MYTHIC -> new FrameStyle(accent, true, 2);
        };
    }

    /** Selon le palier d'une panoplie : ornée à partir du palier 3, vivante au dernier palier. */
    public static FrameStyle ofTier(int tier) {
        int ornaments = tier >= Palette.MAX_TIER ? 2 : tier >= 3 ? 1 : 0;
        return new FrameStyle(Palette.tier(tier), tier >= Palette.MAX_TIER, ornaments);
    }
}
