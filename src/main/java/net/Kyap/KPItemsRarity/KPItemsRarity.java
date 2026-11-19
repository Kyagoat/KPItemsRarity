package net.Kyap.KPItemsRarity;

import net.Kyap.KPItemsRarity.compat.CuriosCompat;
import net.Kyap.KPItemsRarity.component.ModDataComponents;
import net.Kyap.KPItemsRarity.loot.ModLootModifiers;
import net.Kyap.KPItemsRarity.registry.ModAttributes;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(KPItemsRarity.MOD_ID)
public class KPItemsRarity {
    public static final String MOD_ID = "kpitemsrarity";
    public static final Logger LOGGER = LogUtils.getLogger();

    public KPItemsRarity(IEventBus modEventBus, ModContainer modContainer) {
        ModDataComponents.register(modEventBus);
        ModAttributes.register(modEventBus);
        CuriosCompat.init();
        ModLootModifiers.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);

        LOGGER.info("KP Items Rarity : prêt");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
