package net.Kyap.KPItemsRarity.client.tooltip.item;

import net.Kyap.KPItemsRarity.KPItemsRarity;
import net.Kyap.KPItemsRarity.client.ShareKey;
import net.Kyap.KPItemsRarity.client.tooltip.card.CardRenderer;
import net.Kyap.KPItemsRarity.client.tooltip.card.LiveCard;
import net.Kyap.KPItemsRarity.client.tooltip.setcard.SetCard;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * L'infobulle RPG, pour TOUS les objets : c'est le principe du mod, il n'y a pas d'autre infobulle d'objet.
 * GuiGraphicsTooltipMixin la demande à la place de chaque infobulle du jeu ; elle construit le contenu
 * ({@link ItemCard}) et le confie au moteur commun des fenêtres, qui le garde d'une image à l'autre ({@link LiveCard}).
 * Alt maintenu sur une pièce de panoplie : la carte de panoplie la remplace.
 */
@EventBusSubscriber(modid = KPItemsRarity.MOD_ID, value = Dist.CLIENT)
public final class RpgTooltip {
    private RpgTooltip() {}

    private static final int Z = 400;
    /** La molette n'est retenue que si une infobulle qui défile vient d'être dessinée. */
    private static final long WHEEL_WINDOW_MS = 150;

    private static final LiveCard SHOWN = new LiveCard();
    private static long lastScrollingRender;
    private static boolean warned;

    /** Ce que montre l'infobulle : l'objet (comparé par son contenu, pas par sa case) et Maj, qui ajoute les détails. */
    private record Hovered(ItemStack stack, boolean shift) {
        @Override
        public boolean equals(Object other) {
            return other instanceof Hovered hovered && shift == hovered.shift && ItemStack.isSameItemSameComponents(stack, hovered.stack);
        }

        @Override
        public int hashCode() {
            return 31 * ItemStack.hashItemAndComponents(stack) + (shift ? 1 : 0);
        }
    }

    /**
     * Remplace une infobulle du jeu, s'il le faut.
     * @param lines   les lignes de l'infobulle vanilla (et des autres mods), déjà prêtes à dessiner
     * @param hovered l'objet de l'infobulle selon le jeu ; vide pour une infobulle sans objet, ou d'un écran qui ne
     *                le donne pas (Curios : on le cherche alors dans la case survolée)
     * @return vrai si l'infobulle vanilla ne doit pas être dessinée
     */
    public static boolean replace(GuiGraphics g, Font font, List<ClientTooltipComponent> lines, @Nullable ItemStack hovered,
                                  int mouseX, int mouseY) {
        if (SetCard.isOpen()) return true; // carte de panoplie figée : aucune autre infobulle, même sans objet
        if (lines.isEmpty()) return false;
        ItemStack stack = hovered != null && !hovered.isEmpty() ? hovered : ForeignLines.hoveredSlotItem(lines);
        if (stack.isEmpty()) return false; // infobulle d'un bouton, d'un panneau... : celle du jeu
        if (ShareKey.isSetDetailsDown() && SetCard.open(stack, mouseX, mouseY)) return true;
        draw(g, font, lines, stack, mouseX, mouseY);
        return true;
    }

    private static void draw(GuiGraphics g, Font font, List<ClientTooltipComponent> lines, ItemStack stack, int mouseX, int mouseY) {
        try {
            Hovered key = new Hovered(stack.copy(), Screen.hasShiftDown());
            CardRenderer.Placement shown = SHOWN.render(g, font, key, () -> ItemCard.of(stack, lines, font, SHOWN.shownSince()), mouseX, mouseY, Z);
            if (shown.viewport().scrolling()) lastScrollingRender = Util.getMillis();
        } catch (RuntimeException e) {
            // Pas de repli sur une infobulle texte : le bug est signalé une fois dans le journal, rien n'est affiché.
            if (!warned) {
                warned = true;
                KPItemsRarity.LOGGER.error("Infobulle RPG impossible pour {}", stack, e);
            }
        }
    }

    /** Molette sur un objet dont l'infobulle défile : c'est le corps de l'infobulle qui défile, pas l'écran. */
    @SubscribeEvent
    public static void onScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (Util.getMillis() - lastScrollingRender > WHEEL_WINDOW_MS) return;
        if (SHOWN.wheel(event.getScrollDeltaY())) event.setCanceled(true);
    }
}
