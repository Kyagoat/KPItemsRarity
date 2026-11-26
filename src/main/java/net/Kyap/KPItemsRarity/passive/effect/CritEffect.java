package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.CritTracker;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * crit_effect : un coup critique donne un effet à la cible (Givre mordant : lenteur ; Venin : poison).
 * Réglages : source (arrow|melee|any), effect, seconds, amplifier (voir Effects).
 */
public final class CritEffect implements PassiveEffect {
    @Override
    public String id() {
        return "crit_effect";
    }

    @Override
    public void onAttack(Attack hit, PassiveDefinition p) {
        if (!hit.from(p.str("source", "any")) || !CritTracker.critNow(hit.attacker())) return;
        MobEffectInstance effect = Effects.of(p);
        if (effect != null) hit.target().addEffect(effect, hit.attacker());
    }
}
