package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;
import net.minecraft.world.entity.player.Player;

/**
 * context_prospection : + amount Prospection (en points, 30 = +30 %) dans un contexte (voir Contexts : nether, water...).
 * Lu par Prospecting au moment des drops ; rien d'autre à faire ici.
 */
public final class ContextProspection implements PassiveEffect {
    public static final String ID = "context_prospection";

    @Override
    public String id() {
        return ID;
    }

    /** La Prospection que ce passif donne en ce moment, en fraction (0,3 = +30 %). */
    public static double amount(Player player, PassiveDefinition p) {
        return Contexts.any(player, Contexts.of(p)) ? p.num("amount", 0) / 100.0 : 0;
    }

    @Override
    public boolean isWaiting(Player wearer, PassiveDefinition p) {
        return !Contexts.any(wearer, Contexts.of(p));
    }
}
