package net.Kyap.ItemsRarity.block.entity.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.Kyap.ItemsRarity.block.entity.custom.EnhancedAnvilBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Affiche la ressource, l'équipement et le matériau posés sur l'enclume. */
public class EnhancedBlockEntityRenderer implements BlockEntityRenderer<EnhancedAnvilBlockEntity> {

    public EnhancedBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(EnhancedAnvilBlockEntity anvil, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        var handler = anvil.getItemHandler();

        renderItem(handler.getStackInSlot(EnhancedAnvilBlockEntity.RESOURCE_SLOT), anvil, poseStack, buffer, packedLight, packedOverlay,
                itemRenderer, 0.35f, 1.08f, 0.55f, 1f);
        renderItem(handler.getStackInSlot(EnhancedAnvilBlockEntity.GEAR_SLOT), anvil, poseStack, buffer, packedLight, packedOverlay,
                itemRenderer, 0.5f, 1.027f, 0.2f, 2f);
        renderItem(handler.getStackInSlot(EnhancedAnvilBlockEntity.MOD_MATERIAL_SLOT), anvil, poseStack, buffer, packedLight, packedOverlay,
                itemRenderer, 0.76f, 1.08f, 0.3f, 1f);
    }

    /** Objet posé à plat sur l'enclume. */
    private void renderItem(ItemStack stack, EnhancedAnvilBlockEntity anvil, PoseStack poseStack, MultiBufferSource buffer,
                            int packedLight, int packedOverlay, ItemRenderer itemRenderer,
                            float x, float y, float z, float scale) {
        if (stack.isEmpty()) return;
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));
        poseStack.scale(scale, scale, scale);
        itemRenderer.renderStatic(stack, ItemDisplayContext.GROUND, packedLight, packedOverlay, poseStack, buffer, anvil.getLevel(), 0);
        poseStack.popPose();
    }
}
