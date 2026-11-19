package net.Kyap.KPItemsRarity;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration "common" (config/kpitemsrarity-common.toml).
 * Les taux et effets restent dans les datapacks ; ici on ne met que les réglages techniques.
 */
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue DISABLE_VANILLA_CRITS = BUILDER
            .comment("Désactive le coup critique vanilla (saut + coup). Les crits viennent alors uniquement",
                     "de la chance de critique du joueur (kpitemsrarity:crit_chance, 5 % de base).")
            .define("disableVanillaCrits", true);

    public static final ModConfigSpec.IntValue INVENTORY_SCAN_INTERVAL = BUILDER
            .comment("Toutes les combien de ticks l'inventaire des joueurs est vérifié pour donner une rareté",
                     "aux équipements qui n'en ont pas (échanges villageois, table de forgeron, commandes...).",
                     "20 ticks = 1 seconde. 0 = désactivé.")
            .defineInRange("inventoryScanInterval", 20, 0, 1200);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
