package net.Kyap.KPItemsRarity.passive;

import net.minecraft.world.entity.LivingEntity;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Coups critiques du tick en cours, côté serveur : mêlée (CriticalHitEvent) et tirs (CombatEvents). Les passifs qui
 * dépendent d'un critique le lisent ici (armor_ignore, crit_explosion, crit_effect).
 */
public final class CritTracker {
    private CritTracker() {}

    private static final Map<LivingEntity, Long> LAST_CRIT = new WeakHashMap<>();
    /** Joueurs dont le prochain coup de mêlée sera un critique (Pas du Vide). */
    private static final Set<LivingEntity> FORCED = Collections.newSetFromMap(new WeakHashMap<>());

    public static void record(LivingEntity attacker) {
        LAST_CRIT.put(attacker, attacker.level().getGameTime());
    }

    /** Le coup en cours de l'attaquant est-il un critique ? */
    public static boolean critNow(LivingEntity attacker) {
        Long t = LAST_CRIT.get(attacker);
        return t != null && t == attacker.level().getGameTime();
    }

    /** Le prochain coup de mêlée de cet attaquant sera un critique. */
    public static void forceNext(LivingEntity attacker) {
        FORCED.add(attacker);
    }

    /** Vrai (une seule fois) si le coup en cours doit être un critique forcé. */
    public static boolean consumeForced(LivingEntity attacker) {
        return FORCED.remove(attacker);
    }
}
