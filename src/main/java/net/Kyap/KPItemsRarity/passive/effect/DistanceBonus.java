package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;

/** distance_bonus : un tir à plus de min_distance blocs gagne per_block par bloc (au plus max). */
public final class DistanceBonus implements PassiveEffect {
    @Override
    public String id() {
        return "distance_bonus";
    }

    @Override
    public void onAttack(Attack hit, PassiveDefinition p) {
        if (!hit.projectile()) return;
        double over = hit.attacker().distanceTo(hit.target()) - p.num("min_distance", 10);
        if (over <= 0) return;
        hit.multiply(1 + Math.min(p.num("max", 0.3), over * p.num("per_block", 0.02)));
    }
}
