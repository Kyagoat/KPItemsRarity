package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;

/** ally_strength : les autres joueurs à moins de radius blocs du porteur font + bonus de dégâts (Bannière). */
public final class AllyStrength implements PassiveEffect {
    @Override
    public String id() {
        return "ally_strength";
    }

    @Override
    public void onAllyAttack(Attack hit, PassiveDefinition p) {
        hit.multiply(1 + p.num("bonus", 0.1));
    }
}
