package net.Kyap.KPItemsRarity.stats;

import net.Kyap.KPItemsRarity.data.ItemDefinition;
import net.Kyap.KPItemsRarity.registry.ModAttributes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * Conditions d'équipement (kp_items, "conditions") : une pièce de verre ne donne ses stats et ses passifs que si
 * son porteur reste fragile. Chaque condition est un plafond, comparé aux valeurs réelles du porteur :
 *
 *   "max_armor"    Armure totale
 *   "max_health"   Vie maximale, en PV
 *   "max_healing"  Soins reçus, en %
 *
 * Une condition inconnue (faute de frappe, condition d'une version future) est considérée comme remplie.
 */
public final class Conditions {
    private Conditions() {}

    private static final Map<String, ToDoubleFunction<LivingEntity>> MEASURES = Map.of(
            "max_armor", LivingEntity::getArmorValue,
            "max_health", LivingEntity::getMaxHealth,
            "max_healing", entity -> entity.getAttributeValue(ModAttributes.HEALING_RECEIVED) * 100.0);

    /** Le porteur remplit-il toutes les conditions de l'objet ? (Vrai pour un objet sans condition.) */
    public static boolean met(LivingEntity entity, @Nullable ItemDefinition def) {
        if (def == null) return true;
        for (Map.Entry<String, Double> condition : def.conditions.entrySet()) {
            if (!met(entity, condition.getKey(), condition.getValue())) return false;
        }
        return true;
    }

    public static boolean met(LivingEntity entity, String condition, double limit) {
        ToDoubleFunction<LivingEntity> measure = MEASURES.get(condition);
        return measure == null || measure.applyAsDouble(entity) <= limit + 1e-6;
    }

    /** "Armure totale ≤ 8" (clé tooltip.kpitemsrarity.condition.<condition>), la limite telle qu'écrite dans le fichier. */
    public static Component text(String condition, double limit) {
        String shown = limit == Math.rint(limit) ? String.valueOf((long) limit) : String.valueOf(limit);
        return Component.translatable("tooltip.kpitemsrarity.condition." + condition, shown);
    }
}
