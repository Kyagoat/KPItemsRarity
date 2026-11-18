package net.Kyap.ItemsRarity.block.custom.particle;

import net.Kyap.ItemsRarity.block.entity.custom.EnhancedAnvilBlockEntity;
import net.Kyap.ItemsRarity.util.ModRarities.ModRarity;
import net.Kyap.ItemsRarity.util.UpgradeHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

public final class ParticleAnvilBlock {
    private ParticleAnvilBlock() {}

    /** Éclats colorés selon la rareté obtenue : plus elle est haute, plus il y en a. */
    public static void spawnRarityParticles(Level level, BlockPos pos, EnhancedAnvilBlockEntity anvil) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        ItemStack gearItem = anvil.getItemHandler().getStackInSlot(EnhancedAnvilBlockEntity.GEAR_SLOT);
        if (gearItem.isEmpty()) return;

        ModRarity rarity = UpgradeHelper.getRarityOrCommon(gearItem);
        int multiplier = rarity.getParticleMultiplier();
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(rarity.red(), rarity.green(), rarity.blue()), 0.5f);

        double cx = pos.getX() + 0.5, cy = pos.getY() + 1.0, cz = pos.getZ() + 0.5;
        RandomSource random = level.getRandom();

        for (int i = 0; i < 15 * multiplier; i++) {
            serverLevel.sendParticles(dust,
                    cx + (random.nextDouble() - 0.5) * 0.3, cy + random.nextDouble() * 0.1, cz + (random.nextDouble() - 0.5) * 0.3,
                    1,
                    (random.nextDouble() - 0.5) * 0.4, random.nextDouble() * 0.3 + 0.1, (random.nextDouble() - 0.5) * 0.4,
                    0.2);
        }
        for (int i = 0; i < 6 * multiplier; i++) {
            serverLevel.sendParticles(ParticleTypes.CRIT,
                    cx + (random.nextDouble() - 0.5) * 0.6, cy + random.nextDouble() * 0.2, cz + (random.nextDouble() - 0.5) * 0.6,
                    1,
                    (random.nextDouble() - 0.5) * 0.3, random.nextDouble() * 0.2 + 0.1, (random.nextDouble() - 0.5) * 0.3,
                    0.15);
        }
    }

    /** Petite fumée en cas d'échec, pour que le joueur comprenne tout de suite. */
    public static void spawnFailParticles(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 8, 0.2, 0.05, 0.2, 0.01);
    }
}
