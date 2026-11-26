package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.CritTracker;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * hit_effect : un coup ou un tir qui blesse donne un effet à la cible (Toxine : poison ; Faim du Wither : wither).
 * Réglages : source (arrow|melee|any), effect, seconds, amplifier (voir Effects), chance (1), crit_only.
 * Seuls les coups et les tirs comptent (pas les épines, le feu ni les autres passifs).
 */
public final class HitEffect implements PassiveEffect {
    @Override
    public String id() {
        return "hit_effect";
    }

    @Override
    public boolean onDealt(Dealt hit, PassiveDefinition p) {
        Player attacker = hit.attacker();
        boolean direct = hit.source().getDirectEntity() == attacker || hit.source().getDirectEntity() instanceof Projectile;
        if (!direct || attacker == hit.victim() || !hit.victim().isAlive() || !hit.from(p.str("source", "any"))) return false;
        if (p.flag("crit_only") && !CritTracker.critNow(attacker)) return false;
        if (attacker.getRandom().nextDouble() >= p.num("chance", 1)) return false;
        MobEffectInstance effect = Effects.of(p);
        if (effect != null) hit.victim().addEffect(effect, attacker);
        return false;
    }
}
