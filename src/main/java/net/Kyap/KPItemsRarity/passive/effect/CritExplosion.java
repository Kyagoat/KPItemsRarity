package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.CritTracker;
import net.Kyap.KPItemsRarity.passive.PassiveCooldowns;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** crit_explosion : un critique de mêlée sous max_health PV fait exploser la cible (radius ; ne casse pas de blocs). */
public final class CritExplosion implements PassiveEffect {
    @Override
    public String id() {
        return "crit_explosion";
    }

    @Override
    public void onAttack(Attack hit, PassiveDefinition p) {
        Player attacker = hit.attacker();
        if (!hit.melee() || !CritTracker.critNow(attacker) || isWaiting(attacker, p) || !PassiveCooldowns.ready(attacker, p)) return;
        LivingEntity target = hit.target();
        attacker.level().explode(attacker, target.getX(), target.getY(0.5), target.getZ(),
                (float) p.num("radius", 2.5), Level.ExplosionInteraction.NONE);
    }

    /** N'agit que sous max_health PV. */
    @Override
    public boolean isWaiting(Player wearer, PassiveDefinition p) {
        return wearer.getHealth() > p.num("max_health", 2.0);
    }
}
