package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.PassiveCooldowns;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/** cheat_death : un coup mortel laisse "health" PV (avec la recharge du passif). */
public final class CheatDeath implements PassiveEffect {
    @Override
    public String id() {
        return "cheat_death";
    }

    @Override
    public boolean onLethal(Lethal hit, PassiveDefinition p) {
        Player victim = hit.victim();
        if (!PassiveCooldowns.ready(victim, p)) return false;
        hit.event().setNewDamage(Math.max(0f, victim.getHealth() - (float) p.num("health", 2.0)));
        victim.level().playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6f, 1.3f);
        if (victim.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, victim.getX(), victim.getY() + 1.0, victim.getZ(), 30, 0.4, 0.6, 0.4, 0.4);
        }
        return true;
    }
}
