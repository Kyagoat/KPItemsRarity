package net.Kyap.ItemsRarity.block.entity;

import net.Kyap.ItemsRarity.ItemsRarity;
import net.Kyap.ItemsRarity.block.ModBlocks;
import net.Kyap.ItemsRarity.block.entity.custom.EnhancedAnvilBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@EventBusSubscriber(modid = ItemsRarity.MOD_ID)
public final class ModBlockEntities {
    private ModBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ItemsRarity.MOD_ID);

    @SuppressWarnings("DataFlowIssue") // build(null) est la norme pour les block entities sans DataFixer
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EnhancedAnvilBlockEntity>> ENHANCED_ANVIL_BE =
            BLOCK_ENTITIES.register("enhanced_anvil_be", () ->
                    BlockEntityType.Builder.of(EnhancedAnvilBlockEntity::new, ModBlocks.ENHANCED_ANVIL_BLOCK.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }

    /** Remplace getCapability/LazyOptional : expose l'inventaire aux hoppers, tuyaux Create, etc. */
    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ENHANCED_ANVIL_BE.get(),
                (anvil, side) -> anvil.getItemHandler());
    }
}
