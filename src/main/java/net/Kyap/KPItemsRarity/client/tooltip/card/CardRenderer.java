package net.Kyap.KPItemsRarity.client.tooltip.card;

import net.Kyap.KPItemsRarity.ClientConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Le moteur commun des fenêtres RPG ({@link Card}) : il les mesure (une fois, voir {@link LiveCard}), les place près
 * d'un point (la souris) sans sortir de l'écran, puis dessine le cadre, l'en-tête, le corps qui défile et le pied.
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
    /** Distance minimale au bord de l'écran ; hauteur visible minimale d'un corps qui défile. */
    private static final int SCREEN_MARGIN = 4, MIN_VIEW = 30;
    /** Une fenêtre s'élargit plutôt que de s'allonger tant qu'elle est plus haute que les 4/5 de sa largeur. */
    private static final float TALL_RATIO = 0.8f;

    /** Les dimensions d'une carte : sa largeur et la hauteur de chaque partie (le corps en entier, sans défilement). */
    public record Size(int width, int headerH, int bodyH, int footerH) {}

    /** Où la fenêtre est dessinée à cette image, sa hauteur, et la partie visible de son corps. */
    public record Placement(int x, int y, Size size, int height, CardScroll.Viewport viewport) {
        public int width() {
            return size.width();
        }

        public int left() {
            return x + PAD;
        }

        public int right() {
            return x + size.width() - PAD;
        }

        public int bodyTop() {
            return y + size.headerH() + BODY_GAP;
        }

        Elem.Area bodyArea() {
            int inner = size.width() - 2 * PAD;
            return new Elem.Area(left(), right(), inner, viewport.scrolling() ? GUTTER : 0, inner - GUTTER);
        }
    }

    /** Mesure la carte : à faire une fois, pas à chaque image (le texte y est retourné à la ligne plusieurs fois). */
    public static Size measure(Card card, Font font) {
        int width = width(card, font);
        return new Size(width, card.header().height(), bodyHeight(card.body(), font, wrapWidth(width)), card.footer() != null ? FOOTER_H : 0);
    }

    /**
     * Place la carte à côté de (anchorX, anchorY) et la dessine à la profondeur {@code z}.
     * @return où elle a été dessinée (pour les survols de l'appelant)
     */
    public static Placement render(GuiGraphics g, Font font, Card card, Size size, CardScroll scroll, int anchorX, int anchorY, int z) {
        Placement p = place(size, scroll, anchorX, anchorY, g.guiWidth(), g.guiHeight());
        g.pose().pushPose();
        g.pose().translate(0, 0, z);
        RpgFrame.draw(g, p.x(), p.y(), size.width(), p.height(), size.headerH(), card.style());
        card.header().draw(g, font, p.left(), p.right(), p.y());
        g.pose().pushPose();
        g.pose().translate(0, 0, 200); // au-dessus des objets dessinés dans l'en-tête
        if (size.bodyH() > 0) drawBody(g, font, card, p);
        if (card.footer() != null) drawFooter(g, font, card.footer(), card.style().accent(), p);
        g.pose().popPose();
        g.pose().popPose();
        return p;
    }

    // ---------------------------------------------------------------- Placement et mesure

    /** Trop haute pour l'écran : seul le corps défile, l'en-tête et le pied restent. Placée comme l'infobulle vanilla. */
    private static Placement place(Size size, CardScroll scroll, int anchorX, int anchorY, int screenW, int screenH) {
        int fixedH = size.headerH() + BODY_GAP + BODY_BOTTOM + size.footerH() + BOTTOM;
        int maxView = Math.max(MIN_VIEW, Math.min(ClientConfig.maxBodyHeight(), screenH - 2 * SCREEN_MARGIN - fixedH));
        CardScroll.Viewport viewport = scroll.fit(size.bodyH(), maxView);
        int height = size.headerH() + (size.bodyH() > 0 ? BODY_GAP + viewport.view() + BODY_BOTTOM : 0) + size.footerH() + BOTTOM;

        // À droite du point et un peu au-dessus ; de l'autre côté si ça dépasse ; toujours dans l'écran.
        int x = anchorX + 12, y = anchorY - 12;
        if (x + size.width() + SCREEN_MARGIN > screenW) x = Math.max(SCREEN_MARGIN, anchorX - 16 - size.width());
        if (y + height + SCREEN_MARGIN > screenH) y = screenH - height - SCREEN_MARGIN;
        return new Placement(x, Math.max(SCREEN_MARGIN, y), size, height, viewport);
    }

    /**
     * Largeur : celle que le contenu demande (entre le minimum et le maximum de la config), puis élargie tant que
     * ça raccourcit le texte qui se retourne et que la fenêtre reste plus haute que large. Un long texte donne une
     * fenêtre large et basse plutôt qu'une colonne étroite ; des lignes courtes ne l'élargissent pas pour rien.
     */
    private static int width(Card card, Font font) {
        int bodyW = 0;
        for (Elem e : card.body()) bodyW = Math.max(bodyW, e.width(font));
        // Le corps garde la place de la barre de défilement : qu'il défile ou non (ça dépend de l'écran), rien n'est coupé.
        int contentW = Math.max(card.header().width(font), bodyW > 0 ? bodyW + GUTTER : 0);
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
        int viewBottom = top + viewport.view();
        for (Elem e : card.body()) {
            int h = e.height(font, area.wrapWidth());
            if (y + h > top && y < viewBottom) e.draw(g, font, area, y); // hors de la partie visible : rien à dessiner
            y += h;
        }
        if (viewport.scrolling()) {
            g.disableScissor();
            RpgFrame.scrollChrome(g, font, p.x(), p.width(), p.right(), top, viewport.view(), p.size().bodyH(),
                    viewport.offset(), viewport.max(), card.style().accent());
        }
    }

    /** Le pied : un trait à la couleur du cadre, le texte centré dessous. */
    private static void drawFooter(GuiGraphics g, Font font, Component footer, int accent, Placement p) {
        int y = p.y() + p.height() - p.size().footerH() - 2;
        g.fill(p.left(), y, p.right(), y + 1, Palette.argb(accent, 0x40));
        g.drawString(font, footer, p.x() + (p.width() - font.width(footer)) / 2, y + 3, 0xFFFFFF, true);
    }
}
