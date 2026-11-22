package net.Kyap.KPItemsRarity.client.tooltip.card;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Une fenêtre façon RPG : un en-tête, un corps d'éléments qui défile s'il est trop haut pour l'écran, et un pied
 * qui reste en bas. L'infobulle d'un objet et la carte d'une panoplie sont deux fenêtres de ce genre, dessinées par
 * le même moteur ({@link CardRenderer}) et gardées d'une image à l'autre par {@link LiveCard}.
 *
 * @param header en-tête : nom, aperçu de l'objet, cases des pièces...
 * @param body   corps, de haut en bas
 * @param footer pied fixe (durabilité), ou null
 * @param style  couleur et ornements du cadre
 */
public record Card(Header header, List<Elem> body, @Nullable Component footer, FrameStyle style) {

    /** L'en-tête dit sa taille et se dessine lui-même : c'est la seule partie propre à chaque fenêtre. */
    public interface Header {
        /** Pas d'en-tête : une petite fenêtre d'aide, rien que le corps. */
        Header NONE = new Header() {
            @Override
            public int width(Font font) {
                return 0;
            }

            @Override
            public int height() {
                return 0;
            }

            @Override
            public void draw(GuiGraphics g, Font font, int left, int right, int top) {}
        };

        /** Largeur que l'en-tête demande à la fenêtre. */
        int width(Font font);

        int height();

        /**
         * @param left  bord gauche du contenu (marge comprise)
         * @param right bord droit du contenu
         * @param top   haut de la fenêtre
         */
        void draw(GuiGraphics g, Font font, int left, int right, int top);
    }
}
