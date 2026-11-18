package net.Kyap.ItemsRarity.util;

import net.Kyap.ItemsRarity.util.ModRarities.ModRarity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.crafting.Ingredient;

/** Petits utilitaires encore utilisés par l'enclume. */
public final class UpgradeHelper {
    private UpgradeHelper() {}

    /** Un objet "à stats" = un objet qui a une définition kp_items. */
    public static boolean isItemUpgradable(ItemStack stack) {
        return StatEngine.hasDefinition(stack);
    }

    public static ModRarity getRarityOrCommon(ItemStack stack) {
        return StatEngine.rarityOf(stack);
    }

    public static boolean isValidRepairResource(ItemStack gearItem, ItemStack resourceItem) {
        if (gearItem.isEmpty() || resourceItem.isEmpty()) return false;
        for (ItemStack candidate : getRepairMaterial(gearItem.getItem())) {
            if (candidate.is(resourceItem.getItem())) return true;
        }
        return false;
    }

    /** Matériau de réparation de l'objet (lingot de fer pour une épée en fer...). Plume si inconnu. */
    public static ItemStack[] getRepairMaterial(Item item) {
        Ingredient ingredient = null;
        if (item instanceof TieredItem tiered) {
            ingredient = tiered.getTier().getRepairIngredient();
        } else if (item instanceof ArmorItem armor) {
            ingredient = armor.getMaterial().value().repairIngredient().get();
        }
        if (ingredient != null && !ingredient.isEmpty()) {
            ItemStack[] stacks = ingredient.getItems();
            if (stacks.length > 0) return stacks;
        }
        return new ItemStack[]{new ItemStack(Items.FEATHER)};
    }
}
