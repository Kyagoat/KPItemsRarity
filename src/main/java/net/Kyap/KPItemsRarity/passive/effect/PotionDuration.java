package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * potion_duration : les effets bénéfiques que le porteur donne (potions bues ou jetées, sur lui ou ses alliés)
 * durent plus longtemps. Réglage : bonus (0.5 = +50 %). Les effets instantanés et infinis ne changent pas.
 */
public final class PotionDuration implements PassiveEffect {
    @Override
    public String id() {
        return "potion_duration";
    }

    @Override
    public void onEffectGiven(EffectGiven given, PassiveDefinition p) {
        MobEffectInstance effect = given.effect();
        if (!effect.getEffect().value().isBeneficial() || effect.getEffect().value().isInstantenous()
                || effect.isInfiniteDuration() || effect.getDuration() <= 1) return;
        int longer = (int) Math.round(effect.getDuration() * (1 + p.num("bonus", 0.5)));
        // Le même niveau avec une durée plus longue : update() n'allonge que la durée.
        effect.update(new MobEffectInstance(effect.getEffect(), longer, effect.getAmplifier(),
                effect.isAmbient(), effect.isVisible(), effect.showIcon()));
    }
}
