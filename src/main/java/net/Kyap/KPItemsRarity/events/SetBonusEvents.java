package net.Kyap.KPItemsRarity.events;

import net.Kyap.KPItemsRarity.KPItemsRarity;
import net.Kyap.KPItemsRarity.compat.CuriosCompat;
import net.Kyap.KPItemsRarity.data.RarityData;
import net.Kyap.KPItemsRarity.data.SetDefinition;
import net.Kyap.KPItemsRarity.data.StatDefinition;
import net.Kyap.KPItemsRarity.passive.Passives;
import net.Kyap.KPItemsRarity.stats.Conditions;
import net.Kyap.KPItemsRarity.stats.EquippedStats;
import net.Kyap.KPItemsRarity.stats.GearSlots;
import net.Kyap.KPItemsRarity.stats.ItemRolls;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bonus de panoplie : modificateurs "transient" posés directement sur le joueur
 * (kpitemsrarity:set/<ns>/<panoplie>/<stat>), recalculés à chaque changement d'équipement.
 * Pas sauvegardés : recalculés à la connexion, au respawn et au rechargement des datapacks.
 */
@EventBusSubscriber(modid = KPItemsRarity.MOD_ID)
public final class SetBonusEvents {
    private SetBonusEvents() {}

    public static final String SET_PREFIX = "set/";
    /** Stats annulées d'un objet dont la condition d'équipement n'est pas remplie. */
    public static final String COND_PREFIX = "cond/";

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) return;
        // Changer d'objet dans la barre rapide déclenche cet event : on ne recalcule que si une pièce de panoplie,
        // une pièce à condition ou une pièce d'armure (l'Armure totale compte dans les conditions) bouge.
        if (isSetPiece(event.getFrom()) || isSetPiece(event.getTo()) || hasConditions(event.getFrom()) || hasConditions(event.getTo())
                || event.getSlot().isArmor()) refresh(player);
    }

    private static boolean isSetPiece(ItemStack stack) {
        var def = RarityData.itemDef(stack);
        return def != null && def.hasSet();
    }

    private static boolean hasConditions(ItemStack stack) {
        var def = RarityData.itemDef(stack);
        return def != null && def.hasConditions();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        refresh(event.getEntity());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        refresh(event.getEntity());
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        refresh(event.getEntity());
    }

    /** /reload : les panoplies ont pu changer. */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        event.getRelevantPlayers().forEach(SetBonusEvents::refresh);
    }

    public static void refresh(LivingEntity entity) {
        if (entity.level().isClientSide()) return;
        removeAll(entity);

        EquippedStats.countSetPieces(entity).forEach((setId, count) -> {
            SetDefinition set = RarityData.set(setId);
            if (set == null) return;
            set.activeBonus(count).forEach((statId, value) -> {
                if (value == null || value == 0) return;
                StatDefinition stat = RarityData.stat(statId);
                if (stat == null) return;
                stat.attributeHolder().ifPresent(attribute -> {
                    AttributeInstance instance = entity.getAttributes().getInstance(attribute);
                    if (instance == null) return;
                    instance.addTransientModifier(new AttributeModifier(
                            modifierId(setId, statId), value * stat.scale, stat.parsedOperation()));
                });
            });
        });

        applyConditions(entity);
        if (entity instanceof Player player) applyPassiveStats(player);

        // Les PV max ont pu baisser : on ne garde pas plus de vie que le maximum.
        if (entity.getHealth() > entity.getMaxHealth()) entity.setHealth(entity.getMaxHealth());
    }

    /**
     * Objets dont la condition n'est pas remplie : leurs stats sont annulées par des modificateurs inverses
     * (kpitemsrarity:cond/<case>/<stat>). Évalué après les bonus de panoplie, sur les valeurs réelles du porteur.
     */
    private static void applyConditions(LivingEntity entity) {
        Map<String, ItemStack> worn = new LinkedHashMap<>();
        for (EquipmentSlot slot : GearSlots.GEAR_SLOTS) {
            var stack = entity.getItemBySlot(slot);
            if (GearSlots.isWornCorrectly(stack, slot)) worn.put(slot.getName(), stack);
        }
        int i = 0;
        for (var stack : CuriosCompat.equipped(entity)) {
            if (GearSlots.isAccessory(stack)) worn.put("curio" + i++, stack);
        }
        worn.forEach((key, stack) -> {
            var def = RarityData.itemDef(stack);
            if (def == null || Conditions.met(entity, def)) return;
            var stats = ItemRolls.stats(stack);
            if (stats == null) return;
            stats.pointsWithExo().forEach((statId, value) -> {
                StatDefinition stat = RarityData.stat(statId);
                if (stat == null || stat.base || value == 0) return;
                stat.attributeHolder().ifPresent(attribute -> {
                    AttributeInstance instance = entity.getAttributes().getInstance(attribute);
                    if (instance == null) return;
                    ResourceLocation id = KPItemsRarity.id(COND_PREFIX + key + "/" + statId);
                    instance.removeModifier(id);
                    instance.addTransientModifier(new AttributeModifier(id, -value * stat.scale, stat.parsedOperation()));
                });
            });
        });
    }

    /** Points de stat ajoutés par les passifs portés (kpitemsrarity:set/passive/<stat>), comme un bonus de panoplie. */
    private static void applyPassiveStats(Player player) {
        Map<String, Double> points = new LinkedHashMap<>();
        Passives.onRefresh(player, (statId, value) -> points.merge(statId, value, Double::sum));
        points.forEach((statId, value) -> {
            StatDefinition stat = RarityData.stat(statId);
            if (stat == null || value == 0) return;
            stat.attributeHolder().ifPresent(attribute -> {
                AttributeInstance instance = player.getAttributes().getInstance(attribute);
                if (instance != null) instance.addTransientModifier(new AttributeModifier(
                        KPItemsRarity.id(SET_PREFIX + "passive/" + statId), value * stat.scale, stat.parsedOperation()));
            });
        });
    }

    private static void removeAll(LivingEntity entity) {
        for (Holder<Attribute> attribute : attributesUsedByStats()) {
            AttributeInstance instance = entity.getAttributes().getInstance(attribute);
            if (instance == null) continue;
            List<ResourceLocation> toRemove = new ArrayList<>();
            for (AttributeModifier modifier : instance.getModifiers()) {
                ResourceLocation id = modifier.id();
                if (id.getNamespace().equals(KPItemsRarity.MOD_ID) && (id.getPath().startsWith(SET_PREFIX) || id.getPath().startsWith(COND_PREFIX))) {
                    toRemove.add(id);
                }
            }
            toRemove.forEach(instance::removeModifier);
        }
    }

    private static Set<Holder<Attribute>> attributesUsedByStats() {
        Set<Holder<Attribute>> out = new HashSet<>();
        for (StatDefinition stat : RarityData.statsSorted()) stat.attributeHolder().ifPresent(out::add);
        return out;
    }

    public static ResourceLocation modifierId(String setId, String statId) {
        ResourceLocation set = ResourceLocation.tryParse(setId);
        String path = set != null ? set.getNamespace() + "/" + set.getPath() : setId.replace(':', '/');
        return KPItemsRarity.id(SET_PREFIX + path + "/" + statId);
    }
}
