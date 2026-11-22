package net.Kyap.KPItemsRarity.client.tooltip.item;

import net.Kyap.KPItemsRarity.KPItemsRarity;
import net.Kyap.KPItemsRarity.client.ShareKey;
import net.Kyap.KPItemsRarity.client.tooltip.card.Card;
import net.Kyap.KPItemsRarity.client.tooltip.card.CardRenderer;
import net.Kyap.KPItemsRarity.client.tooltip.card.CardScroll;
import net.Kyap.KPItemsRarity.client.tooltip.setcard.SetCard;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.List;

/**
 * L'infobulle RPG, pour TOUS les objets (c'est le principe du mod : il n'y a pas d'autre infobulle d'objet).
 * GuiGraphicsTooltipMixin l'appelle à la place du dessin vanilla ; elle construit le contenu ({@link ItemCard})
 * et le confie au moteur commun ({@link CardRenderer}). Alt maintenu sur une pièce de panoplie : la carte de
 * panoplie la remplace.
 */
@EventBusSubscriber(modid = KPItemsRarity.MOD_ID, value = Dist.CLIENT)
public final class RpgTooltip {
    private RpgTooltip() {}

    private static final int Z = 400;
    /** La molette n'est retenue que si une infobulle qui défile vient d'être dessinée. */
    private static final long WHEEL_WINDOW_MS = 150;

    /** Le défilement suit l'objet survolé : il repart du haut quand on en survole un autre. */
    private static final CardScroll SCROLL = new CardScroll();
    private static ItemStack scrolled = ItemStack.EMPTY;
    private static long lastScrollingRender;
    private static boolean warned;

    /**
     * Dessine l'infobulle de l'objet.
     * @param lines  lignes de l'infobulle vanilla et des autres mods
     * @param images composants non textuels (cases d'appât de Tide, cartes...)
     */
    public static void render(GuiGraphics g, Font font, List<Component> lines, List<ClientTooltipComponent> images,
                              ItemStack stack, int mouseX, int mouseY) {
        if (ShareKey.isSetDetailsDown() && SetCard.open(stack, mouseX, mouseY)) return;
        try {
            Card card = ItemCard.of(stack, lines, images, font);
            follow(stack);
            CardRenderer.Placement placement = CardRenderer.render(g, font, card, SCROLL, mouseX, mouseY, Z);
            if (placement.viewport().scrolling()) lastScrollingRender = Util.getMillis();
        } catch (RuntimeException e) {
            // Pas de repli sur une infobulle texte : le bug est signalé une fois dans le journal, rien n'est affiché.
            if (!warned) {
                warned = true;
                KPItemsRarity.LOGGER.error("Infobulle RPG impossible pour {}", stack, e);
            }
        }
    }

    private static void follow(ItemStack stack) {
        if (ItemStack.isSameItemSameComponents(stack, scrolled)) return;
        scrolled = stack.copy();
        SCROLL.reset();
    }

    /** Molette sur un objet dont l'infobulle défile : c'est le corps de l'infobulle qui défile, pas l'écran. */
    @SubscribeEvent
    public static void onScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (Util.getMillis() - lastScrollingRender > WHEEL_WINDOW_MS) return;
        if (SCROLL.wheel(event.getScrollDeltaY())) event.setCanceled(true);
    }
}
