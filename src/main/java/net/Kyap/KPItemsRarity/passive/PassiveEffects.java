package net.Kyap.KPItemsRarity.passive;

import net.Kyap.KPItemsRarity.passive.effect.ArmorIgnore;
import net.Kyap.KPItemsRarity.passive.effect.BonusDamage;
import net.Kyap.KPItemsRarity.passive.effect.CheatDeath;
import net.Kyap.KPItemsRarity.passive.effect.CritExplosion;
import net.Kyap.KPItemsRarity.passive.effect.DamageReduction;
import net.Kyap.KPItemsRarity.passive.effect.DistanceBonus;
import net.Kyap.KPItemsRarity.passive.effect.Dodge;
import net.Kyap.KPItemsRarity.passive.effect.FallBounce;
import net.Kyap.KPItemsRarity.passive.effect.FlatDamage;
import net.Kyap.KPItemsRarity.passive.effect.HealingInWater;
import net.Kyap.KPItemsRarity.passive.effect.LifeSteal;
import net.Kyap.KPItemsRarity.passive.effect.Reflect;
import net.Kyap.KPItemsRarity.passive.effect.Repel;
import net.Kyap.KPItemsRarity.passive.effect.Ricochet;
import net.Kyap.KPItemsRarity.passive.effect.ShareHealing;
import net.Kyap.KPItemsRarity.passive.effect.StrengthInWater;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tous les effets de passifs, dans l'ordre où ils passent quand plusieurs réagissent au même moment :
 * esquive avant réduction avant repousse, vol de vie avant ricochet, soins dans l'eau avant partage,
 * rebond de chute avant celui de la repousse. Au moment d'un coup donné, c'est l'ordre des passifs portés.
 * Chaque classe d'effet décrit ses réglages ("params" du fichier kp_passives).
 */
public final class PassiveEffects {
    private PassiveEffects() {}

    private static final Map<String, PassiveEffect> EFFECTS = new LinkedHashMap<>();

    static {
        for (PassiveEffect effect : List.of(
                // coup donné
                new BonusDamage(), new FlatDamage(), new DistanceBonus(), new StrengthInWater(),
                new ArmorIgnore(), new CritExplosion(),
                // coup reçu, chute
                new Dodge(), new DamageReduction(), new FallBounce(), new Repel(),
                new CheatDeath(),
                // dégâts infligés / subis
                new LifeSteal(), new Ricochet(), new Reflect(),
                // soins
                new HealingInWater(), new ShareHealing()))
            EFFECTS.put(effect.id(), effect);
    }

    /** L'effet d'un identifiant de données, ou null s'il n'existe pas. */
    public static PassiveEffect get(String id) {
        return EFFECTS.get(id);
    }

    /** Les effets dans leur ordre de passage. */
    static Iterable<PassiveEffect> inOrder() {
        return EFFECTS.values();
    }
}
