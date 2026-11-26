package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;
import net.minecraft.world.entity.player.Player;

/**
 * big_hit_reduction (Inébranlable) : la part d'un coup qui dépasse "above" de la vie max (armure comptée) est
 * réduite de ratio. Réglages : above (0.25), ratio (0.5).
 */
public final class BigHitReduction implements PassiveEffect {
    @Override
    public String id() {
        return "big_hit_reduction";
    }

    @Override
    public void onHurt(Hurt hit, PassiveDefinition p) {
        Player victim = hit.victim();
        float damage = hit.event().getNewDamage();
        double limit = victim.getMaxHealth() * p.num("above", 0.25);
        if (damage > limit) hit.event().setNewDamage((float) (limit + (damage - limit) * Math.max(0.0, 1 - p.num("ratio", 0.5))));
    }
}
