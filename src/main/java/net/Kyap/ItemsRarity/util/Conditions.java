package net.Kyap.ItemsRarity.util;

import net.Kyap.ItemsRarity.data.ItemDefinition;
import net.Kyap.ItemsRarity.registry.ModAttributes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Conditions d'équipement (kp_items "conditions") : une pièce de verre ne donne ses stats et ses passifs
 * que si le porteur reste fragile. Évaluées sur les valeurs réelles du porteur (attributs).
 */
public final class Conditions {
    private Conditions() {}

    /** Toutes les conditions de l'objet sont-elles remplies par ce porteur ? */
    public static boolean met(LivingEntity entity, ItemDefinition def) {
        if (def == null || !def.hasConditions()) return true;
        for (Map.Entry<String, Double> e : def.conditions.entrySet()) {
            if (!met(entity, e.getKey(), e.getValue())) return false;
        }
        return true;
    }

    public static boolean met(LivingEntity entity, String key, double limit) {
        return switch (key) {
            case "max_armor" -> entity.getArmorValue() <= limit;
            case "max_health" -> entity.getMaxHealth() <= limit;
            case "max_healing" -> entity.getAttributeValue(ModAttributes.HEALING_RECEIVED) * 100.0 <= limit + 1e-6;
            default -> true;
        };
    }

    /** Lignes de tooltip : une par condition. */
    public static List<MutableComponent> describe(ItemDefinition def) {
        List<MutableComponent> out = new ArrayList<>();
        if (def == null || !def.hasConditions()) return out;
        def.conditions.forEach((key, limit) -> out.add(Component.translatable("tooltip.itemsrarity.condition." + key, fmt(limit))));
        return out;
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
