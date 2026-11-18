package net.Kyap.ItemsRarity;

import com.mojang.logging.LogUtils;
import net.Kyap.ItemsRarity.block.ModBlocks;
import net.Kyap.ItemsRarity.block.entity.ModBlockEntities;
import net.Kyap.ItemsRarity.component.ModDataComponents;
import net.Kyap.ItemsRarity.item.ItemsRarityCreativeTabs;
import net.Kyap.ItemsRarity.item.ModItems;
import net.Kyap.ItemsRarity.loot.ModLootModifiers;
import net.Kyap.ItemsRarity.screen.ModMenuTypes;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(ItemsRarity.MOD_ID)
public class ItemsRarity {
    public static final String MOD_ID = "itemsrarity";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ItemsRarity(IEventBus modEventBus, ModContainer modContainer) {
        ModDataComponents.register(modEventBus);
        net.Kyap.ItemsRarity.registry.ModAttributes.register(modEventBus);
        net.Kyap.ItemsRarity.compat.CuriosCompat.init();
        if (net.neoforged.fml.ModList.get().isLoaded(net.Kyap.ItemsRarity.compat.cinematic.CinematicCompat.MOD_ID)) {
            net.Kyap.ItemsRarity.compat.cinematic.CinematicCompat.init();
        }
        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenuTypes.register(modEventBus);
        ModLootModifiers.register(modEventBus);
        ItemsRarityCreativeTabs.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);

        LOGGER.info("Items Rarity mod setup complete!");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
