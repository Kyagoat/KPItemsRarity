package net.Kyap.KPItemsRarity.client.panel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Les écrans qui montrent la fiche (inventaire de survie, créatif, Curios, écrans d'autres mods enregistrés),
 * le personnage qu'elle décrit, et où elle se place à côté de l'écran.
 */
final class PanelScreens {
    private PanelScreens() {}

    static final int WIDTH = 132;
    static final int GAP = 2;
    /** Valeur de panelX quand il n'y a pas la place. */
    static final int NO_ROOM = Integer.MIN_VALUE;

    /** Écrans d'autres mods qui veulent la fiche, avec le personnage à afficher (ex. KP PNJ : un villageois). */
    private static final Map<Class<?>, Function<Screen, LivingEntity>> EXTRA = new ConcurrentHashMap<>();
    private static final String CURIOS_SCREEN = "top.theillusivec4.curios.client.gui.CuriosScreen";

    static void register(Class<?> screen, Function<Screen, LivingEntity> subject) {
        EXTRA.put(screen, subject);
    }

    /** Écrans qui ont le bouton de la fiche : l'inventaire de survie et les écrans enregistrés. */
    static boolean hasButton(Screen screen) {
        return screen instanceof InventoryScreen || EXTRA.containsKey(screen.getClass());
    }

    /**
     * L'écran s'il montre la fiche, sinon null. Curios n'est pas une dépendance : on reconnaît son écran
     * par le nom de sa classe (on y voit ses stats changer en équipant ses accessoires).
     */
    @Nullable
    static AbstractContainerScreen<?> panelScreen(Screen screen) {
        boolean curios = screen instanceof AbstractContainerScreen<?> && screen.getClass().getName().equals(CURIOS_SCREEN);
        return screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen || curios
                || EXTRA.containsKey(screen.getClass()) ? (AbstractContainerScreen<?>) screen : null;
    }

    /** Le personnage dont la fiche est affichée : le joueur, ou celui d'un écran enregistré. */
    @Nullable
    static LivingEntity subject(Screen screen) {
        Function<Screen, LivingEntity> f = EXTRA.get(screen.getClass());
        return f != null ? f.apply(screen) : Minecraft.getInstance().player;
    }

    /** x de la fiche (à droite de l'inventaire, sinon à gauche), ou NO_ROOM s'il n'y a pas la place. */
    static int panelX(AbstractContainerScreen<?> screen) {
        int right = screen.getGuiLeft() + screen.getXSize() + GAP;
        int left = screen.getGuiLeft() - GAP - WIDTH;
        if (right + WIDTH <= screen.width) return right;
        boolean book = screen instanceof InventoryScreen inv && inv.getRecipeBookComponent().isVisible();
        if (!book && left >= 0) return left;
        return NO_ROOM;
    }

    static boolean isOnRight(AbstractContainerScreen<?> screen, int x) {
        return x > screen.getGuiLeft();
    }

    static boolean isInside(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        int x = panelX(screen);
        if (x == NO_ROOM) return false;
        int y = screen.getGuiTop();
        return mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + screen.getYSize();
    }
}
