package net.Kyap.ItemsRarity.compat.cinematic;

import dev.dyslexic.cinematic.api.AddonStations;
import net.Kyap.ItemsRarity.ItemsRarity;
import net.Kyap.ItemsRarity.screen.EnhancedAnvilBlockMenu;
import net.minecraft.core.Direction;

import java.util.List;

/**
 * Cinematic Interactions (optionnel) : la table de forgemagie devient un poste "cinématique" (on s'y
 * penche, l'objet et les runes sont posés sur la table, on frappe au marteau). Ce fichier n'est chargé
 * que si le mod est présent (voir ItemsRarity / ForgeStation pour la scène côté client).
 */
public final class CinematicCompat {
    private CinematicCompat() {}

    public static final String MOD_ID = "cinematic";

    public static void init() {
        AddonStations.register(new AddonStations.Definition(
                ItemsRarity.id("forge_table"),
                ItemsRarity.id("enhanced_anvil_block"),
                EnhancedAnvilBlockMenu.class,
                state -> List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)));
        ItemsRarity.LOGGER.info("Items Rarity : table de forgemagie enregistrée pour Cinematic Interactions");
    }

    /** Côté client : la scène elle-même. */
    public static void initClient() {
        dev.dyslexic.cinematic.api.client.AddonScenes.register(ItemsRarity.id("forge_table"),
                (pos, state, player, side) -> new ForgeStation(pos, side));
    }
}
