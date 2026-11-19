package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * repel : un coup de mêlée reçu repousse l'attaquant ("strength"). Avec min_fall, une chute d'au moins
 * min_fall blocs fait rebondir au lieu de blesser (comme fall_bounce, avec la même "strength").
 */
public final class Repel implements PassiveEffect {
    @Override
    public String id() {
        return "repel";
    }

    @Override
    public boolean onDefend(Defense hit, PassiveDefinition p) {
        Player victim = hit.victim();
        if (!hit.melee() || !(hit.source().getEntity() instanceof LivingEntity attacker) || attacker == victim) return false;
        attacker.knockback(p.num("strength", 0.8), victim.getX() - attacker.getX(), victim.getZ() - attacker.getZ());
        return true; // une seule repousse par coup
    }

    @Override
    public boolean onFall(Fall fall, PassiveDefinition p) {
        return p.num("min_fall", -1) >= 0 && FallBounce.bounce(fall, p);
    }
}
