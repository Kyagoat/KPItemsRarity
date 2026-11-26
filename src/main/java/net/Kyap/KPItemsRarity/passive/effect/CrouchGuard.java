package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * crouch_guard (Roulé en boule) : accroupi, le porteur prend ratio de dégâts en moins, mais ses coups et ses tirs ne
 * font rien. reflect_projectiles : accroupi, les projectiles rebondissent et blessent leur tireur à sa place.
 */
public final class CrouchGuard implements PassiveEffect {
    @Override
    public String id() {
        return "crouch_guard";
    }

    @Override
    public boolean onDefend(Defense hit, PassiveDefinition p) {
        Player victim = hit.victim();
        if (!victim.isCrouching()) return false;
        if (p.flag("reflect_projectiles") && hit.projectile() && hit.source().getEntity() instanceof LivingEntity shooter && shooter != victim) {
            float damage = hit.event().getAmount();
            hit.event().setCanceled(true);
            shooter.hurt(victim.damageSources().thorns(victim), damage);
            return true;
        }
        hit.multiply(1 - p.num("ratio", 0.3));
        return false;
    }

    @Override
    public void onAttack(Attack hit, PassiveDefinition p) {
        if (hit.attacker().isCrouching()) hit.event().setCanceled(true);
    }

    @Override
    public boolean isWaiting(Player wearer, PassiveDefinition p) {
        return !wearer.isCrouching();
    }
}
