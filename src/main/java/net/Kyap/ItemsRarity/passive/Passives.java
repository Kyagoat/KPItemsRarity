package net.Kyap.ItemsRarity.passive;

import net.Kyap.ItemsRarity.ItemsRarity;
import net.Kyap.ItemsRarity.compat.CuriosCompat;
import net.Kyap.ItemsRarity.data.ItemDefinition;
import net.Kyap.ItemsRarity.data.PassiveDefinition;
import net.Kyap.ItemsRarity.data.RarityData;
import net.Kyap.ItemsRarity.data.SetDefinition;
import net.Kyap.ItemsRarity.data.StatDefinition;
import net.Kyap.ItemsRarity.network.PassiveCooldownPayload;
import net.Kyap.ItemsRarity.registry.ModAttributes;
import net.Kyap.ItemsRarity.util.Conditions;
import net.Kyap.ItemsRarity.util.StatEngine;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Les passifs (data/<ns>/kp_passives) : des effets à déclencheur portés par un objet ou débloqués par une panoplie.
 * Leurs valeurs se calculent à partir des stats de base du porteur ("50 % de ta Force", "0,5 % par point d'Agilité").
 *
 * Effets disponibles (champ "effect" ; paramètres dans "params") :
 *  bonus_damage      source (arrow|melee|any), stat, ratio : dégâts × (1 + ratio × stat en %)
 *  life_steal        ratio, below_health, low_ratio : soigne de ratio × Soins des dégâts infligés
 *  dodge             stat, per_point, max, on_dodge (slow|teleport_behind), duration, no_rain
 *  reflect           stat (Armure par défaut), per_point : renvoie per_point × points des dégâts de mêlée
 *  armor_ignore      crit_only, flat, stat, per, every_nth : ignore une partie de l'armure de la cible
 *  crit_explosion    max_health, radius : un coup critique sous max_health PV fait exploser la cible (sans casser de blocs)
 *  fall_bounce       min_fall, strength : pas de dégâts de chute au-delà de min_fall, on rebondit
 *  strength_in_water bonus : +bonus de dégâts dans l'eau
 *  cheat_death       health : un coup mortel laisse à "health" PV
 *  share_healing     ratio, radius : une part des soins reçus soigne les joueurs proches
 *  healing_in_water  multiplier : soins reçus multipliés dans l'eau
 *  damage_reduction  source (projectile|melee|any), ratio : dégâts reçus × (1 − ratio)
 *  flat_damage       source (melee|projectile|any), stat (max_health|armor|<stat>), per, amount :
 *                    + amount dégâts par tranche de "per" (moteur : 1 dégât par 10 PV max...)
 *  distance_bonus    min_distance, per_block, max : tir à plus de min_distance blocs, + per_block par bloc
 *  ricochet          ratio, radius : une flèche qui tue touche le monstre le plus proche (ratio des dégâts)
 *  repel             strength, min_fall : un coup de mêlée reçu repousse l'attaquant ; au-delà de
 *                    min_fall blocs (si présent), une chute fait rebondir au lieu de blesser
 * Chaque passif peut avoir une recharge ("cooldown", secondes).
 *
 * Hors passifs : la Force (attaque) s'applique aussi aux flèches et au trident lancé du joueur.
 *
 * Tout se calcule au moment d'un coup donné ou reçu (ou d'un soin, d'une chute) : aucun calcul à chaque tick.
 */
@EventBusSubscriber(modid = ItemsRarity.MOD_ID)
public final class Passives {
    private Passives() {}

    // ---------------------------------------------------------------- Quels passifs sont actifs

    /** Les passifs actifs d'une créature : objets portés (armure, mains, Curios) + panoplies au bon palier. */
    public static List<PassiveDefinition> active(LivingEntity entity) {
        Set<String> ids = new LinkedHashSet<>();
        for (EquipmentSlot slot : StatEngine.GEAR_SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!StatEngine.isWornCorrectly(stack, slot)) continue;
            ItemDefinition def = RarityData.itemDef(stack);
            if (def != null && def.passives != null && Conditions.met(entity, def)) ids.addAll(def.passives);
        }
        for (ItemStack stack : CuriosCompat.equipped(entity)) {
            ItemDefinition def = RarityData.itemDef(stack);
            if (def != null && def.passives != null && StatEngine.isAccessory(stack) && Conditions.met(entity, def)) ids.addAll(def.passives);
        }
        StatEngine.countSetPieces(entity).forEach((setId, count) -> {
            SetDefinition set = RarityData.set(setId);
            if (set != null) ids.addAll(set.activePassives(count));
        });
        List<PassiveDefinition> out = new ArrayList<>();
        for (String id : ids) {
            PassiveDefinition p = RarityData.passive(id);
            if (p != null) out.add(p);
        }
        return out;
    }

    private static List<PassiveDefinition> of(LivingEntity entity, String effect) {
        if (!(entity instanceof Player)) return List.of(); // les passifs sont pour les joueurs
        List<PassiveDefinition> out = new ArrayList<>();
        for (PassiveDefinition p : active(entity)) if (effect.equals(p.effect)) out.add(p);
        return out;
    }

    /** Points d'une stat portés (objets + panoplies), comme dans le panneau de stats. */
    public static double points(LivingEntity entity, String statId) {
        return StatEngine.totalStats(entity).getOrDefault(statId, 0);
    }

    /** Valeur d'une stat en fraction : 12 points de Force (%) -> 0,12. */
    private static double fraction(LivingEntity entity, String statId) {
        StatDefinition sd = RarityData.stat(statId);
        double scale = sd != null ? Math.abs(sd.scale) : 0.01;
        return points(entity, statId) * scale;
    }

    // ---------------------------------------------------------------- Recharges

    private static final Map<UUID, Map<String, Long>> COOLDOWNS = new ConcurrentHashMap<>();

    /** Le passif est-il prêt ? S'il l'est et a une recharge, on la lance. */
    private static boolean ready(LivingEntity entity, PassiveDefinition p) {
        if (p.cooldown <= 0) return true;
        long now = entity.level().getGameTime();
        Map<String, Long> map = COOLDOWNS.computeIfAbsent(entity.getUUID(), k -> new HashMap<>());
        Long until = map.get(p.id);
        if (until != null && until > now) return false;
        int ticks = (int) Math.round(p.cooldown * 20);
        map.put(p.id, now + ticks);
        if (entity instanceof ServerPlayer sp) PacketDistributor.sendToPlayer(sp, new PassiveCooldownPayload(p.id, ticks));
        return true;
    }

    // Côté client : fin de recharge, en millisecondes réelles (pour le voyant du tooltip).
    private static final Map<String, Long> CLIENT_COOLDOWNS = new ConcurrentHashMap<>();

    public static void clientCooldown(String passive, int ticks) {
        CLIENT_COOLDOWNS.put(passive, net.minecraft.Util.getMillis() + ticks * 50L);
    }

    /** Secondes de recharge restantes côté client (0 = prêt). */
    public static int clientCooldownLeft(String passive) {
        Long until = CLIENT_COOLDOWNS.get(passive);
        if (until == null) return 0;
        long left = until - net.minecraft.Util.getMillis();
        return left <= 0 ? 0 : (int) Math.ceil(left / 1000.0);
    }

    // ---------------------------------------------------------------- Attaque

    /** Coups critiques de mêlée du tick en cours (pour armor_ignore / crit_explosion). */
    private static final Map<LivingEntity, Long> LAST_CRIT = new WeakHashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCrit(CriticalHitEvent event) {
        if (event.isCriticalHit()) LAST_CRIT.put(event.getEntity(), event.getEntity().level().getGameTime());
    }

    private static boolean critNow(LivingEntity attacker) {
        Long t = LAST_CRIT.get(attacker);
        return t != null && t == attacker.level().getGameTime();
    }

    /** Coups d'affilée sur la même cible (armor_ignore every_nth). */
    private static final Map<LivingEntity, int[]> STREAK = new WeakHashMap<>();

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onOutgoing(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof Player attacker) || attacker.level().isClientSide()) return;
        LivingEntity target = event.getEntity();
        boolean projectile = source.getDirectEntity() instanceof Projectile;
        boolean melee = source.getDirectEntity() == attacker;
        // La Force compte aussi pour les flèches et le trident lancé (en mêlée elle passe par l'attribut).
        if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.AbstractArrow) {
            double strength = fraction(attacker, "strength");
            if (strength != 0) event.setAmount((float) (event.getAmount() * Math.max(0.0, 1 + strength)));
        }
        List<PassiveDefinition> all = active(attacker);
        if (all.isEmpty()) return;

        for (PassiveDefinition p : all) {
            switch (p.effect) {
                case "bonus_damage" -> {
                    String src = p.str("source", "any");
                    if ((src.equals("arrow") && !projectile) || (src.equals("melee") && !melee)) continue;
                    double bonus = p.num("ratio", 0) * fraction(attacker, p.str("stat", "strength"));
                    if (bonus != 0) event.setAmount((float) (event.getAmount() * (1 + bonus)));
                }
                case "flat_damage" -> {
                    String src = p.str("source", "melee");
                    if ((src.equals("projectile") && !projectile) || (src.equals("melee") && !melee)) continue;
                    String stat = p.str("stat", "max_health");
                    double value = switch (stat) {
                        case "max_health" -> attacker.getMaxHealth();
                        case "armor" -> attacker.getArmorValue();
                        default -> points(attacker, stat);
                    };
                    double per = Math.max(1e-6, p.num("per", 10));
                    double add = Math.floor(value / per) * p.num("amount", 1);
                    if (add > 0) event.setAmount((float) (event.getAmount() + add));
                }
                case "distance_bonus" -> {
                    if (!projectile) continue;
                    double over = attacker.distanceTo(target) - p.num("min_distance", 10);
                    if (over <= 0) continue;
                    double bonus = Math.min(p.num("max", 0.3), over * p.num("per_block", 0.02));
                    event.setAmount((float) (event.getAmount() * (1 + bonus)));
                }
                case "strength_in_water" -> {
                    if (attacker.isInWaterOrRain() && attacker.isInWater()) event.setAmount((float) (event.getAmount() * (1 + p.num("bonus", 0.15))));
                }
                case "armor_ignore" -> {
                    if (!melee && !projectile) continue;
                    boolean crit = melee && critNow(attacker);
                    double ignore = 0;
                    if (!p.flag("crit_only") || crit) {
                        ignore += p.num("flat", 0);
                        double per = p.num("per", 0);
                        if (per > 0) ignore += Math.floor(points(attacker, p.str("stat", "strength")) / per);
                    }
                    int nth = (int) p.num("every_nth", 0);
                    if (nth > 0) {
                        int[] s = STREAK.computeIfAbsent(attacker, k -> new int[2]);
                        if (s[0] != target.getId()) {
                            s[0] = target.getId();
                            s[1] = 0;
                        }
                        if (++s[1] >= nth) {
                            s[1] = 0;
                            ignore = 1024;
                        }
                    }
                    if (ignore > 0) {
                        double ign = ignore;
                        event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> {
                            double armor = target.getArmorValue();
                            if (armor <= 0) return reduction;
                            return (float) (reduction * Math.max(0.0, (armor - ign) / armor));
                        });
                    }
                }
                case "crit_explosion" -> {
                    if (!melee || !critNow(attacker) || attacker.getHealth() > p.num("max_health", 2.0) || !ready(attacker, p)) continue;
                    attacker.level().explode(attacker, target.getX(), target.getY(0.5), target.getZ(),
                            (float) p.num("radius", 2.5), Level.ExplosionInteraction.NONE);
                }
                default -> {
                }
            }
        }
    }

    // ---------------------------------------------------------------- Défense

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncoming(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim) || victim.level().isClientSide()) return;
        DamageSource source = event.getSource();
        if (source.getEntity() == null || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        for (PassiveDefinition p : of(victim, "dodge")) {
            if (p.flag("no_rain") && victim.isInWaterOrRain()) continue;
            double chance = Math.min(p.num("max", 0.5), p.num("per_point", 0.005) * points(victim, p.str("stat", "agility")));
            if (chance <= 0 || victim.getRandom().nextDouble() >= chance) continue;
            event.setCanceled(true);
            victim.level().playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 1.0f, 1.6f);
            if (victim.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.CLOUD, victim.getX(), victim.getY() + 1.0, victim.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
            }
            if (source.getEntity() instanceof LivingEntity attacker) {
                switch (p.str("on_dodge", "")) {
                    case "slow" -> attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                            (int) (p.num("duration", 2) * 20), 1), victim);
                    case "teleport_behind" -> {
                        if (ready(victim, p)) {
                            Vec3 look = attacker.getLookAngle().multiply(1, 0, 1).normalize();
                            Vec3 to = attacker.position().subtract(look.scale(p.num("distance", 3)));
                            if (victim.randomTeleport(to.x, attacker.getY(), to.z, true)) {
                                victim.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, attacker.getEyePosition());
                            }
                        }
                    }
                    default -> {
                    }
                }
            }
            return;
        }
        boolean projectile = source.getDirectEntity() instanceof Projectile;
        boolean melee = source.getDirectEntity() == source.getEntity() && !source.is(DamageTypeTags.IS_EXPLOSION);
        for (PassiveDefinition p : of(victim, "damage_reduction")) {
            String src = p.str("source", "any");
            if ((src.equals("projectile") && !projectile) || (src.equals("melee") && !melee)) continue;
            event.setAmount((float) (event.getAmount() * Math.max(0.0, 1 - p.num("ratio", 0))));
        }
        if (melee && source.getEntity() instanceof LivingEntity attacker && attacker != victim) {
            for (PassiveDefinition p : of(victim, "repel")) {
                attacker.knockback(p.num("strength", 0.8), victim.getX() - attacker.getX(), victim.getZ() - attacker.getZ());
                break;
            }
        }
    }

    /** Coup mortel évité (cheat_death) : le coup laisse "health" PV. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLethal(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player victim) || victim.level().isClientSide()) return;
        if (event.getNewDamage() < victim.getHealth()) return;
        for (PassiveDefinition p : of(victim, "cheat_death")) {
            if (!ready(victim, p)) continue;
            event.setNewDamage(Math.max(0f, victim.getHealth() - (float) p.num("health", 2.0)));
            victim.level().playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6f, 1.3f);
            if (victim.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, victim.getX(), victim.getY() + 1.0, victim.getZ(), 30, 0.4, 0.6, 0.4, 0.4);
            }
            return;
        }
    }

    private static boolean passiveHealing;
    private static boolean ricocheting;

    @SubscribeEvent
    public static void onDealt(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || event.getNewDamage() <= 0) return;
        DamageSource source = event.getSource();

        // Vol de vie : proportionnel aux Soins du porteur, plus fort sous un seuil de vie.
        if (source.getEntity() instanceof Player attacker && attacker != victim) {
            for (PassiveDefinition p : of(attacker, "life_steal")) {
                double ratio = p.num("ratio", 0);
                if (attacker.getHealth() <= attacker.getMaxHealth() * p.num("below_health", 0)) ratio = p.num("low_ratio", ratio);
                double healing = attacker.getAttributeValue(ModAttributes.HEALING_RECEIVED); // 1 = 100 %
                float amount = (float) (event.getNewDamage() * ratio * healing);
                if (amount > 0) attacker.setHealth(Math.min(attacker.getMaxHealth(), attacker.getHealth() + amount));
            }
        }

        // Ricochet : une flèche qui tue rebondit sur le monstre le plus proche.
        if (!ricocheting && victim.getHealth() <= 0 && source.getEntity() instanceof Player shooter
                && source.getDirectEntity() instanceof Projectile) {
            for (PassiveDefinition p : of(shooter, "ricochet")) {
                double r = p.num("radius", 8);
                LivingEntity next = null;
                double best = Double.MAX_VALUE;
                for (var m : victim.level().getEntitiesOfClass(LivingEntity.class,
                        victim.getBoundingBox().inflate(r), e -> e != victim && e != shooter && e.isAlive() && e instanceof net.minecraft.world.entity.monster.Enemy)) {
                    double d = m.distanceToSqr(victim);
                    if (d < best) {
                        best = d;
                        next = m;
                    }
                }
                if (next == null) break;
                ricocheting = true;
                try {
                    next.hurt(source, (float) (event.getNewDamage() * p.num("ratio", 0.5)));
                } finally {
                    ricocheting = false;
                }
                if (victim.level() instanceof ServerLevel level) {
                    level.sendParticles(ParticleTypes.CRIT, next.getX(), next.getY(0.6), next.getZ(), 8, 0.2, 0.3, 0.2, 0.1);
                }
                break;
            }
        }

        // Renvoi : une part des dégâts de mêlée revient à l'attaquant, selon l'Armure.
        if (victim instanceof Player player && source.getEntity() instanceof LivingEntity attacker
                && source.getDirectEntity() == attacker && attacker != victim
                && !source.is(net.minecraft.world.damagesource.DamageTypes.THORNS)) {
            for (PassiveDefinition p : of(player, "reflect")) {
                double pts = p.str("stat", "armor").equals("armor") ? player.getArmorValue() : points(player, p.str("stat", ""));
                float back = (float) (event.getNewDamage() * p.num("per_point", 0.01) * pts);
                if (back > 0) attacker.hurt(player.damageSources().thorns(player), back);
            }
        }
    }

    // ---------------------------------------------------------------- Soins, chutes

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHeal(LivingHealEvent event) {
        if (passiveHealing || !(event.getEntity() instanceof Player player) || player.level().isClientSide()) return;
        for (PassiveDefinition p : of(player, "healing_in_water")) {
            if (player.isInWater()) event.setAmount((float) (event.getAmount() * p.num("multiplier", 2)));
        }
        List<PassiveDefinition> share = of(player, "share_healing");
        if (share.isEmpty() || event.getAmount() <= 0) return;
        passiveHealing = true;
        try {
            for (PassiveDefinition p : share) {
                double r = p.num("radius", 6);
                float part = (float) (event.getAmount() * p.num("ratio", 0.3));
                for (Player other : player.level().getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(r), o -> o != player && o.isAlive())) {
                    other.heal(part);
                }
            }
        } finally {
            passiveHealing = false;
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide() || player.isShiftKeyDown()) return;
        List<PassiveDefinition> bounce = new ArrayList<>(of(player, "fall_bounce"));
        for (PassiveDefinition p : of(player, "repel")) if (p.num("min_fall", -1) >= 0) bounce.add(p);
        for (PassiveDefinition p : bounce) {
            if (event.getDistance() < p.num("min_fall", 6)) continue;
            event.setDamageMultiplier(0);
            double up = Math.min(1.2, Math.sqrt(event.getDistance()) * p.num("strength", 0.18));
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(v.x, up, v.z);
            player.hurtMarked = true;
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 1.0f, 1.0f);
            return;
        }
    }
}
