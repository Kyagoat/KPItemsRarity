package net.Kyap.KPItemsRarity.stats;

import net.Kyap.KPItemsRarity.component.ItemStats;
import net.Kyap.KPItemsRarity.component.ModDataComponents;
import net.Kyap.KPItemsRarity.data.ItemDefinition;
import net.Kyap.KPItemsRarity.data.RarityData;
import net.Kyap.KPItemsRarity.data.RollRange;
import net.Kyap.KPItemsRarity.data.StatDefinition;
import net.Kyap.KPItemsRarity.util.ModRarities.ModRarity;
import net.minecraft.world.item.ItemStack;

/**
 * La rareté dépend de CE QUE L'OBJET A TIRÉ, pas seulement de l'objet :
 *   qualité des jets   < 30 % -> Commun
 *                      < 60 % -> Peu commun
 *                      < 85 % -> Rare
 *                      sinon  -> Épique (jet presque parfait)
 *   1 cran de forge           -> Légendaire
 *   2 crans de forge          -> Mythique
 * Le champ "rarity" d'une définition est une rareté MINIMUM (un objet de boss peut être au moins Rare).
 */
public final class DynamicRarity {
    private DynamicRarity() {}

    /** Seuils de qualité des jets (0 à 1) pour Peu commun, Rare et Épique. */
    public static final float UNCOMMON_AT = 0.30f, RARE_AT = 0.60f, EPIC_AT = 0.85f;

    public static ModRarity rarityFor(ItemDefinition def, ItemStats stats) {
        ModRarity fromRoll;
        if (stats.upgrades() >= 2) fromRoll = ModRarity.MYTHIC;
        else if (stats.upgrades() == 1) fromRoll = ModRarity.LEGENDARY;
        else {
            float q = quality(def, stats);
            fromRoll = q >= EPIC_AT ? ModRarity.EPIC : q >= RARE_AT ? ModRarity.RARE : q >= UNCOMMON_AT ? ModRarity.UNCOMMON : ModRarity.COMMON;
        }
        ModRarity floor = def.minimumRarity();
        return fromRoll.ordinal() >= floor.ordinal() ? fromRoll : floor;
    }

    /** Qualité des jets de 0 à 1 (1 = jet parfait), hors lignes de base. */
    public static float quality(ItemStack stack) {
        ItemStats stats = ItemRolls.stats(stack);
        ItemDefinition def = RarityData.itemDef(stack);
        return stats == null || def == null ? 0f : quality(def, stats);
    }

    public static float quality(ItemDefinition def, ItemStats stats) {
        float total = 0;
        int count = 0;
        for (String stat : stats.lines().keySet()) {
            StatDefinition sd = RarityData.stat(stat);
            RollRange r = ItemRolls.range(def, stat, stats.upgrades());
            if (r == null || (sd != null && sd.base)) continue;
            count++;
            // Malus (fourchette négative) : plus proche de 0 = meilleur, la formule reste la même.
            total += r.position(stats.get(stat));
        }
        return count == 0 ? 1f : total / count;
    }

    /** La rareté de l'objet (celle tirée, sinon le minimum de sa définition, sinon Commun). */
    public static ModRarity rarityOf(ItemStack stack) {
        ModRarity r = stack.get(ModDataComponents.RARITY);
        if (r != null) return r;
        ItemDefinition def = RarityData.itemDef(stack);
        return def != null ? def.minimumRarity() : ModRarity.COMMON;
    }
}
