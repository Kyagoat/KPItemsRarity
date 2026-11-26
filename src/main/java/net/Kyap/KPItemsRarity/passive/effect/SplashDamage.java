package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * splash_damage (Souffle) : un coup ou un tir sur "every_nth" touche aussi les monstres autour de la cible, avec
 * ratio de ses dégâts (souffle du dragon : l'armure ne compte pas). Réglages : every_nth (5), ratio (0.5),
 * radius (3), max_targets (6). Le souffle ne relance pas les passifs du porteur.
 */
public final class SplashDamage implements PassiveEffect {
    private final Map<Player, Integer> hits = new WeakHashMap<>();
    private static boolean splashing;

    /** Un souffle est en cours : ses dégâts ne passent pas par les passifs. */
    public static boolean splashing() {
        return splashing;
    }

    @Override
    public String id() {
        return "splash_damage";
    }

    @Override
    public boolean onDealt(Dealt hit, PassiveDefinition p) {
        Player attacker = hit.attacker();
        boolean direct = hit.source().getDirectEntity() == attacker || hit.source().getDirectEntity() instanceof Projectile;
        if (splashing || !direct || attacker == hit.victim()) return false;
        int count = hits.merge(attacker, 1, Integer::sum);
        if (count < (int) Math.max(1, p.num("every_nth", 5))) return false;
        hits.put(attacker, 0);
        LivingEntity center = hit.victim();
        double radius = Math.min(8, p.num("radius", 3));
        List<LivingEntity> targets = center.level().getEntitiesOfClass(LivingEntity.class, center.getBoundingBox().inflate(radius),
                e -> e != center && e != attacker && e.isAlive() && e instanceof Enemy);
        targets.sort(Comparator.comparingDouble(e -> e.distanceToSqr(center)));
        float damage = (float) (hit.damage() * p.num("ratio", 0.5));
        DamageSource breath = new DamageSource(attacker.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DamageTypes.DRAGON_BREATH), null, attacker);
        splashing = true;
        try {
            int max = (int) p.num("max_targets", 6);
            for (int i = 0; i < Math.min(max, targets.size()); i++) targets.get(i).hurt(breath, damage);
        } finally {
            splashing = false;
        }
        if (center.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.DRAGON_BREATH, center.getX(), center.getY(0.5), center.getZ(), 30, radius / 2, 0.3, radius / 2, 0.02);
        }
        return false;
    }
}
