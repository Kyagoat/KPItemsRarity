package net.Kyap.ItemsRarity.item;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;

public final class ModToolTiers {
    private ModToolTiers() {}

    // 1.21 : plus de TierSortingRegistry ; le niveau de minage passe par un tag de blocs "incorrects".
    public static final Tier EMERALD = new SimpleTier(
            BlockTags.INCORRECT_FOR_IRON_TOOL,
            32,      // durabilité
            6.0F,    // vitesse de minage
            6.0F,    // bonus de dégâts
            15,      // enchantabilité
            () -> Ingredient.of(Items.EMERALD));
}
