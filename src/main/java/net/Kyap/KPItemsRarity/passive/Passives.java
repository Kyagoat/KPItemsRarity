package net.Kyap.KPItemsRarity.passive;

import net.Kyap.KPItemsRarity.KPItemsRarity;
import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.effect.SplashDamage;
import net.Kyap.KPItemsRarity.passive.effect.Taunt;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.PotionItem;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;

import java.util.function.BiConsumer;

/**
 * Les passifs (data/<ns>/kp_passives) : des effets à déclencheur portés par un objet ou débloqués par une
 * panoplie, dont les valeurs dépendent des stats du porteur. Cette classe ne fait que relier les événements
 * du jeu aux effets ({@link PassiveEffects}, un fichier par effet dans passive/effect).
 *
 * Tout se calcule au moment d'un coup donné ou reçu, d'un soin, d'une chute, d'un kill, d'un effet de potion donné,
 * d'une flèche tirée ou d'un saut : rien à chaque tick (sauf le suivi du sprint, voir SprintTracker).
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

    /** Le joueur lance un coup de mêlée (avant le critique : Mèche peut le rendre critique). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onSwing(AttackEntityEvent event) {
        Player attacker = event.getEntity();
        if (attacker.level().isClientSide()) return;
        PassiveEffect.Swing swing = new PassiveEffect.Swing(attacker, event.getTarget());
        ActivePassives.of(attacker).forEachByEffect((effect, p) -> effect.onSwing(swing, p));
    }

    /** Le joueur frappe (avant armure). Le souffle d'un passif ne relance pas les passifs. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onOutgoing(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof Player attacker) || attacker.level().isClientSide() || SplashDamage.splashing()) return;
        applyStrengthToArrows(event, attacker);
        boolean projectile = source.getDirectEntity() instanceof Projectile;
        boolean melee = source.getDirectEntity() == attacker;
        PassiveEffect.Attack hit = new PassiveEffect.Attack(attacker, event.getEntity(), event, melee, projectile);
        ActivePassives.of(attacker).forEachInOrder((effect, p) -> effect.onAttack(hit, p));
        if (!event.isCanceled()) allyAuras(hit);
    }

    /** Les auras des autres joueurs proches (Bannière) : chacune a sa portée ("radius", en blocs, au plus AURA_MAX). */
    private static void allyAuras(PassiveEffect.Attack hit) {
        Player attacker = hit.attacker();
        for (Player ally : attacker.level().players()) {
            if (ally == attacker || !ally.isAlive()) continue;
            double distanceSqr = ally.distanceToSqr(attacker);
            if (distanceSqr > AURA_MAX * AURA_MAX) continue;
            ActivePassives.of(ally).forEachByEffect((effect, p) -> {
                double radius = Math.min(AURA_MAX, p.num("radius", 8));
                if (distanceSqr <= radius * radius) effect.onAllyAttack(hit, p);
            });
        }
    }

    /** Portée maximale d'une aura : au-delà, on ne regarde même pas les passifs du joueur. */
    private static final double AURA_MAX = 16;

    /** Hors passifs : la Force compte aussi pour les flèches et le trident lancé (en mêlée, elle passe par l'attribut). */
    private static void applyStrengthToArrows(LivingIncomingDamageEvent event, Player attacker) {
        if (!(event.getSource().getDirectEntity() instanceof AbstractArrow)) return;
        double strength = PassiveStats.fraction(attacker, "strength");
        if (strength != 0) event.setAmount((float) (event.getAmount() * Math.max(0.0, 1 + strength)));
    }

    /**
     * Le joueur est frappé (avant armure) : esquive, réduction, repousse. Les dégâts sans attaquant (feu, lave, chute...)
     * passent aussi : seules les réductions s'en occupent.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncoming(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim) || victim.level().isClientSide()) return;
        DamageSource source = event.getSource();
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        boolean projectile = source.getDirectEntity() instanceof Projectile;
        boolean melee = source.getEntity() != null && source.getDirectEntity() == source.getEntity() && !source.is(DamageTypeTags.IS_EXPLOSION);
        PassiveEffect.Defense hit = new PassiveEffect.Defense(victim, source, event, melee, projectile);
        ActivePassives.of(victim).anyByEffect((effect, p) -> effect.onDefend(hit, p));
        if (!event.isCanceled() && source.getEntity() instanceof Mob attacker) taunt(victim, attacker);
    }

    /**
     * Provocation : un monstre qui frappe un joueur se retourne contre l'allié provocateur le plus proche (à sa portée).
     * Seulement les joueurs proches sont regardés, et seulement quand un coup est porté.
     */
    private static void taunt(Player victim, Mob attacker) {
        Player best = null;
        double bestSqr = Double.MAX_VALUE;
        for (Player ally : victim.level().players()) {
            if (ally == victim || !ally.isAlive() || ally.isSpectator()) continue;
            double distanceSqr = ally.distanceToSqr(victim);
            if (distanceSqr > AURA_MAX * AURA_MAX || distanceSqr >= bestSqr) continue;
            for (PassiveDefinition p : ActivePassives.list(ally)) {
                double radius = p.num("radius", 8);
                if (Taunt.ID.equals(p.effect) && distanceSqr <= radius * radius) {
                    best = ally;
                    bestSqr = distanceSqr;
                    break;
                }
            }
        }
        if (best != null) Allies.setTarget(attacker, best);
    }

    /** Le joueur tue une créature. */
    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof Player killer) || killer.level().isClientSide() || killer == event.getEntity()) return;
        PassiveEffect.Kill kill = new PassiveEffect.Kill(killer, event.getEntity(), event.getSource());
        ActivePassives.of(killer).forEachByEffect((effect, p) -> effect.onKill(kill, p));
    }

    /** Le joueur va perdre des PV (armure comptée) : réduction des gros coups. Avant le test du coup mortel. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player victim) || victim.level().isClientSide() || event.getNewDamage() <= 0) return;
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        PassiveEffect.Hurt hit = new PassiveEffect.Hurt(victim, event);
        ActivePassives.of(victim).forEachByEffect((effect, p) -> effect.onHurt(hit, p));
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
        if (source.getEntity() instanceof Player attacker && !SplashDamage.splashing()) {
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

    // ---------------------------------------------------------------- Hors combat : potions, tirs, sauts

    /**
     * Un effet arrive sur une créature : celui qui le donne est le lanceur de la potion ou de la flèche, ou, pour
     * une potion bue, celui qui la boit (le jeu n'en dit rien : on regarde ce qu'il est en train de boire).
     */
    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        Player giver = event.getEffectSource() instanceof Player thrower ? thrower
                : event.getEffectSource() == null && target instanceof Player self && self.getUseItem().getItem() instanceof PotionItem ? self
                : null;
        if (giver == null) return;
        PassiveEffect.EffectGiven given = new PassiveEffect.EffectGiven(giver, target, event.getEffectInstance());
        ActivePassives.of(giver).forEachByEffect((effect, p) -> effect.onEffectGiven(given, p));
    }

    /** Une flèche tirée par un joueur (pas une flèche rechargée avec le monde, ni une flèche ajoutée par un passif). */
    @SubscribeEvent
    public static void onArrowShot(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || event.getLevel().isClientSide() || PassiveArrows.spawning()) return;
        if (!(event.getEntity() instanceof AbstractArrow arrow) || !(arrow.getOwner() instanceof Player shooter)) return;
        PassiveEffect.Shot shot = new PassiveEffect.Shot(shooter, arrow);
        ActivePassives.of(shooter).forEachByEffect((effect, p) -> effect.onShoot(shot, p));
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) return;
        PassiveEffect.Jump jump = new PassiveEffect.Jump(player);
        ActivePassives.of(player).forEachByEffect((effect, p) -> effect.onJump(jump, p));
    }


    /** Appelé par SetBonusEvents.refresh : les points de stat que les passifs portés ajoutent. */
    public static void onRefresh(Player player, BiConsumer<String, Double> addStat) {
        PassiveEffect.Refresh refresh = new PassiveEffect.Refresh(player, addStat);
        ActivePassives.of(player).forEachByEffect((effect, p) -> effect.onRefresh(refresh, p));
    }
}
