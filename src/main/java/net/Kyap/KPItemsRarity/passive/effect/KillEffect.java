package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.Allies;
import net.Kyap.KPItemsRarity.passive.CritTracker;
import net.Kyap.KPItemsRarity.passive.PassiveCooldowns;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * kill_effect : chaque monstre tué (pas les animaux) donne un effet au porteur. Réglages : effect, seconds,
 * amplifier (voir Effects) ; max_stacks (chaque kill monte l'effet d'un niveau, jusqu'à ce nombre, et relance sa
 * durée) ; next_crit (le coup suivant est un critique) ; drop_aggro (les monstres à 16 blocs qui le ciblaient le
 * perdent de vue) ; any_kill (compte aussi les animaux). La recharge du passif évite de le relancer à chaque kill.
 */
public final class KillEffect implements PassiveEffect {
    @Override
    public String id() {
        return "kill_effect";
    }

    @Override
    public void onKill(Kill kill, PassiveDefinition p) {
        Player killer = kill.killer();
        if (!(kill.victim() instanceof Enemy) && !p.flag("any_kill")) return;
        if (!PassiveCooldowns.ready(killer, p)) return;
        MobEffectInstance effect = Effects.of(p);
        if (effect != null) {
            int amplifier = effect.getAmplifier();
            int stacks = (int) p.num("max_stacks", 1);
            MobEffectInstance current = killer.getEffect(effect.getEffect());
            if (stacks > 1 && current != null) amplifier = Math.min(stacks - 1, current.getAmplifier() + 1);
            killer.addEffect(new MobEffectInstance(effect.getEffect(), effect.getDuration(), amplifier, false, false, true), killer);
        }
        if (p.flag("next_crit")) CritTracker.forceNext(killer);
        if (p.flag("drop_aggro")) {
            for (Mob mob : killer.level().getEntitiesOfClass(Mob.class, killer.getBoundingBox().inflate(16), m -> Allies.targets(m, killer))) {
                Allies.forget(mob);
            }
        }
    }
}
