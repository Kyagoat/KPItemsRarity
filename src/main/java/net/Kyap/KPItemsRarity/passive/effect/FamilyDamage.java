package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;

/** family_damage : + ratio de dégâts contre une famille de créatures. Réglages : tag ("minecraft:illager"), ratio, source (any). */
public final class FamilyDamage implements PassiveEffect {
    @Override
    public String id() {
        return "family_damage";
    }

    @Override
    public void onAttack(Attack hit, PassiveDefinition p) {
        if (hit.from(p.str("source", "any")) && Families.is(hit.target(), p.str("tag", ""))) hit.multiply(1 + p.num("ratio", 0));
    }
}
