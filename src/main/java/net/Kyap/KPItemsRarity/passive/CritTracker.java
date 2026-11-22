package net.Kyap.KPItemsRarity.passive;

import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.WeakHashMap;

/** Coups critiques de mêlée du tick en cours, côté serveur (armor_ignore et crit_explosion en dépendent). */
public final class CritTracker {
    private CritTracker() {}

    private static final Map<LivingEntity, Long> LAST_CRIT = new WeakHashMap<>();

    static void record(LivingEntity attacker) {
        LAST_CRIT.put(attacker, attacker.level().getGameTime());
    }

    /** Le coup en cours de l'attaquant est-il un critique ? */
    public static boolean critNow(LivingEntity attacker) {
        Long t = LAST_CRIT.get(attacker);
        return t != null && t == attacker.level().getGameTime();
    }
}
