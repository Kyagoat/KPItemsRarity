package net.Kyap.KPItemsRarity.client.tooltip.card;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Une fenêtre façon RPG : un en-tête, un corps d'éléments qui défile s'il est trop haut pour l'écran, et un pied
 * qui reste en bas. L'infobulle d'un objet et la carte d'une panoplie sont deux fenêtres de ce genre, dessinées par
 * le même moteur ({@link CardRenderer}).
 *
 * @param header en-tête : nom, aperçu de l'objet, cases des pièces...
 * @param body   corps, de haut en bas
 * @param footer pied fixe (durabilité), ou null
 * @param accent couleur du cadre (rareté, palier de panoplie)
 * @param pulse  le cadre "respire" (Légendaire, Mythique, palier maximum)
 * @param ornate niveau d'ornements du cadre, voir {@link RpgFrame#ornateLevel}
 */
public record Card(Header header, List<Elem> body, @Nullable Component footer, int accent, boolean pulse, int ornate) {

    /** L'en-tête dit sa taille et se dessine lui-même : c'est la seule partie propre à chaque fenêtre. */
    public interface Header {
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
