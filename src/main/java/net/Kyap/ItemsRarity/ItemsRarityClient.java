package net.Kyap.ItemsRarity;

import net.Kyap.ItemsRarity.block.entity.ModBlockEntities;
import net.Kyap.ItemsRarity.block.entity.renderer.EnhancedBlockEntityRenderer;
import net.Kyap.ItemsRarity.screen.EnhancedAnvilBlockScreen;
import net.Kyap.ItemsRarity.screen.ModMenuTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = ItemsRarity.MOD_ID, value = Dist.CLIENT)
public final class ItemsRarityClient {
    private ItemsRarityClient() {}

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.ENHANCED_ANVIL_MENU.get(), EnhancedAnvilBlockScreen::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.ENHANCED_ANVIL_BE.get(), EnhancedBlockEntityRenderer::new);
    }
}
