package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;
import net.minecraft.world.entity.player.Player;

/** context_damage : + bonus de dégâts dans un contexte (voir Contexts). Réglages : contexts, bonus, source (any). */
public final class ContextDamage implements PassiveEffect {
    @Override
    public String id() {
        return "context_damage";
    }

    @Override
    public void onAttack(Attack hit, PassiveDefinition p) {
        if (hit.from(p.str("source", "any")) && Contexts.any(hit.attacker(), Contexts.of(p))) hit.multiply(1 + p.num("bonus", 0));
    }

    @Override
    public boolean isWaiting(Player wearer, PassiveDefinition p) {
        return !Contexts.any(wearer, Contexts.of(p));
    }
}
