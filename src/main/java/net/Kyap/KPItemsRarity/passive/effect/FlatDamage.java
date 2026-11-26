package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;
import net.Kyap.KPItemsRarity.passive.PassiveStats;
import net.minecraft.world.entity.player.Player;

/**
 * flat_damage : + amount dégâts par tranche de "per" d'une valeur du porteur (1 dégât par 10 PV max...).
 * Réglages : source (melee|projectile|any), stat (max_health|armor|<stat>), per, amount, sprinting_only (en sprint).
 */
public final class FlatDamage implements PassiveEffect {
    @Override
    public String id() {
        return "flat_damage";
    }

    @Override
    public void onAttack(Attack hit, PassiveDefinition p) {
        if (!hit.from(p.str("source", "melee")) || p.flag("sprinting_only") && !hit.attacker().isSprinting()) return;
        double per = Math.max(1e-6, p.num("per", 10));
        double add = Math.floor(value(hit.attacker(), p.str("stat", "max_health")) / per) * p.num("amount", 1);
        if (add > 0) hit.add(add);
    }

    private static double value(Player attacker, String stat) {
        return switch (stat) {
            case "max_health" -> attacker.getMaxHealth();
            case "armor" -> attacker.getArmorValue();
            default -> PassiveStats.points(attacker, stat);
        };
    }
}
