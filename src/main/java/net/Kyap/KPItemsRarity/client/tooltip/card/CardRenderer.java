package net.Kyap.KPItemsRarity.client.tooltip.card;

import net.Kyap.KPItemsRarity.ClientConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Le moteur commun des fenêtres RPG ({@link Card}) : il les mesure, les place près d'un point (la souris) sans
 * sortir de l'écran, puis dessine le cadre, l'en-tête, le corps qui défile et le pied.
 *
 *   ┌──────────────────────────────┐
 *   │ en-tête (Card.Header)        │  hauteur choisie par l'en-tête
 *   ├──────────────◆───────────────┤
 *   │ corps (Elem...)             ▐│  limité en hauteur : au-delà il défile (molette, CardScroll)
 *   │                             ▐│
 *   │──────────────────────────────│
 *   │          pied fixe           │  durabilité
 *   └──────────────────────────────┘
 */
public final class CardRenderer {
    private CardRenderer() {}

    /** Marge intérieure de la fenêtre. */
    public static final int PAD = 7;
    /** Place gardée à droite du texte pour la barre de défilement. */
    public static final int GUTTER = 7;
    private static final int BODY_GAP = 6, BODY_BOTTOM = 4, FOOTER_H = 13, BOTTOM = 4, WIDEN_STEP = 8;
    /** Une fenêtre s'élargit plutôt que de s'allonger tant qu'elle est plus haute que les 4/5 de sa largeur. */
    private static final float TALL_RATIO = 0.8f;

    /** Où la fenêtre est dessinée, ses hauteurs, et la partie visible de son corps. */
    public record Placement(int x, int y, int width, int height, int headerH, int bodyH, int footerH, CardScroll.Viewport viewport) {
        public int left() {
            return x + PAD;
        }

        public int right() {
            return x + width - PAD;
        }

        public int bodyTop() {
            return y + headerH + BODY_GAP;
        }

        Elem.Area bodyArea() {
            int inner = width - 2 * PAD;
            return new Elem.Area(left(), right(), inner, viewport.scrolling() ? GUTTER : 0, inner - GUTTER);
        }
    }

    /**
     * Place la fenêtre à côté de (anchorX, anchorY) et la dessine à la profondeur {@code z}.
     * @return où elle a été dessinée (pour les survols de l'appelant)
     */
    public static Placement render(GuiGraphics g, Font font, Card card, CardScroll scroll, int anchorX, int anchorY, int z) {
        Placement p = place(card, font, scroll, anchorX, anchorY, g.guiWidth(), g.guiHeight());
        g.pose().pushPose();
        g.pose().translate(0, 0, z);
        RpgFrame.draw(g, p.x(), p.y(), p.width(), p.height(), p.headerH(), card.accent(), card.pulse(), card.ornate());
        card.header().draw(g, font, p.left(), p.right(), p.y());
        g.pose().pushPose();
        g.pose().translate(0, 0, 200); // au-dessus des objets dessinés dans l'en-tête
        if (p.bodyH() > 0) drawBody(g, font, card, p);
        if (card.footer() != null) drawFooter(g, font, card.footer(), card.accent(), p);
        g.pose().popPose();
        g.pose().popPose();
        return p;
    }

    // ---------------------------------------------------------------- Mesure et placement

    static Placement place(Card card, Font font, CardScroll scroll, int anchorX, int anchorY, int screenW, int screenH) {
        int headerH = card.header().height();
        int footerH = card.footer() != null ? FOOTER_H : 0;
        int width = width(card, font);
        int bodyH = bodyHeight(card.body(), font, wrapWidth(width));

        // Trop haute pour l'écran : seul le corps défile, l'en-tête et le pied restent.
        int fixedH = headerH + BODY_GAP + BODY_BOTTOM + footerH + BOTTOM;
        int maxView = Math.max(30, Math.min(ClientConfig.maxBodyHeight(), screenH - 8 - fixedH));
        CardScroll.Viewport viewport = scroll.fit(bodyH, maxView);
        int height = headerH + (bodyH > 0 ? BODY_GAP + viewport.view() + BODY_BOTTOM : 0) + footerH + BOTTOM;

        // Comme vanilla : à droite du point et un peu au-dessus, de l'autre côté si ça dépasse, toujours dans l'écran.
        int x = anchorX + 12, y = anchorY - 12;
        if (x + width + 4 > screenW) x = Math.max(4, anchorX - 16 - width);
        if (y + height + 4 > screenH) y = screenH - height - 4;
        return new Placement(x, Math.max(4, y), width, height, headerH, bodyH, footerH, viewport);
    }

    /**
     * Largeur : celle que le contenu demande (entre le minimum et le maximum de la config), puis élargie tant que
     * ça raccourcit le texte qui se retourne et que la fenêtre reste plus haute que large. Un long texte donne une
     * fenêtre large et basse plutôt qu'une colonne étroite ; des lignes courtes ne l'élargissent pas pour rien.
     */
    private static int width(Card card, Font font) {
        int contentW = card.header().width(font);
        for (Elem e : card.body()) contentW = Math.max(contentW, e.width(font));
        if (card.footer() != null) contentW = Math.max(contentW, font.width(card.footer()));
        int minW = ClientConfig.tooltipWidth(), maxW = Math.max(minW, ClientConfig.tooltipMaxWidth());
        int width = Math.min(maxW, Math.max(minW, contentW + 2 * PAD));

        int fixedH = card.header().height() + BODY_GAP + BODY_BOTTOM + (card.footer() != null ? FOOTER_H : 0) + BOTTOM;
        int shortest = bodyHeight(card.body(), font, wrapWidth(maxW));
        for (int w = width; w <= maxW; w += WIDEN_STEP) {
            width = w;
            int bodyH = bodyHeight(card.body(), font, wrapWidth(w));
            if (bodyH <= shortest || fixedH + bodyH <= w * TALL_RATIO) break;
        }
        return width;
    }

    private static int wrapWidth(int width) {
        return width - 2 * PAD - GUTTER;
    }

    private static int bodyHeight(List<Elem> body, Font font, int wrapWidth) {
        int h = 0;
        for (Elem e : body) h += e.height(font, wrapWidth);
        return h;
    }

    // ---------------------------------------------------------------- Dessin

    /** Le corps, découpé à sa partie visible quand il défile, avec la barre et les flèches. */
    private static void drawBody(GuiGraphics g, Font font, Card card, Placement p) {
        CardScroll.Viewport viewport = p.viewport();
        int top = p.bodyTop();
        if (viewport.scrolling()) g.enableScissor(p.x() + 1, top - 1, p.x() + p.width() - 1, top + viewport.view() + 1);
        Elem.Area area = p.bodyArea();
        int y = top - viewport.offset();
        for (Elem e : card.body()) {
            e.draw(g, font, area, y);
            y += e.height(font, area.wrapWidth());
        }
        if (viewport.scrolling()) {
            g.disableScissor();
            RpgFrame.scrollChrome(g, font, p.x(), p.width(), p.right(), top, viewport.view(), p.bodyH(),
                    viewport.offset(), viewport.max(), card.accent());
        }
    }

    /** Le pied : un trait à la couleur du cadre, le texte centré dessous. */
    private static void drawFooter(GuiGraphics g, Font font, Component footer, int accent, Placement p) {
        int y = p.y() + p.height() - p.footerH() - 2;
        g.fill(p.left(), y, p.right(), y + 1, RpgFrame.argb(accent, 0x40));
        g.drawString(font, footer, p.x() + (p.width() - font.width(footer)) / 2, y + 3, 0xFFFFFF, true);
    }
}
