package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

/** fall_bounce : pas de dégâts de chute au-delà de min_fall blocs, on rebondit ("strength"). */
public final class FallBounce implements PassiveEffect {
    @Override
    public String id() {
        return "fall_bounce";
    }

    @Override
    public boolean onFall(Fall fall, PassiveDefinition p) {
        return bounce(fall, p);
    }

    /** Rebondit si la chute dépasse min_fall (6 par défaut). true = rebond fait. */
    static boolean bounce(Fall fall, PassiveDefinition p) {
        LivingFallEvent event = fall.event();
        if (event.getDistance() < p.num("min_fall", 6)) return false;
        Player player = fall.player();
        event.setDamageMultiplier(0);
        double up = Math.min(1.2, Math.sqrt(event.getDistance()) * p.num("strength", 0.18));
        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(v.x, up, v.z);
        player.hurtMarked = true;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 1.0f, 1.0f);
        return true;
    }
}
