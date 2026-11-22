package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.PassiveCooldowns;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;
import net.Kyap.KPItemsRarity.passive.PassiveStats;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * dodge : chance d'esquiver le coup = per_point × points de stat (au plus max). Réglages : stat, per_point, max,
 * no_rain (pas sous la pluie), on_dodge = slow (ralentit l'attaquant "duration" s) ou teleport_behind
 * (passe "distance" blocs derrière lui, soumis à la recharge).
 */
public final class Dodge implements PassiveEffect {
    @Override
    public String id() {
        return "dodge";
    }

    @Override
    public boolean onDefend(Defense hit, PassiveDefinition p) {
        Player victim = hit.victim();
        if (isWaiting(victim, p)) return false;
        double chance = Math.min(p.num("max", 0.5), p.num("per_point", 0.005) * PassiveStats.points(victim, p.str("stat", "agility")));
        if (chance <= 0 || victim.getRandom().nextDouble() >= chance) return false;
        hit.event().setCanceled(true);
        victim.level().playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 1.0f, 1.6f);
        if (victim.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CLOUD, victim.getX(), victim.getY() + 1.0, victim.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
        }
        if (hit.source().getEntity() instanceof LivingEntity attacker) counter(victim, attacker, p);
        return true;
    }

    /** "no_rain" : pas d'esquive mouillé (pluie ou eau). */
    @Override
    public boolean isWaiting(Player wearer, PassiveDefinition p) {
        return p.flag("no_rain") && wearer.isInWaterOrRain();
    }

    private static void counter(Player victim, LivingEntity attacker, PassiveDefinition p) {
        switch (p.str("on_dodge", "")) {
            case "slow" -> attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    (int) (p.num("duration", 2) * 20), 1), victim);
            case "teleport_behind" -> {
                if (!PassiveCooldowns.ready(victim, p)) return;
                Vec3 look = attacker.getLookAngle().multiply(1, 0, 1).normalize();
                Vec3 to = attacker.position().subtract(look.scale(p.num("distance", 3)));
                if (victim.randomTeleport(to.x, attacker.getY(), to.z, true)) {
                    victim.lookAt(EntityAnchorArgument.Anchor.EYES, attacker.getEyePosition());
                }
            }
            default -> {
            }
        }
    }
}
