package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;
import net.Kyap.KPItemsRarity.passive.SprintTracker;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * sprint_charge : après "seconds" de course, le coup de mêlée suivant fait + bonus de dégâts et repousse la cible
 * (knockback, en force de recul vanilla). Réglages : seconds (2), bonus (0.5), knockback (1.5).
 */
public final class SprintCharge implements PassiveEffect {
    @Override
    public String id() {
        return "sprint_charge";
    }

    @Override
    public void onAttack(Attack hit, PassiveDefinition p) {
        Player attacker = hit.attacker();
        if (!hit.melee() || isWaiting(attacker, p)) return;
        hit.multiply(1 + p.num("bonus", 0.5));
        float yaw = attacker.getYRot() * Mth.DEG_TO_RAD;
        hit.target().knockback(p.num("knockback", 1.5), Mth.sin(yaw), -Mth.cos(yaw));
        SprintTracker.reset(attacker);
    }

    /** Pas encore assez couru. */
    @Override
    public boolean isWaiting(Player wearer, PassiveDefinition p) {
        return SprintTracker.seconds(wearer) < p.num("seconds", 2);
    }
}
