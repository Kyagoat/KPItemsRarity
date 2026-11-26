package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.CritTracker;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;
import net.Kyap.KPItemsRarity.passive.PassiveStats;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.damagesource.DamageContainer;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * armor_ignore : le coup ignore une partie de l'armure de la cible.
 * Réglages : flat (points), stat + per (1 point par "per" points de stat), crit_only (seulement sur un
 * coup critique, de mêlée ou tiré), every_nth (le n-ième coup d'affilée sur la même cible ignore toute l'armure),
 * falling (un coup de mêlée porté en chute, comme l'écrasement de la masse, ignore toute l'armure), weapon (seulement
 * avec cette arme en main, "minecraft:mace").
 */
public final class ArmorIgnore implements PassiveEffect {
    /** Coups d'affilée d'un attaquant sur la même cible. */
    private static final class Streak {
        int targetId = -1;
        int hits;
    }

    private final Map<LivingEntity, Streak> streaks = new WeakHashMap<>();

    /** Assez pour ignorer toute l'armure. */
    private static final double ALL_ARMOR = 1024;
    /** Hauteur de chute à partir de laquelle un coup de masse vanilla écrase (MaceItem). */
    private static final double FALL_SMASH = 1.5;

    @Override
    public String id() {
        return "armor_ignore";
    }

    @Override
    public void onAttack(Attack hit, PassiveDefinition p) {
        if (!hit.melee() && !hit.projectile()) return;
        if (!holdsWeapon(hit.attacker(), p.str("weapon", ""))) return;
        boolean crit = CritTracker.critNow(hit.attacker());
        double ignore = 0;
        if (!p.flag("crit_only") || crit) {
            ignore += p.num("flat", 0);
            double per = p.num("per", 0);
            if (per > 0) ignore += Math.floor(PassiveStats.points(hit.attacker(), p.str("stat", "strength")) / per);
        }
        int nth = (int) p.num("every_nth", 0);
        if (nth > 0 && nthHit(hit.attacker(), hit.target(), nth)) ignore = ALL_ARMOR;
        if (p.flag("falling") && hit.melee() && hit.attacker().fallDistance > FALL_SMASH) ignore = ALL_ARMOR;
        if (ignore > 0) ignoreArmor(hit, ignore);
    }

    /** Pas d'arme exigée, ou l'arme en main est celle-là. */
    private static boolean holdsWeapon(LivingEntity attacker, String weapon) {
        if (weapon.isEmpty()) return true;
        return BuiltInRegistries.ITEM.getKey(attacker.getMainHandItem().getItem()).toString().equals(weapon);
    }

    /** Compte les coups d'affilée sur la cible ; true au n-ième (et le compte repart). */
    private boolean nthHit(LivingEntity attacker, LivingEntity target, int nth) {
        Streak streak = streaks.computeIfAbsent(attacker, k -> new Streak());
        if (streak.targetId != target.getId()) {
            streak.targetId = target.getId();
            streak.hits = 0;
        }
        if (++streak.hits < nth) return false;
        streak.hits = 0;
        return true;
    }

    private static void ignoreArmor(Attack hit, double ignore) {
        LivingEntity target = hit.target();
        hit.event().addReductionModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> {
            double armor = target.getArmorValue();
            if (armor <= 0) return reduction;
            return (float) (reduction * Math.max(0.0, (armor - ignore) / armor));
        });
    }
}
