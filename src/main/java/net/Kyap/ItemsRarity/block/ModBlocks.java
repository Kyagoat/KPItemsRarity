package net.Kyap.ItemsRarity.block;

import net.Kyap.ItemsRarity.ItemsRarity;
import net.Kyap.ItemsRarity.block.custom.EnhancedAnvilBlock;
import net.Kyap.ItemsRarity.item.ModItems;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ItemsRarity.MOD_ID);

    public static final DeferredBlock<Block> ENHANCED_ANVIL_BLOCK = registerBlock("enhanced_anvil_block",
            () -> new EnhancedAnvilBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.ANVIL).noOcclusion()));

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> block) {
        DeferredBlock<T> registered = BLOCKS.register(name, block);
        ModItems.ITEMS.registerSimpleBlockItem(registered);
        return registered;
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
