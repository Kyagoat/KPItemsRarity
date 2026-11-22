package net.Kyap.KPItemsRarity.passive;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Un type d'effet de passif (le champ "effect" d'un fichier kp_passives). Un effet ne réagit qu'aux moments
 * qui le concernent ; les autres méthodes ne font rien. Ses réglages viennent du passif (p.num, p.str, p.flag).
 *
 * Une méthode qui renvoie true arrête le moment en cours pour les passifs suivants (esquive réussie, mort
 * évitée, rebond fait...). L'ordre dans lequel les effets passent est celui de {@link PassiveEffects}.
 */
public interface PassiveEffect {
    /** Identifiant dans les données ("effect": "life_steal"). */
    String id();

    /** Le porteur frappe : dégâts de la cible avant armure. */
    default void onAttack(Attack hit, PassiveDefinition p) {}

    /** Le porteur est frappé : dégâts avant armure. true = arrêter (coup esquivé...). */
    default boolean onDefend(Defense hit, PassiveDefinition p) {
        return false;
    }

    /** Le coup tuerait le porteur. true = la mort est évitée. */
    default boolean onLethal(Lethal hit, PassiveDefinition p) {
        return false;
    }

    /** Dégâts infligés par le porteur, armure comprise. true = arrêter. */
    default boolean onDealt(Dealt hit, PassiveDefinition p) {
        return false;
    }

    /** Dégâts subis par le porteur, armure comprise. */
    default void onTaken(Taken hit, PassiveDefinition p) {}

    /** Le porteur est soigné. */
    default void onHeal(Heal heal, PassiveDefinition p) {}

    /** Le porteur tombe. true = la chute est prise en charge (rebond). */
    default boolean onFall(Fall fall, PassiveDefinition p) {
        return false;
    }

    /**
     * Pour l'infobulle : le passif est porté, mais sa condition n'est pas remplie en ce moment (vie trop haute,
     * hors de l'eau, sous la pluie...). Il agira dès qu'elle le sera.
     */
    default boolean isWaiting(Player wearer, PassiveDefinition p) {
        return false;
    }

    // ---------------------------------------------------------------- Ce que reçoit chaque moment

    /** "arrow" ou "projectile" : un tir ; "melee" : un coup direct ; autre chose ("any") : tout. */
    static boolean fromSource(String source, boolean melee, boolean projectile) {
        return switch (source) {
            case "arrow", "projectile" -> projectile;
            case "melee" -> melee;
            default -> true;
        };
    }

    record Attack(Player attacker, LivingEntity target, LivingIncomingDamageEvent event, boolean melee, boolean projectile) {
        public boolean from(String source) {
            return fromSource(source, melee, projectile);
        }

        public void multiply(double factor) {
            event.setAmount((float) (event.getAmount() * factor));
        }

        public void add(double amount) {
            event.setAmount((float) (event.getAmount() + amount));
        }
    }

    record Defense(Player victim, DamageSource source, LivingIncomingDamageEvent event, boolean melee, boolean projectile) {
        public boolean from(String source) {
            return fromSource(source, melee, projectile);
        }

        public void multiply(double factor) {
            event.setAmount((float) (event.getAmount() * factor));
        }
    }

    record Lethal(Player victim, LivingDamageEvent.Pre event) {}

    record Dealt(Player attacker, LivingEntity victim, DamageSource source, float damage) {}

    record Taken(Player victim, DamageSource source, float damage) {}

    record Heal(Player player, LivingHealEvent event) {}

    record Fall(Player player, LivingFallEvent event) {}
}
