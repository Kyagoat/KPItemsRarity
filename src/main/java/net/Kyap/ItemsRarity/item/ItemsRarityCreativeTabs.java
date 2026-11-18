package net.Kyap.ItemsRarity.item;

import net.Kyap.ItemsRarity.ItemsRarity;
import net.Kyap.ItemsRarity.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ItemsRarityCreativeTabs {
    private ItemsRarityCreativeTabs() {}

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ItemsRarity.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ITEMS_RARITY_TAB =
            CREATIVE_MODE_TABS.register("items_rarity_tab", () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(ModItems.BISMUTH.get()))
                    .title(Component.translatable("creativemodetab.items_rarity_tab"))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.FLOURITE.get());
                        output.accept(ModItems.SULFUR.get());
                        output.accept(ModItems.BISMUTH.get());
                        output.accept(ModBlocks.ENHANCED_ANVIL_BLOCK.get());
                        output.accept(ModItems.EMERALD_HAMMER.get());
                        // Toutes les runes : chaque stat, en rune, Pa et Ra
                        for (var stat : net.Kyap.ItemsRarity.data.RarityData.statsSorted()) {
                            if (stat.base || stat.legacy || !stat.isActive()) continue;
                            for (int power : new int[]{net.Kyap.ItemsRarity.component.RuneData.BASE,
                                    net.Kyap.ItemsRarity.component.RuneData.PA, net.Kyap.ItemsRarity.component.RuneData.RA}) {
                                output.accept(net.Kyap.ItemsRarity.item.custom.RuneItem.of(stat.id, power, 1));
                            }
                        }
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
