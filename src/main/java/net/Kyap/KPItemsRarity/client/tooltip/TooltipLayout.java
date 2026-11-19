package net.Kyap.KPItemsRarity.client.tooltip;

import net.Kyap.KPItemsRarity.ClientConfig;
import net.Kyap.KPItemsRarity.client.tooltip.TooltipModel.Elem;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Où et à quelle taille dessiner une infobulle RPG : largeur adaptée au contenu, en-tête, corps (qui défile
 * s'il est trop haut pour l'écran), pied de durabilité, et position à côté de la souris.
 */
final class TooltipLayout {
    static final int PAD = 7;
    /** Place gardée à droite pour la barre de défilement. */
    static final int GUTTER = 7;

    final int x, y, width, height;
    final int inner;
    /** Haut de l'aperçu de l'objet, relatif au haut de l'infobulle. */
    final int iconTop;
    final int headerH;
    final int bodyH;
    final int footerH;
    final TooltipScroll.Viewport viewport;
    /** Texte du bas déjà coupé à la largeur. */
    final List<FormattedCharSequence> footerLines;

    TooltipLayout(TooltipContent content, Font font, int icon, int screenW, int screenH, int mouseX, int mouseY) {
        this.width = width(content, font, icon);
        this.inner = width - 2 * PAD;
        this.footerLines = TooltipText.wrap(font, content.footerText, inner - GUTTER);

        // En-tête à la hauteur de son contenu : nom, type, ligne de dégâts/armure s'il y en a une, aperçu
        this.iconTop = PAD + 12;
        int iconBottom = iconTop + icon + 3;
        int textTop = PAD + 11 + 10 + (content.base != null ? content.base.height() + 3 : 0);
        this.headerH = Math.max(textTop - 3, iconBottom) + PAD - 2;

        int body = bodyHeight(content.body, font, wrapWidth());
        if (!footerLines.isEmpty()) body += (body > 0 ? 6 : 0) + footerLines.size() * 10;
        this.bodyH = body;
        this.footerH = content.durability != null ? 13 : 0;

        // Trop haute pour l'écran : seul le corps défile (molette), l'en-tête et la durabilité restent.
        int fixedH = headerH + 6 + 4 + footerH + 4;
        int maxView = Math.max(30, Math.min(ClientConfig.maxBodyHeight(), screenH - 8 - fixedH));
        this.viewport = TooltipScroll.fit(content.stack, bodyH, maxView);
        this.height = headerH + (bodyH > 0 ? 6 + viewport.view() + 4 : 0) + footerH + 4;

        // Comme vanilla : à droite de la souris, retournée si ça dépasse
        int px = mouseX + 12, py = mouseY - 12;
        if (px + width + 4 > screenW) px = Math.max(4, mouseX - 16 - width);
        if (py + height + 4 > screenH) py = screenH - height - 4;
        this.x = px;
        this.y = Math.max(4, py);
    }

    /** Largeur où retourner le texte des passifs. */
    int wrapWidth() {
        return inner - GUTTER;
    }

    int left() {
        return x + PAD;
    }

    int right() {
        return x + width - PAD;
    }

    int bodyTop() {
        return y + headerH + 6;
    }

    /** La zone du corps, avec la place de la barre de défilement si elle est là. */
    TooltipModel.Area bodyArea() {
        return new TooltipModel.Area(left(), right(), inner, viewport.scrolling() ? GUTTER : 0, wrapWidth());
    }

    /**
     * Largeur ADAPTATIVE : au moins la largeur de base, puis on élargit (jusqu'au maximum) tant que
     * l'infobulle est trop haute pour sa largeur. Un long texte donne une infobulle large et basse plutôt
     * qu'une colonne étroite et haute. Un très long nom n'élargit pas : il défile (ScrollingName).
     */
    private static int width(TooltipContent content, Font font, int icon) {
        int baseW = content.base != null ? content.base.width(font) : 0;
        int contentW = Math.max(Math.min(font.width(content.name), ScrollingName.MAX_WIDTH) + 12 + font.width(content.rarityLabel),
                Math.max(font.width(content.subtitle), baseW) + icon + 10);
        for (Elem e : content.body) contentW = Math.max(contentW, e.width(font));
        if (content.durability != null) contentW = Math.max(contentW, font.width(content.durability));

        int maxW = Math.max(ClientConfig.tooltipWidth(), ClientConfig.tooltipMaxWidth());
        int width = Math.min(maxW, Math.max(ClientConfig.tooltipWidth(), contentW + 2 * PAD));
        for (int w = width; w <= maxW; w += 8) {
            width = w;
            int wrap = w - 2 * PAD - GUTTER;
            int lines = TooltipText.wrap(font, content.footerText, wrap).size();
            int estimate = 59 + 10 + bodyHeight(content.body, font, wrap) + (lines > 0 ? 6 + lines * 10 : 0) + (content.durability != null ? 13 : 0);
            if (estimate <= w * 0.8f) break;
        }
        return width;
    }

    private static int bodyHeight(List<Elem> body, Font font, int wrapWidth) {
        int h = 0;
        for (Elem e : body) h += e.height(font, wrapWidth);
        return h;
    }
}
