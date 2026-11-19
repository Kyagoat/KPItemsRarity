package net.Kyap.KPItemsRarity.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/** Identifiant d'objet "mod:objet" tel qu'écrit dans les données (kp_items, kp_sets...). */
public final class ItemIds {
    private ItemIds() {}

    public static String of(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
