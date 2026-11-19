package net.Kyap.KPItemsRarity.events;

import net.Kyap.KPItemsRarity.Config;
import net.Kyap.KPItemsRarity.KPItemsRarity;
import net.Kyap.KPItemsRarity.registry.ModAttributes;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Effets des attributs de combat du mod (registry/ModAttributes) :
 *  coup critique, dégâts critiques, vol de vie, esquive, perce-armure, dégâts des flèches,
 *  vitesse d'armement (arcs, arbalètes) et sagesse (expérience).
 */
@EventBusSubscriber(modid = KPItemsRarity.MOD_ID)
public final class CombatEvents {
    private CombatEvents() {}

    private static double value(LivingEntity entity, Holder<Attribute> attribute) {
        AttributeInstance instance = entity.getAttribute(attribute);
        return instance != null ? instance.getValue() : attribute.value().getDefaultValue();
    }

    // ---------------------------------------------------------------- Critique (corps à corps)

    /**
     * Le critique vanilla (coup en sautant) est remplacé par la chance de critique du joueur,
     * et son multiplicateur par les dégâts critiques.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onCriticalHit(CriticalHitEvent event) {
        Player player = event.getEntity();
        boolean vanilla = event.isVanillaCritical() && !Config.DISABLE_VANILLA_CRITS.get();
        boolean crit = vanilla || player.getRandom().nextDouble() < value(player, ModAttributes.CRIT_CHANCE);
        event.setCriticalHit(crit);
        if (crit) event.setDamageMultiplier((float) value(player, ModAttributes.CRIT_DAMAGE));
    }

    // ---------------------------------------------------------------- Esquive, critique à distance, perce-armure

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDodge(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) return;
        DamageSource source = event.getSource();
        if (source.getEntity() == null || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        double dodge = value(player, ModAttributes.DODGE_CHANCE);
        if (dodge <= 0 || player.getRandom().nextDouble() >= dodge) return;
        event.setCanceled(true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 1.0f, 1.6f);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        projectileCrit(event, attacker);
        armorPierce(event, attacker);
    }

    /** Critique des projectiles (le corps à corps passe par CriticalHitEvent) : mêmes chance et dégâts, même son. */
    private static void projectileCrit(LivingIncomingDamageEvent event, LivingEntity attacker) {
        if (!(attacker instanceof Player player) || !(event.getSource().getDirectEntity() instanceof Projectile)) return;
        if (player.getRandom().nextDouble() >= value(player, ModAttributes.CRIT_CHANCE)) return;
        event.setAmount(event.getAmount() * (float) value(player, ModAttributes.CRIT_DAMAGE));
        LivingEntity target = event.getEntity();
        target.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 1.0f);
        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
        }
    }

    /** Perce-armure : une partie de l'armure de la cible est ignorée. */
    private static void armorPierce(LivingIncomingDamageEvent event, LivingEntity attacker) {
        double pierce = value(attacker, ModAttributes.ARMOR_PIERCE);
        if (pierce <= 0) return;
        LivingEntity target = event.getEntity();
        event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> {
            double armor = target.getArmorValue();
            if (armor <= 0) return reduction;
            return (float) (reduction * Math.max(0.0, (armor - pierce) / armor));
        });
    }

    // ---------------------------------------------------------------- Vol de vie

    @SubscribeEvent
    public static void onDamageDealt(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker == event.getEntity()) return;
        double steal = value(attacker, ModAttributes.LIFE_STEAL);
        if (steal > 0 && event.getNewDamage() > 0) {
            // Le vol de vie ignore "Soins reçus" : les panoplies qui vivent du vol de vie (Wither noir, Fléau)
            // ont un malus de soins, pas un malus d'elles-mêmes.
            lifeStealing = true;
            try {
                attacker.heal((float) (event.getNewDamage() * steal));
            } finally {
                lifeStealing = false;
            }
        }
    }

    private static boolean lifeStealing;

    // ---------------------------------------------------------------- Soins et renvoi

    /** Soins : multiplie tout ce qui rend de la vie (régénération, potions, nourriture), sauf le vol de vie. */
    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (lifeStealing) return;
        double mult = value(event.getEntity(), ModAttributes.HEALING_RECEIVED);
        if (mult != 1.0) event.setAmount((float) (event.getAmount() * mult));
    }

    /** Renvoi : une part des dégâts de mêlée subis revient à l'attaquant. */
    @SubscribeEvent
    public static void onThorns(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker || attacker == victim) return;
        if (source.is(DamageTypes.THORNS)) return; // pas de ping-pong
        double thorns = value(victim, ModAttributes.THORNS);
        if (thorns <= 0 || event.getNewDamage() <= 0) return;
        attacker.hurt(victim.damageSources().thorns(victim), (float) (event.getNewDamage() * thorns));
    }

    // ---------------------------------------------------------------- Flèches

    @SubscribeEvent
    public static void onArrowSpawn(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof AbstractArrow arrow) || !(arrow.getOwner() instanceof Player player)) return;
        double mult = value(player, ModAttributes.ARROW_DAMAGE);
        if (mult != 1.0) arrow.setBaseDamage(arrow.getBaseDamage() * mult);
    }

    /** Vitesse d'armement : l'arc se bande (l'arbalète se charge) plus vite. Côté client et serveur. */
    private static final Map<LivingEntity, Float> DRAW_PROGRESS = new WeakHashMap<>();

    @SubscribeEvent
    public static void onUseTick(LivingEntityUseItemEvent.Tick event) {
        var item = event.getItem().getItem();
        if (!(item instanceof BowItem) && !(item instanceof CrossbowItem)) return;
        double speed = value(event.getEntity(), ModAttributes.DRAW_SPEED);
        if (Math.abs(speed - 1.0) < 1e-3) return;
        float acc = DRAW_PROGRESS.getOrDefault(event.getEntity(), 0f) + (float) (speed - 1.0);
        int whole = (int) acc; // positif = plus vite, négatif = plus lent
        DRAW_PROGRESS.put(event.getEntity(), acc - whole);
        if (whole != 0) event.setDuration(Math.max(1, event.getDuration() - whole));
    }

    // ---------------------------------------------------------------- Sagesse

    @SubscribeEvent
    public static void onXp(PlayerXpEvent.XpChange event) {
        if (event.getAmount() <= 0) return;
        double mult = value(event.getEntity(), ModAttributes.EXPERIENCE_GAINED);
        if (mult == 1.0) return;
        double raw = event.getAmount() * mult;
        int amount = (int) raw;
        if (event.getEntity().getRandom().nextDouble() < raw - amount) amount++;
        event.setAmount(Math.max(0, amount));
    }
}
