package net.Kyap.KPItemsRarity;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Réglages d'affichage (config/kpitemsrarity-client.toml). */
public final class ClientConfig {
    private ClientConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue RPG_TOOLTIPS = BUILDER
            .comment("Infobulle façon RPG pour tous les objets (grand aperçu de l'objet, dégâts en gros,",
                     "cadre à la couleur de la rareté). false = infobulle classique avec les lignes du mod.")
            .define("rpgTooltips", true);

    public static final ModConfigSpec.IntValue ICON_SIZE = BUILDER
            .comment("Taille en pixels de l'aperçu de l'objet dans l'infobulle d'un équipement.",
                     "Les autres objets utilisent les trois quarts.")
            .defineInRange("iconSize", 24, 16, 96);

    public static final ModConfigSpec.BooleanValue ANIMATED_ICON = BUILDER
            .comment("Aperçu animé dans l'infobulle (lévitation + l'objet se retourne de temps en temps).")
            .define("animatedIcon", true);

    public static final ModConfigSpec.IntValue MAX_BODY_HEIGHT = BUILDER
            .comment("Hauteur maximale (pixels) de la partie stats de l'infobulle. Au-delà, elle défile à la molette",
                     "(l'en-tête et la durabilité restent affichés).")
            .defineInRange("maxBodyHeight", 80, 30, 1000);

    public static final ModConfigSpec.IntValue TOOLTIP_WIDTH = BUILDER
            .comment("Largeur minimale (pixels) des infobulles et de la carte de panoplie.")
            .defineInRange("tooltipWidth", 150, 120, 400);

    public static final ModConfigSpec.IntValue TOOLTIP_MAX_WIDTH = BUILDER
            .comment("Largeur maximale : une infobulle avec beaucoup de texte s'élargit jusque-là au lieu de s'allonger.")
            .defineInRange("tooltipMaxWidth", 280, 120, 500);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static int tooltipMaxWidth() {
        return SPEC.isLoaded() ? TOOLTIP_MAX_WIDTH.get() : 280;
    }

    public static int tooltipWidth() {
        return SPEC.isLoaded() ? TOOLTIP_WIDTH.get() : 150;
    }

    public static int maxBodyHeight() {
        return SPEC.isLoaded() ? MAX_BODY_HEIGHT.get() : 80;
    }

    public static boolean animatedIcon() {
        return !SPEC.isLoaded() || ANIMATED_ICON.get();
    }

    public static boolean rpgTooltips() {
        return !SPEC.isLoaded() || RPG_TOOLTIPS.get();
    }

    public static int iconSize() {
        return SPEC.isLoaded() ? ICON_SIZE.get() : 24;
    }
}
