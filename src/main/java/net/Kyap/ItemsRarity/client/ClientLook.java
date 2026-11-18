package net.Kyap.ItemsRarity.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Petit utilitaire client : le bloc (à entité) que le joueur regarde. */
public final class ClientLook {
    private ClientLook() {}

    @Nullable
    public static BlockEntity lookedAtBlockEntity() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.hitResult instanceof BlockHitResult hit)) return null;
        return mc.level.getBlockEntity(hit.getBlockPos());
    }
}
