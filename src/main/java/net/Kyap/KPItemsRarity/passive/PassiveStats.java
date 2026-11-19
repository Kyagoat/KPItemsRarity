package net.Kyap.KPItemsRarity.passive;

import net.Kyap.KPItemsRarity.data.RarityData;
import net.Kyap.KPItemsRarity.data.StatDefinition;
import net.Kyap.KPItemsRarity.stats.EquippedStats;
import net.minecraft.world.entity.LivingEntity;

/** Les stats dont dépendent les passifs ("50 % de ta Force", "0,5 % par point d'Agilité"). */
public final class PassiveStats {
    private PassiveStats() {}

    /** Points d'une stat portés (objets + panoplies), comme dans la fiche de stats. */
    public static double points(LivingEntity entity, String statId) {
        return EquippedStats.totalStats(entity).getOrDefault(statId, 0);
    }

    /** Valeur d'une stat en fraction : 12 points de Force (%) -> 0,12. */
    public static double fraction(LivingEntity entity, String statId) {
        StatDefinition sd = RarityData.stat(statId);
        double scale = sd != null ? Math.abs(sd.scale) : 0.01;
        return points(entity, statId) * scale;
    }
}
