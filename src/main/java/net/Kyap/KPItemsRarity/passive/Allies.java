package net.Kyap.KPItemsRarity.passive;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

/**
 * Donner (ou retirer) une cible à un monstre, qu'il ait un « cerveau » (Piglins, Warden...) ou des objectifs classiques.
 * Un Piglin ne garde une cible que s'il est en colère contre elle : on lui donne aussi cette colère.
 */
public final class Allies {
    private Allies() {}

    /** Combien de temps un monstre à cerveau garde la cible qu'on lui donne (ticks). */
    private static final long MEMORY_TICKS = 200;

    public static void setTarget(Mob mob, LivingEntity target) {
        if (mob == target || !target.isAlive()) return;
        Brain<?> brain = mob.getBrain();
        if (brain.checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)) {
            if (brain.checkMemory(MemoryModuleType.ANGRY_AT, MemoryStatus.REGISTERED)) {
                brain.setMemoryWithExpiry(MemoryModuleType.ANGRY_AT, target.getUUID(), MEMORY_TICKS * 3);
            }
            brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            brain.setMemoryWithExpiry(MemoryModuleType.ATTACK_TARGET, target, MEMORY_TICKS);
        } else {
            mob.setTarget(target);
        }
    }

    /** Le monstre cible-t-il cette créature (objectif classique ou cerveau) ? */
    public static boolean targets(Mob mob, LivingEntity who) {
        if (mob.getTarget() == who) return true;
        Brain<?> brain = mob.getBrain();
        return brain.checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)
                && brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == who;
    }

    /** Le monstre perd sa cible (et sa colère). */
    public static void forget(Mob mob) {
        mob.setTarget(null);
        Brain<?> brain = mob.getBrain();
        if (brain.checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)) brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
        if (brain.checkMemory(MemoryModuleType.ANGRY_AT, MemoryStatus.REGISTERED)) brain.eraseMemory(MemoryModuleType.ANGRY_AT);
    }
}
