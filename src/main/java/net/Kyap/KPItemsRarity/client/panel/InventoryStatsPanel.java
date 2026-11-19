package net.Kyap.KPItemsRarity.client.panel;

import net.Kyap.KPItemsRarity.KPItemsRarity;
import net.Kyap.KPItemsRarity.client.tooltip.SetCard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Fiche de personnage directement sur l'inventaire (E).
 * Un petit bouton à côté du livre de recettes l'ouvre / la ferme ; elle s'affiche à droite de
 * l'inventaire (à gauche si l'écran est trop étroit). Bandeau Vie / Armure / Dégâts, puis les
 * caractéristiques du personnage par famille ({@link PanelRows}), avec leur détail au survol.
 *
 * Textures (remplaçables par un pack de ressources) :
 *   assets/kpitemsrarity/textures/gui/sprites/stats/panel.png               (nine-slice, voir .mcmeta)
 *   assets/kpitemsrarity/textures/gui/sprites/stats/button_small.png / button_small_highlighted.png  (10x10)
 */
@EventBusSubscriber(modid = KPItemsRarity.MOD_ID, value = Dist.CLIENT)
public final class InventoryStatsPanel {
    private InventoryStatsPanel() {}

    public static final ResourceLocation PANEL = KPItemsRarity.id("stats/panel");
    public static final WidgetSprites BUTTON = new WidgetSprites(
            KPItemsRarity.id("stats/button_small"), KPItemsRarity.id("stats/button_small_highlighted"));
    /** Petit bouton en haut à droite du cadre du personnage (là où Apothic Attributes mettait le sien). */
    private static final int BUTTON_X = 63, BUTTON_Y = 9, BUTTON_SIZE = 10;

    private static final int WIDTH = PanelScreens.WIDTH;
    private static final int GAP = PanelScreens.GAP;
    private static final int PAD = 7;
    private static final int ROW = 10;
    private static final int TEXT = 0x404040;
    private static final int SUBTLE = 0x7A7A7A;
    /** Stat à 0 : affichée quand même, pour montrer qu'on peut en obtenir. */
    private static final int ZERO = 0xA8A8A8;
    /** Stat en négatif (malus de panoplie...). */
    private static final int MALUS = 0xC0392B;
    private static final int ZERO_TEXT = 0x8A8A8A, STRIPE = 0x18000000, DARK = 0xFF373737, MID_GREY = 0xFF8B8B8B;
    private static final int BAND_H = 14, BAND_LIGHT = 0xFFFFFFFF, BAND_FILL = 0xFFB5B5B5;

    private static final ResourceLocation HEART = ResourceLocation.withDefaultNamespace("hud/heart/full");
    private static final ResourceLocation ARMOR = ResourceLocation.withDefaultNamespace("hud/armor_full");
    private static final ItemStack SWORD = new ItemStack(Items.IRON_SWORD);

    /** Ouverte par défaut ; mémorisé pour la session. */
    private static boolean open = true;
    /** Stats à 0 affichées (sinon repliées sous une ligne "afficher"). */
    private static boolean showAll = false;
    private static double scroll = 0;
    private static ImageButton button;

    // État de la dernière image : survol et zone de la ligne "afficher / masquer"
    private static int lastMouseX, lastMouseY;
    private static String hoverKey;
    private static List<StatDetail> hoverDetails = List.of();
    @Nullable private static Rect toggleRect;

    /** Un rectangle à l'écran (zone cliquable). */
    private record Rect(int left, int top, int right, int bottom) {
        boolean contains(double x, double y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }

    /** Une case du bandeau du haut : son icône (sprite ou objet), sa valeur et son nom au survol. */
    private record BandCell(String hoverKey, @Nullable ResourceLocation sprite, @Nullable ItemStack item, String value) {}

    /** Les autres mods passent par api/KPItemsRarityClientApi.registerStatsPanel. */
    public static void registerScreen(Class<?> screen, Function<Screen, LivingEntity> subject) {
        PanelScreens.register(screen, subject);
    }

    // ---------------------------------------------------------------- Bouton

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Post event) {
        if (!PanelScreens.hasButton(event.getScreen())) return;
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) event.getScreen();
        button = new ImageButton(screen.getGuiLeft() + BUTTON_X, screen.getGuiTop() + BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE, BUTTON, b -> {
            open = !open;
            scroll = 0;
        });
        button.setTooltip(Tooltip.create(Component.translatable("panel.kpitemsrarity.toggle")));
        event.addListener(button);
    }

    /** Le livre de recettes décale l'inventaire : on suit. */
    @SubscribeEvent
    public static void onRenderPre(ScreenEvent.Render.Pre event) {
        if (PanelScreens.hasButton(event.getScreen()) && event.getScreen() instanceof AbstractContainerScreen<?> screen && button != null) {
            button.setPosition(screen.getGuiLeft() + BUTTON_X, screen.getGuiTop() + BUTTON_Y);
        }
    }

    /** Les effets de potion s'affichent à droite de l'inventaire : on les pousse après la fiche. */
    @SubscribeEvent
    public static void onMobEffects(ScreenEvent.RenderInventoryMobEffects event) {
        if (!open || !(PanelScreens.panelScreen(event.getScreen()) instanceof AbstractContainerScreen<?> screen)) return;
        int x = PanelScreens.panelX(screen);
        if (x == PanelScreens.NO_ROOM || !PanelScreens.isOnRight(screen, x)) return;
        int newOffset = x + WIDTH + GAP;
        int space = screen.width - newOffset;
        if (space < 32) return; // pas la place : tant pis, on laisse
        event.setHorizontalOffset(newOffset);
        event.setCompact(space < 120);
    }

    // ---------------------------------------------------------------- Souris

    /** La fiche ouverte est-elle sous la souris ? */
    private static boolean overPanel(Screen screen, double mouseX, double mouseY) {
        return open && PanelScreens.panelScreen(screen) instanceof AbstractContainerScreen<?> s && PanelScreens.isInside(s, mouseX, mouseY);
    }

    @SubscribeEvent
    public static void onClick(ScreenEvent.MouseButtonPressed.Pre event) {
        // Sinon un clic sur la fiche compte comme "en dehors de l'inventaire" et jette l'objet tenu.
        if (!overPanel(event.getScreen(), event.getMouseX(), event.getMouseY())) return;
        if (toggleRect != null && toggleRect.contains(event.getMouseX(), event.getMouseY())) {
            showAll = !showAll;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRelease(ScreenEvent.MouseButtonReleased.Pre event) {
        if (overPanel(event.getScreen(), event.getMouseX(), event.getMouseY())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (!overPanel(event.getScreen(), event.getMouseX(), event.getMouseY())) return;
        scroll -= event.getScrollDeltaY() * ROW * 2;
        event.setCanceled(true);
    }

    private static boolean isHovering(int x, int y, int w, int h) {
        return lastMouseX >= x && lastMouseX < x + w && lastMouseY >= y && lastMouseY < y + h;
    }

    // ---------------------------------------------------------------- Rendu

    @SubscribeEvent
    public static void onRender(ContainerScreenEvent.Render.Background event) {
        if (!open || !(PanelScreens.panelScreen(event.getContainerScreen()) instanceof AbstractContainerScreen<?> screen)) return;
        LivingEntity entity = PanelScreens.subject(screen);
        if (entity == null) return;
        int x = PanelScreens.panelX(screen);
        if (x == PanelScreens.NO_ROOM) return;

        GuiGraphics g = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        int y = screen.getGuiTop();
        int h = screen.getYSize();
        g.blitSprite(PANEL, x, y, WIDTH, h);
        Component title = Component.translatable("panel.kpitemsrarity.title");
        g.drawString(font, title, x + (WIDTH - font.width(title)) / 2, y + 6, TEXT, false);
        int bandY = y + 18;
        drawBand(g, font, entity, x + PAD, x + WIDTH - PAD, bandY);
        drawRows(g, font, PanelRows.build(entity, showAll), x + PAD, x + WIDTH - PAD, bandY + 18, y + h - PAD);
    }

    /** Bandeau du haut : Vie, Armure, Dégâts (valeurs totales du personnage), avec leur nom au survol. */
    private static void drawBand(GuiGraphics g, Font font, LivingEntity entity, int left, int innerRight, int bandY) {
        g.fill(left - 1, bandY, innerRight + 1, bandY + BAND_H, MID_GREY);
        g.fill(left, bandY + 1, innerRight + 1, bandY + BAND_H, BAND_LIGHT);
        g.fill(left, bandY + 1, innerRight, bandY + BAND_H - 1, BAND_FILL);
        List<BandCell> cells = List.of(
                new BandCell("panel.kpitemsrarity.health", HEART, null, number(entity.getMaxHealth())),
                new BandCell("panel.kpitemsrarity.armor", ARMOR, null, number(entity.getArmorValue())),
                new BandCell("panel.kpitemsrarity.damage", null, SWORD, number(entity.getAttributeValue(Attributes.ATTACK_DAMAGE))));
        int cellW = (innerRight - left) / cells.size();
        hoverKey = null;
        for (int i = 0; i < cells.size(); i++) {
            BandCell cell = cells.get(i);
            int cellX = left + i * cellW;
            drawBandCell(g, font, cell, cellX + 2, bandY + 3);
            if (isHovering(cellX, bandY, cellW, BAND_H)) hoverKey = cell.hoverKey();
        }
    }

    /** L'icône (sprite 9 px ou objet réduit) puis la valeur. */
    private static void drawBandCell(GuiGraphics g, Font font, BandCell cell, int x, int y) {
        if (cell.sprite() != null) {
            g.blitSprite(cell.sprite(), x, y, 9, 9);
        } else if (cell.item() != null) {
            g.pose().pushPose();
            g.pose().translate(x - 0.5f, y - 0.5f, 0);
            g.pose().scale(0.625f, 0.625f, 1f);
            g.renderFakeItem(cell.item(), 0, 0);
            g.pose().popPose();
        }
        g.drawString(font, cell.value(), x + 12, y + 1, TEXT, false);
    }

    /** La liste des caractéristiques, qui défile à la molette, avec une barre quand elle dépasse. */
    private static void drawRows(GuiGraphics g, Font font, List<PanelRows.Row> rows, int left, int innerRight, int top, int bottom) {
        int viewHeight = bottom - top;
        int contentHeight = rows.size() * ROW;
        int maxScroll = Math.max(0, contentHeight - viewHeight);
        scroll = Mth.clamp(scroll, 0, maxScroll);

        int right = innerRight - (maxScroll > 0 ? 4 : 0);
        hoverDetails = List.of();
        toggleRect = null;
        g.enableScissor(left - 1, top, right + 1, bottom);
        int rowY = top - (int) scroll;
        int stripe = 0;
        for (PanelRows.Row row : rows) {
            if (row instanceof PanelRows.Header) stripe = 0;
            boolean visible = rowY + ROW > top && rowY < bottom;
            if (visible) {
                drawRow(g, font, row, left, right, rowY, stripe++ % 2 == 1);
                int visibleTop = Math.max(rowY, top), visibleBottom = Math.min(rowY + ROW, bottom);
                if (row instanceof PanelRows.StatLine line && !line.details().isEmpty()
                        && isHovering(left, visibleTop, right - left, visibleBottom - visibleTop)) hoverDetails = line.details();
            }
            rowY += ROW;
        }
        g.disableScissor();
        if (maxScroll > 0) drawScrollBar(g, font, left, right, innerRight, top, bottom, contentHeight, maxScroll);
    }

    /** Flèches ▴ / ▾ au centre et barre fine à droite, aux couleurs de l'inventaire vanilla. */
    private static void drawScrollBar(GuiGraphics g, Font font, int left, int right, int innerRight, int top, int bottom,
                                      int contentHeight, int maxScroll) {
        int viewHeight = bottom - top;
        int arrowX = left + (right - left - font.width("▾")) / 2;
        if (scroll < maxScroll) g.drawString(font, "▾", arrowX, bottom - 3, DARK, false);
        if (scroll > 0) g.drawString(font, "▴", arrowX, top - 2, DARK, false);
        int barX = innerRight - 2;
        int thumbH = Math.max(12, viewHeight * viewHeight / contentHeight);
        int thumbY = top + (int) ((viewHeight - thumbH) * (scroll / maxScroll));
        g.fill(barX, top, barX + 2, bottom, MID_GREY);
        g.fill(barX, thumbY, barX + 2, thumbY + thumbH, DARK);
    }

    private static void drawRow(GuiGraphics g, Font font, PanelRows.Row row, int left, int right, int y, boolean stripe) {
        switch (row) {
            case PanelRows.Header header -> {
                // Famille : icône et nom à sa couleur, filet de la même couleur
                g.drawString(font, header.label(), left, y + 2, header.color(), false);
                g.fill(left + font.width(header.label()) + 3, y + 6, right, y + 7, 0xFF000000 | header.color());
            }
            case PanelRows.Toggle toggle -> {
                boolean hover = isHovering(left, y, right - left, ROW);
                g.drawString(font, toggle.label(), left + (right - left - font.width(toggle.label())) / 2, y + 1, hover ? TEXT : SUBTLE, false);
                toggleRect = new Rect(left, y, right, y + ROW);
            }
            case PanelRows.StatLine line -> drawStat(g, font, line, left, right, y, stripe);
        }
    }

    /** Petit carré à la couleur de la stat, nom (coupé avec "…" s'il le faut), valeur alignée à droite. */
    private static void drawStat(GuiGraphics g, Font font, PanelRows.StatLine line, int left, int right, int y, boolean stripe) {
        boolean zero = line.shade() == PanelRows.Shade.ZERO;
        boolean malus = line.shade() == PanelRows.Shade.MALUS;
        if (stripe) g.fill(left - 1, y, right + 1, y + ROW, STRIPE);
        drawColorSquare(g, left, y + 1, zero ? ZERO : malus ? MALUS : line.color(), zero);
        int labelX = left + 10;
        int valueWidth = font.width(line.value());
        String label = shorten(font, line.label().getString(), right - labelX - valueWidth - 3);
        int valueColor = zero ? ZERO_TEXT : malus ? MALUS : darken(line.color());
        g.drawString(font, label, labelX, y + 1, zero ? ZERO_TEXT : TEXT, false);
        g.drawString(font, line.value(), right - valueWidth, y + 1, valueColor, false);
    }

    /** Carré de 7 px bordé de sombre (de gris pour une stat à 0). */
    private static void drawColorSquare(GuiGraphics g, int x, int y, int rgb, boolean faded) {
        g.fill(x, y, x + 7, y + 7, faded ? 0xFF9A9A9A : DARK);
        g.fill(x + 1, y + 1, x + 6, y + 6, 0xFF000000 | rgb);
    }

    private static String shorten(Font font, String text, int maxWidth) {
        return font.width(text) <= maxWidth ? text : font.plainSubstrByWidth(text, maxWidth - font.width("…")) + "…";
    }

    @SubscribeEvent
    public static void onRenderPost(ScreenEvent.Render.Post event) {
        lastMouseX = event.getMouseX();
        lastMouseY = event.getMouseY();
        if (!open || PanelScreens.panelScreen(event.getScreen()) == null || SetCard.isOpen()) return;
        if (hoverKey != null) {
            event.getGuiGraphics().renderTooltip(Minecraft.getInstance().font, Component.translatable(hoverKey), event.getMouseX(), event.getMouseY());
        } else if (!hoverDetails.isEmpty()) {
            StatDetailTooltip.render(event.getGuiGraphics(), Minecraft.getInstance().font, hoverDetails, event.getMouseX(), event.getMouseY());
        }
    }

    private static String number(double value) {
        return Math.abs(value - Math.round(value)) < 0.05
                ? String.valueOf(Math.round(value))
                : String.format(Locale.ROOT, "%.1f", value);
    }

    /** Couleur plus sombre pour rester lisible sur le fond gris clair. */
    private static int darken(int rgb) {
        int r = (rgb >> 16) & 0xFF, gr = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return ((r * 11 / 20) << 16) | ((gr * 11 / 20) << 8) | (b * 11 / 20);
    }
}
