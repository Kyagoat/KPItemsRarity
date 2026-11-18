package net.Kyap.ItemsRarity.client;

import net.Kyap.ItemsRarity.ItemsRarity;
import net.Kyap.ItemsRarity.item.ModItems;
import net.Kyap.ItemsRarity.item.custom.RuneItem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** Côté client de la forgemagie : la gravure de chaque rune prend la couleur de sa stat. */
@EventBusSubscriber(modid = ItemsRarity.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ForgeClient {
    private ForgeClient() {}

    /** Taille de la rune pour le modèle : 0 = rune, 0.5 = Pa (cerclée d'argent), 1 = Ra (cerclée d'or). */
    @SubscribeEvent
    public static void onClientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        if (net.neoforged.fml.ModList.get().isLoaded("cinematic")) {
            event.enqueueWork(net.Kyap.ItemsRarity.compat.cinematic.CinematicCompat::initClient);
        }
        event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(ModItems.RUNE.get(), ItemsRarity.id("size"),
                (stack, level, entity, seed) -> {
                    var rune = stack.get(net.Kyap.ItemsRarity.component.ModDataComponents.RUNE.get());
                    if (rune == null) return 0f;
                    return rune.power() >= net.Kyap.ItemsRarity.component.RuneData.RA ? 1f
                            : rune.power() >= net.Kyap.ItemsRarity.component.RuneData.PA ? 0.5f : 0f;
                }));
    }

    @SubscribeEvent
    public static void onItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, layer) -> layer == 1 ? RuneItem.tint(stack) : 0xFFFFFFFF, ModItems.RUNE.get());
    }
}
