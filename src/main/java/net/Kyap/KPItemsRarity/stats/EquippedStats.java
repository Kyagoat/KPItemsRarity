package net.Kyap.KPItemsRarity.stats;

import net.Kyap.KPItemsRarity.component.ItemStats;
import net.Kyap.KPItemsRarity.data.ItemDefinition;
import net.Kyap.KPItemsRarity.data.RarityData;
import net.Kyap.KPItemsRarity.data.SetDefinition;
import net.Kyap.KPItemsRarity.data.StatDefinition;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Ce que porte une créature : pièces de panoplie et total des points de stats (jets, exos, bonus de panoplie). */
public final class EquippedStats {
    private EquippedStats() {}

    /** Nombre de pièces portées par panoplie. */
    public static Map<String, Integer> countSetPieces(LivingEntity entity) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemStack stack : GearSlots.wornGear(entity)) {
            ItemDefinition def = RarityData.itemDef(stack);
            if (def != null && def.hasSet() && Conditions.met(entity, def)) counts.merge(def.set, 1, Integer::sum);
        }
        // Parures : un même objet ne compte qu'une fois (deux anneaux identiques = une pièce).
        Set<Item> seen = new HashSet<>();
        for (ItemStack stack : GearSlots.wornAccessories(entity)) {
            ItemDefinition def = RarityData.itemDef(stack);
            if (def == null || !def.hasSet() || !Conditions.met(entity, def) || !seen.add(stack.getItem())) continue;
            counts.merge(def.set, 1, Integer::sum);
        }
        return counts;
    }

    /** Total des points de stats portés : jets + exos (équipement et accessoires) + bonus de panoplie actifs. */
    public static Map<String, Integer> totalStats(LivingEntity entity) {
        Map<String, Integer> totals = new LinkedHashMap<>();
        List<ItemStack> worn = new ArrayList<>(GearSlots.wornGear(entity));
        worn.addAll(GearSlots.wornAccessories(entity));
        for (ItemStack stack : worn) {
            ItemStats stats = ItemRolls.stats(stack);
            if (stats == null || !Conditions.met(entity, RarityData.itemDef(stack))) continue;
            stats.lines().forEach((statId, points) -> {
                StatDefinition stat = RarityData.stat(statId);
                if (stat == null || !stat.base) totals.merge(statId, points, Integer::sum); // la ligne de base (Dégâts) n'est pas un bonus
            });
            if (stats.hasExo()) totals.merge(stats.exoStat(), stats.exoValue(), Integer::sum);
        }
        countSetPieces(entity).forEach((setId, count) -> {
            SetDefinition set = RarityData.set(setId);
            if (set != null) set.activeBonus(count).forEach((stat, value) -> totals.merge(stat, value, Integer::sum));
        });
        return totals;
    }
}
