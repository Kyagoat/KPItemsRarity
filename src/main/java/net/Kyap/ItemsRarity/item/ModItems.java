package net.Kyap.ItemsRarity.item;

import net.Kyap.ItemsRarity.ItemsRarity;
import net.Kyap.ItemsRarity.item.custom.EmeraldHammer;
import net.Kyap.ItemsRarity.item.custom.UpgradeMaterialItem;
import net.Kyap.ItemsRarity.util.ModRarities.ModRarity;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ItemsRarity.MOD_ID);

    public static final DeferredItem<Item> FLOURITE = ITEMS.register("flourite",
            () -> new UpgradeMaterialItem(new Item.Properties().rarity(ModRarity.RARE.getRarity()), "flourite", 3));

    public static final DeferredItem<Item> SULFUR = ITEMS.register("sulfur",
            () -> new UpgradeMaterialItem(new Item.Properties().rarity(ModRarity.EPIC.getRarity()), "sulfur", 3));

    public static final DeferredItem<Item> BISMUTH = ITEMS.register("bismuth",
            () -> new UpgradeMaterialItem(new Item.Properties().rarity(ModRarity.LEGENDARY.getRarity()), "bismuth", 3));

    /** Rune de forgemagie : la stat et la taille sont dans le composant itemsrarity:rune. */
    public static final DeferredItem<Item> RUNE = ITEMS.register("rune",
            () -> new net.Kyap.ItemsRarity.item.custom.RuneItem(new Item.Properties()));

    public static final DeferredItem<Item> EMERALD_HAMMER = ITEMS.register("emerald_hammer", EmeraldHammer::new);

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
