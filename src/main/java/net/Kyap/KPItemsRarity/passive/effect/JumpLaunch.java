package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.PassiveCooldowns;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * jump_launch : un saut accroupi projette le porteur à "height" blocs de haut (Rafale). Réglages : height (6) ;
 * la recharge du passif évite de voler.
 */
public final class JumpLaunch implements PassiveEffect {
    @Override
    public String id() {
        return "jump_launch";
    }

    @Override
    public void onJump(Jump jump, PassiveDefinition p) {
        Player player = jump.player();
        if (!player.isCrouching() || !PassiveCooldowns.ready(player, p)) return;
        player.setDeltaMovement(player.getDeltaMovement().x, velocityFor(p.num("height", 6)), player.getDeltaMovement().z);
        player.hurtMarked = true; // envoie le nouvel élan au client
        player.fallDistance = 0;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 0.8f, 1.2f);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.GUST, player.getX(), player.getY(), player.getZ(), 1, 0, 0, 0, 0);
        }
    }

    /** Élan vertical pour monter de "height" blocs (gravité 0,08 par tick, frottement 2 %), cherché pas à pas. */
    static double velocityFor(double height) {
        double v = 0.42;
        while (v < 4 && peak(v) < height) v += 0.01;
        return v;
    }

    private static double peak(double v) {
        double y = 0;
        while (v > 0) {
            y += v;
            v = (v - 0.08) * 0.98;
        }
        return y;
    }

    @Override
    public boolean isWaiting(Player wearer, PassiveDefinition p) {
        return !wearer.isCrouching();
    }
}
