package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;
import net.minecraft.world.entity.player.Player;

/** missing_health_bonus : + ratio % de dégâts par % de vie manquante (Rage : ratio 1). Réglage : source (any). */
public final class MissingHealthBonus implements PassiveEffect {
    @Override
    public String id() {
        return "missing_health_bonus";
    }

    @Override
    public void onAttack(Attack hit, PassiveDefinition p) {
        if (!hit.from(p.str("source", "any"))) return;
        Player attacker = hit.attacker();
        double missing = 1 - attacker.getHealth() / attacker.getMaxHealth();
        if (missing > 0) hit.multiply(1 + missing * p.num("ratio", 1));
    }

    /** En pleine santé, il n'ajoute rien. */
    @Override
    public boolean isWaiting(Player wearer, PassiveDefinition p) {
        return wearer.getHealth() >= wearer.getMaxHealth();
    }
}
