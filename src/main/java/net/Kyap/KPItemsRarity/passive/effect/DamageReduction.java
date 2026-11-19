package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;

/** damage_reduction : dégâts reçus × (1 − ratio). Réglages : source (projectile|melee|any), ratio. */
public final class DamageReduction implements PassiveEffect {
    @Override
    public String id() {
        return "damage_reduction";
    }

    @Override
    public boolean onDefend(Defense hit, PassiveDefinition p) {
        if (hit.from(p.str("source", "any"))) hit.multiply(Math.max(0.0, 1 - p.num("ratio", 0)));
        return false;
    }
}
