package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;

/** strength_in_water : + bonus de dégâts quand le porteur est dans l'eau. */
public final class StrengthInWater implements PassiveEffect {
    @Override
    public String id() {
        return "strength_in_water";
    }

    @Override
    public void onAttack(Attack hit, PassiveDefinition p) {
        if (hit.attacker().isInWaterOrRain() && hit.attacker().isInWater()) hit.multiply(1 + p.num("bonus", 0.15));
    }
}
