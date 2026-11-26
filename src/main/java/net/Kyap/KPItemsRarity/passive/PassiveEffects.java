package net.Kyap.KPItemsRarity.passive;

import net.Kyap.KPItemsRarity.passive.effect.AllyStrength;
import net.Kyap.KPItemsRarity.passive.effect.ArmorIgnore;
import net.Kyap.KPItemsRarity.passive.effect.BigHitReduction;
import net.Kyap.KPItemsRarity.passive.effect.BonusDamage;
import net.Kyap.KPItemsRarity.passive.effect.CallAllies;
import net.Kyap.KPItemsRarity.passive.effect.ChargedStrike;
import net.Kyap.KPItemsRarity.passive.effect.CheatDeath;
import net.Kyap.KPItemsRarity.passive.effect.ContextDamage;
import net.Kyap.KPItemsRarity.passive.effect.ContextProspection;
import net.Kyap.KPItemsRarity.passive.effect.CritEffect;
import net.Kyap.KPItemsRarity.passive.effect.CritExplosion;
import net.Kyap.KPItemsRarity.passive.effect.CrouchGuard;
import net.Kyap.KPItemsRarity.passive.effect.DamageReduction;
import net.Kyap.KPItemsRarity.passive.effect.DistanceBonus;
import net.Kyap.KPItemsRarity.passive.effect.Dodge;
import net.Kyap.KPItemsRarity.passive.effect.FallBounce;
import net.Kyap.KPItemsRarity.passive.effect.FallingStrike;
import net.Kyap.KPItemsRarity.passive.effect.FamilyDamage;
import net.Kyap.KPItemsRarity.passive.effect.FamilyResistance;
import net.Kyap.KPItemsRarity.passive.effect.FlatDamage;
import net.Kyap.KPItemsRarity.passive.effect.HealingInWater;
import net.Kyap.KPItemsRarity.passive.effect.HitEffect;
import net.Kyap.KPItemsRarity.passive.effect.Ignite;
import net.Kyap.KPItemsRarity.passive.effect.JumpLaunch;
import net.Kyap.KPItemsRarity.passive.effect.KillEffect;
import net.Kyap.KPItemsRarity.passive.effect.LifeSteal;
import net.Kyap.KPItemsRarity.passive.effect.LowHealthEffect;
import net.Kyap.KPItemsRarity.passive.effect.MissingHealthBonus;
import net.Kyap.KPItemsRarity.passive.effect.PotionDuration;
import net.Kyap.KPItemsRarity.passive.effect.Reflect;
import net.Kyap.KPItemsRarity.passive.effect.Repel;
import net.Kyap.KPItemsRarity.passive.effect.Ricochet;
import net.Kyap.KPItemsRarity.passive.effect.ShareHealing;
import net.Kyap.KPItemsRarity.passive.effect.SplashDamage;
import net.Kyap.KPItemsRarity.passive.effect.SprintCharge;
import net.Kyap.KPItemsRarity.passive.effect.StatConversion;
import net.Kyap.KPItemsRarity.passive.effect.StrengthInWater;
import net.Kyap.KPItemsRarity.passive.effect.TargetStateBonus;
import net.Kyap.KPItemsRarity.passive.effect.Taunt;
import net.Kyap.KPItemsRarity.passive.effect.Volley;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tous les effets de passifs, dans l'ordre où ils passent quand plusieurs réagissent au même moment :
 * renvoi des projectiles avant esquive avant réduction avant repousse, effet de coup avant vol de vie avant ricochet
 * et souffle, soins dans l'eau avant partage, rebond de chute avant celui de la repousse. Au moment d'un coup donné,
 * c'est l'ordre des passifs portés.
 * Chaque classe d'effet décrit ses réglages ("params" du fichier kp_passives).
 */
public final class PassiveEffects {
    private PassiveEffects() {}

    private static final Map<String, PassiveEffect> EFFECTS = new LinkedHashMap<>();

    static {
        for (PassiveEffect effect : List.of(
                // coup lancé, coup donné
                new ChargedStrike(), new BonusDamage(), new FlatDamage(), new FamilyDamage(), new ContextDamage(), new TargetStateBonus(),
                new DistanceBonus(), new StrengthInWater(), new FallingStrike(), new SprintCharge(), new MissingHealthBonus(),
                new ArmorIgnore(), new CritEffect(), new CritExplosion(), new CrouchGuard(), new AllyStrength(), new Ignite(),
                // coup reçu (renvoi des projectiles avant l'esquive), chute
                new CallAllies(), new Dodge(), new DamageReduction(), new FamilyResistance(), new FallBounce(), new Repel(),
                // PV perdus, coup mortel
                new BigHitReduction(), new CheatDeath(), new LowHealthEffect(),
                // dégâts infligés / subis
                new HitEffect(), new LifeSteal(), new Ricochet(), new SplashDamage(), new Reflect(),
                // soins
                new HealingInWater(), new ShareHealing(),
                // kills, hors combat
                new KillEffect(), new PotionDuration(), new Volley(), new JumpLaunch(),
                // lus ailleurs (Prospection, Provocation), points de stat tant que le passif est porté
                new ContextProspection(), new Taunt(), new StatConversion()))
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
