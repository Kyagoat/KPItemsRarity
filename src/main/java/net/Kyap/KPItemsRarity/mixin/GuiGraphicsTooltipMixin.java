package net.Kyap.KPItemsRarity.mixin;

import net.Kyap.KPItemsRarity.client.tooltip.RpgTooltip;
import net.Kyap.KPItemsRarity.client.tooltip.SetCard;
import net.Kyap.KPItemsRarity.client.tooltip.TooltipText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * UN SEUL point d'entrée pour toutes les infobulles du jeu : le rendu interne, juste avant le dessin.
 * Inventaire, créatif, chat, livres, Curios, écrans des autres mods : tout passe ici.
 * L'objet survolé vient de "tooltipStack" (rempli par vanilla/NeoForge pendant le dessin d'une infobulle
 * d'objet) ; s'il manque (écran qui dessine l'infobulle sans donner l'objet, comme Curios), on le
 * retrouve dans la case survolée. Les lignes arrivent prêtes à dessiner : on garde le texte et les
 * images (cases d'appât de Tide...), l'infobulle RPG les remet à nos couleurs.
 */
@Mixin(value = GuiGraphics.class, priority = 500)
public abstract class GuiGraphicsTooltipMixin {

    @Shadow private ItemStack tooltipStack;

    @Inject(method = "renderTooltipInternal", at = @At("HEAD"), cancellable = true)
    private void kpitemsrarity$rpgTooltip(Font font, List<ClientTooltipComponent> components, int mouseX, int mouseY,
                                        ClientTooltipPositioner positioner, CallbackInfo ci) {
        // Carte de panoplie figée (Alt) : aucune autre infobulle, même sans objet (panneau de stats...)
        if (SetCard.isOpen()) {
            ci.cancel();
            return;
        }
        if (components.isEmpty()) return;
        List<Component> lines = new ArrayList<>();
        List<ClientTooltipComponent> images = new ArrayList<>();
        for (ClientTooltipComponent c : components) {
            if (c instanceof ClientTextTooltip text) lines.add(TooltipText.toComponent(((ClientTextTooltipAccessor) text).kpitemsrarity$getText()));
            else images.add(c);
        }
        ItemStack stack = tooltipStack;
        if (stack == null || stack.isEmpty()) stack = hoveredSlotItem(lines);
        if (stack.isEmpty()) return;
        if (RpgTooltip.handle((GuiGraphics) (Object) this, font, lines, images, stack, mouseX, mouseY)) {
            ci.cancel();
        }
    }

    /** Objet de la case survolée, seulement si l'infobulle commence bien par son nom. */
    private static ItemStack hoveredSlotItem(List<Component> lines) {
        if (lines.isEmpty() || !(Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen)) return ItemStack.EMPTY;
        var slot = screen.getSlotUnderMouse();
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        return lines.get(0).getString().equals(stack.getHoverName().getString()) ? stack : ItemStack.EMPTY;
    }
}
