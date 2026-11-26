package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;

/**
 * damage_reduction : dégâts reçus × (1 − ratio). Réglages : source (projectile|melee|fire|explosion|hot_floor|fall|any),
 * ratio (1 = aucun dégât : le coup est annulé, une flèche rebondit), chance (1 : à chaque coup), crouch_only
 * (seulement accroupi).
 */
public final class DamageReduction implements PassiveEffect {
    @Override
    public String id() {
        return "damage_reduction";
    }

    @Override
    public boolean onDefend(Defense hit, PassiveDefinition p) {
        if (!matches(hit, p.str("source", "any")) || isWaiting(hit.victim(), p)) return false;
        if (hit.victim().getRandom().nextDouble() >= p.num("chance", 1)) return false;
        double ratio = p.num("ratio", 0);
        if (ratio >= 1) {
            hit.event().setCanceled(true);
            return true;
        }
        hit.multiply(Math.max(0.0, 1 - ratio));
        return false;
    }

    private static boolean matches(Defense hit, String source) {
        return switch (source) {
            case "fire" -> hit.source().is(DamageTypeTags.IS_FIRE);
            case "explosion" -> hit.source().is(DamageTypeTags.IS_EXPLOSION);
            case "hot_floor" -> hit.source().is(DamageTypes.HOT_FLOOR);
            case "fall" -> hit.source().is(DamageTypeTags.IS_FALL);
            default -> hit.from(source);
        };
    }

    @Override
    public boolean isWaiting(Player wearer, PassiveDefinition p) {
        return p.flag("crouch_only") && !wearer.isCrouching();
    }
}
