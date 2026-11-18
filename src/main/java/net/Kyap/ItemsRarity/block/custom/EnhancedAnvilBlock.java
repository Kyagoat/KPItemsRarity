package net.Kyap.ItemsRarity.block.custom;

import com.mojang.serialization.MapCodec;
import net.Kyap.ItemsRarity.block.custom.particle.ParticleAnvilBlock;
import net.Kyap.ItemsRarity.block.entity.custom.EnhancedAnvilBlockEntity;
import net.Kyap.ItemsRarity.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class EnhancedAnvilBlock extends BaseEntityBlock {
    public static final MapCodec<EnhancedAnvilBlock> CODEC = simpleCodec(EnhancedAnvilBlock::new);
    public static final VoxelShape SHAPE = makeShape();

    public EnhancedAnvilBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    private static VoxelShape makeShape() {
        VoxelShape shape = Block.box(2, 10, 2, 14, 16, 14);
        shape = Shapes.or(shape, Block.box(5, 4, 5, 11, 10, 11));
        shape = Shapes.or(shape, Block.box(2, 0, 2, 14, 4, 14));
        shape = Shapes.or(shape, Block.box(5, 11, 0, 11, 15, 2));
        shape = Shapes.or(shape, Block.box(0, 11, 5, 2, 15, 11));
        shape = Shapes.or(shape, Block.box(14, 11, 5, 16, 15, 11));
        shape = Shapes.or(shape, Block.box(5, 11, 14, 11, 15, 16));
        return shape;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnhancedAnvilBlockEntity(pos, state);
    }

    /** Clic droit avec le marteau d'émeraude : forger (comme le bouton "Forger" de l'écran). */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!held.is(ModItems.EMERALD_HAMMER.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION; // -> ouvre l'interface
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof EnhancedAnvilBlockEntity anvil)
                || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return ItemInteractionResult.FAIL;
        }
        var outcome = anvil.forge(serverPlayer);
        if (outcome != net.Kyap.ItemsRarity.forge.ForgeMagic.Outcome.INVALID) held.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        return ItemInteractionResult.CONSUME;
    }

    /** Clic droit sans objet (ou avec autre chose) : ouvrir l'interface. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof EnhancedAnvilBlockEntity anvil) {
            player.openMenu(anvil, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    public static void playUpgradeAnimation(Level level, BlockPos pos, EnhancedAnvilBlockEntity anvil, boolean success) {
        if (success) {
            ParticleAnvilBlock.spawnRarityParticles(level, pos, anvil);
            level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.2f, 1.0f);
            level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.8f, 1.3f);
            level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.6f, 1.5f);
        } else {
            ParticleAnvilBlock.spawnFailParticles(level, pos);
            level.playSound(null, pos, SoundEvents.ANVIL_DESTROY, SoundSource.BLOCKS, 0.5f, 0.8f);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof EnhancedAnvilBlockEntity anvil) {
            for (int i = 0; i < anvil.getItemHandler().getSlots(); i++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), anvil.getItemHandler().getStackInSlot(i));
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
