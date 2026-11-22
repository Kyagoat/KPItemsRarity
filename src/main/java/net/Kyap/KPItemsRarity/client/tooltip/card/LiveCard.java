package net.Kyap.KPItemsRarity.client.tooltip.card;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Une fenêtre affichée image après image. Construire sa carte et la mesurer coûte cher (lire l'objet, retourner le
 * texte à la ligne) ; la dessiner, non. La carte n'est donc reconstruite que quand ce qu'elle montre change (sa clé :
 * l'objet survolé, la panoplie...) et au plus tard toutes les {@link #REFRESH_MS} ms, pour ce qui bouge tout seul
 * (recharge d'un passif, pièces portées, lignes des autres mods). Le défilement repart du haut avec une nouvelle clé.
 */
public final class LiveCard {
    private static final long REFRESH_MS = 250;

    private final CardScroll scroll = new CardScroll();
    @Nullable private Object key;
    @Nullable private Card card;
    @Nullable private CardRenderer.Size size;
    private long builtAt;
    private long shownSince;

    /**
     * Dessine la carte de {@code key} à côté de (anchorX, anchorY), à la profondeur {@code z} ; {@code build} n'est
     * appelé que si elle doit être reconstruite. La clé doit savoir se comparer (equals) d'une image à l'autre.
     * @return où la carte a été dessinée
     */
    public CardRenderer.Placement render(GuiGraphics g, Font font, Object key, Supplier<Card> build, int anchorX, int anchorY, int z) {
        long now = Util.getMillis();
        boolean sameKey = key.equals(this.key);
        if (!sameKey) {
            this.key = key;
            shownSince = now;
            scroll.reset();
            card = null;
        }
        if (card == null || now - builtAt > REFRESH_MS) {
            Card built = build.get();
            size = CardRenderer.measure(built, font);
            card = built;
            builtAt = now;
        }
        return CardRenderer.render(g, font, card, size, scroll, anchorX, anchorY, z);
    }

    /** Depuis quand la clé actuelle est affichée (pour les animations qui attendent un peu : nom qui défile). */
    public long shownSince() {
        return shownSince;
    }

    /** Un cran de molette ; vrai si le corps a défilé. */
    public boolean wheel(double deltaY) {
        return scroll.wheel(deltaY);
    }
}
