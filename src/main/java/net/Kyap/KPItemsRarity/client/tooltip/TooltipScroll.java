package net.Kyap.KPItemsRarity.client.tooltip;

import net.Kyap.KPItemsRarity.KPItemsRarity;
import net.minecraft.Util;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Défilement des infobulles trop hautes pour l'écran : la molette sur l'objet survolé fait défiler le corps
 * (l'en-tête et la durabilité restent). Le décalage est gardé tant qu'on survole le même objet.
 */
@EventBusSubscriber(modid = KPItemsRarity.MOD_ID, value = Dist.CLIENT)
public final class TooltipScroll {
    private TooltipScroll() {}

    /** Partie visible du corps : hauteur affichée, décalage et décalage maximum. */
    record Viewport(int view, int offset, int max) {
        boolean scrolling() {
            return max > 0;
        }
    }

    private static ItemStack scrollStack = ItemStack.EMPTY;
    private static int scrollOffset, scrollMax;
    private static long lastTallRender;

    /** La partie visible d'un corps de bodyH pixels quand on n'a que maxView pixels. */
    static Viewport fit(ItemStack stack, int bodyH, int maxView) {
        if (bodyH <= maxView) {
            if (ItemStack.isSameItemSameComponents(stack, scrollStack)) scrollMax = 0;
            return new Viewport(bodyH, 0, 0);
        }
        if (!ItemStack.isSameItemSameComponents(stack, scrollStack)) {
            scrollStack = stack.copy();
            scrollOffset = 0;
        }
        scrollMax = Math.max(0, bodyH - maxView);
        scrollOffset = Math.min(scrollOffset, scrollMax);
        lastTallRender = Util.getMillis();
        return new Viewport(maxView, scrollOffset, scrollMax);
    }

    @SubscribeEvent
    public static void onScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (scrollMax <= 0 || Util.getMillis() - lastTallRender > 150) return;
        int next = Math.max(0, Math.min(scrollMax, scrollOffset - (int) Math.signum(event.getScrollDeltaY()) * 22));
        // Arrivé en bas (ou en haut), la molette n'est plus retenue : elle repart à l'écran (liste du créatif...).
        if (next == scrollOffset) return;
        scrollOffset = next;
        event.setCanceled(true);
    }
}
