package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;
import net.Kyap.KPItemsRarity.registry.ModAttributes;
import net.minecraft.world.entity.player.Player;

/**
 * life_steal : soigne de ratio × dégâts infligés, multiplié par les Soins du porteur.
 * Réglages : ratio, below_health (part de la vie max) et low_ratio (ratio sous ce seuil), source (arrow|melee|any),
 * burning_only (seulement sur une cible en feu).
 */
public final class LifeSteal implements PassiveEffect {
    @Override
    public String id() {
        return "life_steal";
    }

    @Override
    public boolean onDealt(Dealt hit, PassiveDefinition p) {
        Player attacker = hit.attacker();
        if (attacker == hit.victim() || !hit.from(p.str("source", "any"))) return false;
        if (p.flag("burning_only") && !hit.victim().isOnFire()) return false;
        double ratio = p.num("ratio", 0);
        if (attacker.getHealth() <= attacker.getMaxHealth() * p.num("below_health", 0)) ratio = p.num("low_ratio", ratio);
        double healing = attacker.getAttributeValue(ModAttributes.HEALING_RECEIVED); // 1 = 100 %
        float amount = (float) (hit.damage() * ratio * healing);
        if (amount > 0) attacker.setHealth(Math.min(attacker.getMaxHealth(), attacker.getHealth() + amount));
        return false;
    }

    /** Sans ratio de base, il n'agit que sous below_health : en attente au-dessus. */
    @Override
    public boolean isWaiting(Player wearer, PassiveDefinition p) {
        double threshold = p.num("below_health", 0);
        return p.num("ratio", 0) <= 0 && threshold > 0 && wearer.getHealth() > wearer.getMaxHealth() * threshold;
    }
}
