package net.Kyap.KPItemsRarity.client.tooltip;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Nom d'objet trop long pour la place : "…" d'abord, puis après un survol d'une seconde il défile
 * d'un bout à l'autre (aller-retour, avec une pause à chaque extrémité).
 */
final class ScrollingName {
    private ScrollingName() {}

    /** Largeur maximale que le nom peut imposer à l'infobulle. */
    static final int MAX_WIDTH = 110;
    private static final long WAIT_MS = 1000, PAUSE_MS = 900;
    private static final float SPEED = 0.03f; // pixels par milliseconde

    private static ItemStack stack = ItemStack.EMPTY;
    private static long since;

    static void draw(GuiGraphics g, Font font, Component name, ItemStack hovered, int x, int y, int maxW) {
        int full = font.width(name);
        long now = Util.getMillis();
        if (!ItemStack.isSameItemSameComponents(hovered, stack)) {
            stack = hovered.copy();
            since = now;
        }
        long elapsed = now - since - WAIT_MS;
        if (full <= maxW || elapsed < 0) {
            g.drawString(font, RpgFrame.fit(font, name, maxW), x, y, 0xFFFFFF, true);
            return;
        }
        float shift = shift(full - maxW, elapsed);
        g.enableScissor(x, y - 1, x + maxW, y + 10);
        g.drawString(font, name, x - Math.round(shift), y, 0xFFFFFF, true);
        g.disableScissor();
    }

    /** Décalage du texte : aller, pause au bout, retour, pause au début. */
    private static float shift(int overflow, long elapsed) {
        long travel = (long) (overflow / SPEED);
        long t = elapsed % (2 * (travel + PAUSE_MS));
        if (t < travel) return t * SPEED;
        if (t < travel + PAUSE_MS) return overflow;
        if (t < 2 * travel + PAUSE_MS) return overflow - (t - travel - PAUSE_MS) * SPEED;
        return 0;
    }
}
