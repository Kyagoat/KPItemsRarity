package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;
import net.Kyap.KPItemsRarity.passive.PassiveStats;

/** bonus_damage : dégâts × (1 + ratio × stat en %). Réglages : source (arrow|melee|any), stat, ratio. */
public final class BonusDamage implements PassiveEffect {
    @Override
    public String id() {
        return "bonus_damage";
    }

    @Override
    public void onAttack(Attack hit, PassiveDefinition p) {
        if (!hit.from(p.str("source", "any"))) return;
        double bonus = p.num("ratio", 0) * PassiveStats.fraction(hit.attacker(), p.str("stat", "strength"));
        if (bonus != 0) hit.multiply(1 + bonus);
    }
}
