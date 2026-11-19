package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;

/** ricochet : un tir qui tue touche le monstre le plus proche (dans "radius" blocs) avec ratio des dégâts. */
public final class Ricochet implements PassiveEffect {
    /** Le rebond est lui-même un coup : il ne doit pas rebondir à son tour. */
    private boolean ricocheting;

    @Override
    public String id() {
        return "ricochet";
    }

    @Override
    public boolean onDealt(Dealt hit, PassiveDefinition p) {
        LivingEntity victim = hit.victim();
        if (ricocheting || victim.getHealth() > 0 || !(hit.source().getDirectEntity() instanceof Projectile)) return false;
        LivingEntity next = nearestEnemy(hit, p.num("radius", 8));
        if (next == null) return true;
        ricocheting = true;
        try {
            next.hurt(hit.source(), (float) (hit.damage() * p.num("ratio", 0.5)));
        } finally {
            ricocheting = false;
        }
        if (victim.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CRIT, next.getX(), next.getY(0.6), next.getZ(), 8, 0.2, 0.3, 0.2, 0.1);
        }
        return true; // un seul rebond par tir
    }

    private static LivingEntity nearestEnemy(Dealt hit, double radius) {
        LivingEntity victim = hit.victim();
        LivingEntity next = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity m : victim.level().getEntitiesOfClass(LivingEntity.class, victim.getBoundingBox().inflate(radius),
                e -> e != victim && e != hit.attacker() && e.isAlive() && e instanceof Enemy)) {
            double d = m.distanceToSqr(victim);
            if (d < best) {
                best = d;
                next = m;
            }
        }
        return next;
    }
}
