package net.Kyap.KPItemsRarity.mixin;

import net.Kyap.KPItemsRarity.client.tooltip.item.RpgTooltip;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * UN SEUL point d'entrée pour toutes les infobulles du jeu : le dessin interne, juste avant qu'il commence.
 * Inventaire, créatif, chat, livres, Curios, écrans des autres mods : tout passe ici, et l'infobulle RPG décide
 * (voir RpgTooltip.replace). "tooltipStack" est l'objet de l'infobulle, rempli par vanilla/NeoForge.
 */
@Mixin(value = GuiGraphics.class, priority = 500)
public abstract class GuiGraphicsTooltipMixin {

    @Shadow private ItemStack tooltipStack;

    @Inject(method = "renderTooltipInternal", at = @At("HEAD"), cancellable = true)
    private void kpitemsrarity$rpgTooltip(Font font, List<ClientTooltipComponent> lines, int mouseX, int mouseY,
                                        ClientTooltipPositioner positioner, CallbackInfo ci) {
        if (RpgTooltip.replace((GuiGraphics) (Object) this, font, lines, tooltipStack, mouseX, mouseY)) ci.cancel();
    }
}
