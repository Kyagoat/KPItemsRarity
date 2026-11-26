package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;
import net.minecraft.world.entity.Entity;

/**
 * family_resistance : − ratio de dégâts reçus d'une famille de créatures, coups et tirs (la flèche d'un squelette
 * compte pour le squelette). Réglages : tag ("minecraft:undead"), ratio.
 */
public final class FamilyResistance implements PassiveEffect {
    @Override
    public String id() {
        return "family_resistance";
    }

    @Override
    public boolean onDefend(Defense hit, PassiveDefinition p) {
        Entity attacker = hit.source().getEntity();
        if (attacker != null && Families.is(attacker, p.str("tag", ""))) hit.multiply(Math.max(0.0, 1 - p.num("ratio", 0)));
        return false;
    }
}
