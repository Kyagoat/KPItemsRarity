package net.Kyap.KPItemsRarity.passive;

import net.Kyap.KPItemsRarity.KPItemsRarity;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;

/**
 * Les passifs (data/<ns>/kp_passives) : des effets à déclencheur portés par un objet ou débloqués par une
 * panoplie, dont les valeurs dépendent des stats du porteur. Cette classe ne fait que relier les événements
 * du jeu aux effets ({@link PassiveEffects}, un fichier par effet dans passive/effect).
 *
 * Tout se calcule au moment d'un coup donné ou reçu, d'un soin ou d'une chute : rien à chaque tick.
 * Seuls les joueurs ont des passifs.
 */
@EventBusSubscriber(modid = KPItemsRarity.MOD_ID)
public final class Passives {
    private Passives() {}

    /** Un soin donné par un passif (partage) ne relance pas les passifs. */
    private static boolean passiveHealing;

    /** Retient les critiques de mêlée pour les passifs qui en dépendent (le coup est joué des deux côtés : serveur seul). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCrit(CriticalHitEvent event) {
        if (event.isCriticalHit() && !event.getEntity().level().isClientSide()) CritTracker.record(event.getEntity());
    }

    /** Le joueur frappe (avant armure). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onOutgoing(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof Player attacker) || attacker.level().isClientSide()) return;
        applyStrengthToArrows(event, attacker);
        ActivePassives passives = ActivePassives.of(attacker);
        if (passives.isEmpty()) return;
        boolean projectile = source.getDirectEntity() instanceof Projectile;
        boolean melee = source.getDirectEntity() == attacker;
        PassiveEffect.Attack hit = new PassiveEffect.Attack(attacker, event.getEntity(), event, melee, projectile);
        passives.forEachInOrder((effect, p) -> effect.onAttack(hit, p));
    }

    /** Hors passifs : la Force compte aussi pour les flèches et le trident lancé (en mêlée, elle passe par l'attribut). */
    private static void applyStrengthToArrows(LivingIncomingDamageEvent event, Player attacker) {
        if (!(event.getSource().getDirectEntity() instanceof AbstractArrow)) return;
        double strength = PassiveStats.fraction(attacker, "strength");
        if (strength != 0) event.setAmount((float) (event.getAmount() * Math.max(0.0, 1 + strength)));
    }

    /** Le joueur est frappé (avant armure) : esquive, réduction, repousse. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncoming(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim) || victim.level().isClientSide()) return;
        DamageSource source = event.getSource();
        if (source.getEntity() == null || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        boolean projectile = source.getDirectEntity() instanceof Projectile;
        boolean melee = source.getDirectEntity() == source.getEntity() && !source.is(DamageTypeTags.IS_EXPLOSION);
        PassiveEffect.Defense hit = new PassiveEffect.Defense(victim, source, event, melee, projectile);
        ActivePassives.of(victim).anyByEffect((effect, p) -> effect.onDefend(hit, p));
    }

    /** Le coup tuerait le joueur. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLethal(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player victim) || victim.level().isClientSide()) return;
        if (event.getNewDamage() < victim.getHealth()) return;
        PassiveEffect.Lethal hit = new PassiveEffect.Lethal(victim, event);
        ActivePassives.of(victim).anyByEffect((effect, p) -> effect.onLethal(hit, p));
    }

    /** Dégâts appliqués : côté attaquant (vol de vie, ricochet) puis côté victime (renvoi). */
    @SubscribeEvent
    public static void onDealt(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || event.getNewDamage() <= 0) return;
        DamageSource source = event.getSource();
        if (source.getEntity() instanceof Player attacker) {
            PassiveEffect.Dealt hit = new PassiveEffect.Dealt(attacker, victim, source, event.getNewDamage());
            ActivePassives.of(attacker).anyByEffect((effect, p) -> effect.onDealt(hit, p));
        }
        if (victim instanceof Player player) {
            PassiveEffect.Taken hit = new PassiveEffect.Taken(player, source, event.getNewDamage());
            ActivePassives.of(player).forEachByEffect((effect, p) -> effect.onTaken(hit, p));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHeal(LivingHealEvent event) {
        if (passiveHealing || !(event.getEntity() instanceof Player player) || player.level().isClientSide()) return;
        ActivePassives passives = ActivePassives.of(player);
        if (passives.isEmpty()) return;
        PassiveEffect.Heal heal = new PassiveEffect.Heal(player, event);
        passiveHealing = true;
        try {
            passives.forEachByEffect((effect, p) -> effect.onHeal(heal, p));
        } finally {
            passiveHealing = false;
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide() || player.isShiftKeyDown()) return;
        PassiveEffect.Fall fall = new PassiveEffect.Fall(player, event);
        ActivePassives.of(player).anyByEffect((effect, p) -> effect.onFall(fall, p));
    }
}
