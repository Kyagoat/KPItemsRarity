package net.Kyap.KPItemsRarity.api;

import net.Kyap.KPItemsRarity.client.panel.InventoryStatsPanel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Function;

/** Points d'entrée stables côté client (voir KPItemsRarityApi). À n'appeler que sur le client. */
public final class KPItemsRarityClientApi {
    private KPItemsRarityClientApi() {}

    /**
     * Affiche aussi la fiche de stats à côté d'un écran d'un autre mod, pour le personnage que donne
     * {@code subject} (ex. KP PNJ : l'équipement d'un villageois).
     */
    public static void registerStatsPanel(Class<?> screen, Function<Screen, LivingEntity> subject) {
        InventoryStatsPanel.registerScreen(screen, subject);
    }
}
