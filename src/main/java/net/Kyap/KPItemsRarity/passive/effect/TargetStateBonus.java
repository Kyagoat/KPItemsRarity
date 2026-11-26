package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.Allies;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * target_state_bonus : + bonus de dégâts contre une cible dans un état. Réglages : bonus, source (any), state :
 * burning (en feu), poisoned, withered, slowed, full_health (pleine vie), below:0.3 (sous 30 % de sa vie),
 * not_targeting (un monstre qui ne te cible pas : il ne t'a pas vu venir).
 */
public final class TargetStateBonus implements PassiveEffect {
    @Override
    public String id() {
        return "target_state_bonus";
    }

    @Override
    public void onAttack(Attack hit, PassiveDefinition p) {
        if (hit.from(p.str("source", "any")) && matches(hit.attacker(), hit.target(), p.str("state", ""))) {
            hit.multiply(1 + p.num("bonus", 0));
        }
    }

    private static boolean matches(Player attacker, LivingEntity target, String state) {
        if (state.startsWith("below:")) {
            try {
                return target.getHealth() <= target.getMaxHealth() * Double.parseDouble(state.substring(6));
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return switch (state) {
            case "burning" -> target.isOnFire();
            case "poisoned" -> target.hasEffect(MobEffects.POISON);
            case "withered" -> target.hasEffect(MobEffects.WITHER);
            case "slowed" -> target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN);
            case "full_health" -> target.getHealth() >= target.getMaxHealth();
            case "not_targeting" -> target instanceof Mob mob && !Allies.targets(mob, attacker);
            default -> false;
        };
    }
}
