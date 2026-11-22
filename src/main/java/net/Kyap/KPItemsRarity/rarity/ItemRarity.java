package net.Kyap.KPItemsRarity.rarity;

import net.Kyap.KPItemsRarity.component.ItemStats;
import net.Kyap.KPItemsRarity.component.ModDataComponents;
import net.Kyap.KPItemsRarity.data.ItemDefinition;
import net.Kyap.KPItemsRarity.data.RarityData;
import net.Kyap.KPItemsRarity.data.RollRange;
import net.Kyap.KPItemsRarity.data.StatDefinition;
import net.Kyap.KPItemsRarity.stats.ItemRolls;
import net.minecraft.world.item.ItemStack;

/**
 * La rareté d'un équipement du mod dépend de CE QU'IL A TIRÉ, pas seulement de l'objet :
 *
 *   qualité des jets   < 30 %  Commun
 *                      < 60 %  Peu commun
 *                      < 85 %  Rare
 *                      sinon   Épique (jet presque parfait)
 *   1 cran de forge            Légendaire
 *   2 crans de forge           Mythique
 *
 * Le champ "rarity" de sa définition est un minimum (un objet de boss peut être au moins Rare). Les autres objets
 * gardent leur rareté vanilla, ou celle que leur donnent les fichiers kp_rarity (voir RarityOverrides).
 */
public final class ItemRarity {
    private ItemRarity() {}

    /** Qualité des jets (0 à 1) à partir de laquelle l'objet est Peu commun, Rare, Épique. */
    public static final float UNCOMMON_AT = 0.30f, RARE_AT = 0.60f, EPIC_AT = 0.85f;

    /** La rareté de n'importe quel objet, telle qu'affichée. */
    public static ModRarity of(ItemStack stack) {
        ModRarity rolled = stack.get(ModDataComponents.RARITY);
        if (rolled != null) return rolled;
        ItemDefinition def = RarityData.itemDef(stack);
        if (def != null) return def.minimumRarity(); // pas encore tiré (menu créatif, JEI)
        return ModRarity.fromVanilla(stack.getRarity()); // kp_rarity compris : voir ItemStackMixin
    }

    /** La rareté que méritent ces jets, au moins celle de la définition. */
    public static ModRarity rolled(ItemDefinition def, ItemStats stats) {
        ModRarity fromRoll = switch (stats.upgrades()) {
            case 0 -> byQuality(quality(def, stats));
            case 1 -> ModRarity.LEGENDARY;
            default -> ModRarity.MYTHIC;
        };
        return ModRarity.max(fromRoll, def.minimumRarity());
    }

    private static ModRarity byQuality(float quality) {
        if (quality >= EPIC_AT) return ModRarity.EPIC;
        if (quality >= RARE_AT) return ModRarity.RARE;
        if (quality >= UNCOMMON_AT) return ModRarity.UNCOMMON;
        return ModRarity.COMMON;
    }

    /** Qualité des jets de l'objet, de 0 à 1 (1 = jet parfait) ; 0 s'il n'est pas tiré. */
    public static float quality(ItemStack stack) {
        ItemStats stats = ItemRolls.stats(stack);
        ItemDefinition def = RarityData.itemDef(stack);
        return stats == null || def == null ? 0f : quality(def, stats);
    }

    /**
     * La moyenne de la position de chaque ligne dans sa fourchette, sans la ligne de base (Dégâts).
     * Un malus (fourchette négative) compte de la même façon : plus proche de 0 = meilleur.
     */
    public static float quality(ItemDefinition def, ItemStats stats) {
        float total = 0;
        int count = 0;
        for (String statId : stats.lines().keySet()) {
            StatDefinition stat = RarityData.stat(statId);
            RollRange range = ItemRolls.range(def, statId, stats.upgrades());
            if (range == null || stat != null && stat.base) continue;
            total += range.position(stats.get(statId));
            count++;
        }
        return count == 0 ? 1f : total / count;
    }
}
