package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.PassiveCooldowns;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

/**
 * low_health_effect : un coup qui fait passer le porteur sous below_health (part de sa vie max) lui donne un effet
 * (Embaumement : armure et robustesse renforcées). Réglages : below_health, effect, seconds, amplifier ; une
 * recharge évite de le relancer à chaque coup.
 */
public final class LowHealthEffect implements PassiveEffect {
    @Override
    public String id() {
        return "low_health_effect";
    }

    @Override
    public void onTaken(Taken hit, PassiveDefinition p) {
        Player victim = hit.victim();
        if (!victim.isAlive() || isWaiting(victim, p)) return;
        MobEffectInstance effect = Effects.of(p);
        if (effect != null && PassiveCooldowns.ready(victim, p)) victim.addEffect(effect, victim);
    }

    /** N'agit que sous le seuil de vie. */
    @Override
    public boolean isWaiting(Player wearer, PassiveDefinition p) {
        return wearer.getHealth() > wearer.getMaxHealth() * p.num("below_health", 0.3);
    }
}
