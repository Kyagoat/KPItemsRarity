package net.Kyap.KPItemsRarity.client.tooltip.card;

/**
 * Le défilement du corps d'une fenêtre trop haute : le décalage est gardé d'une image à l'autre et la molette
 * le fait avancer par pas. Chaque fenêtre a le sien (l'infobulle le remet à zéro quand on change d'objet).
 */
public final class CardScroll {
    private static final int STEP = 22;

    private int offset, max;

    /** Partie visible du corps : hauteur affichée, décalage, et décalage maximum (0 : pas de défilement). */
    public record Viewport(int view, int offset, int max) {
        public boolean scrolling() {
            return max > 0;
        }
    }

    /** La partie visible d'un corps de {@code bodyH} pixels quand on n'a que {@code maxView} pixels. */
    public Viewport fit(int bodyH, int maxView) {
        if (bodyH <= maxView) {
            max = 0;
            return new Viewport(bodyH, 0, 0);
        }
        max = bodyH - maxView;
        offset = Math.min(offset, max);
        return new Viewport(maxView, offset, max);
    }

    public void reset() {
        offset = 0;
    }

    /**
     * Un cran de molette. @return vrai si le corps a bougé ; arrivé en bas (ou en haut), la molette est
     * rendue à l'écran (liste du créatif...).
     */
    public boolean wheel(double deltaY) {
        if (max <= 0) return false;
        int next = Math.max(0, Math.min(max, offset - (int) Math.signum(deltaY) * STEP));
        if (next == offset) return false;
        offset = next;
        return true;
    }
}
