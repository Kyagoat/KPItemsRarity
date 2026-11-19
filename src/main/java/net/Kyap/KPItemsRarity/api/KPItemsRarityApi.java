package net.Kyap.KPItemsRarity.api;

import net.Kyap.KPItemsRarity.component.ModDataComponents;
import net.Kyap.KPItemsRarity.stats.ItemRolls;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * Points d'entrée stables pour les autres mods (KP PNJ, KP Forge...). Ces noms ne changent pas quand le
 * reste du mod est réorganisé : un mod sans dépendance de compilation peut les appeler par réflexion
 * ("net.Kyap.KPItemsRarity.api.KPItemsRarityApi").
 */
public final class KPItemsRarityApi {
    private KPItemsRarityApi() {}

    /** Donne ses jets à un équipement qui n'en a pas encore (comme un objet fabriqué par un joueur). */
    public static void ensureRolled(ItemStack stack, RandomSource random) {
        ItemRolls.ensureRolled(stack, random);
    }

    /** L'objet porte-t-il des jets de stats ? */
    public static boolean hasStats(ItemStack stack) {
        return !stack.isEmpty() && stack.has(ModDataComponents.STATS);
    }
}
