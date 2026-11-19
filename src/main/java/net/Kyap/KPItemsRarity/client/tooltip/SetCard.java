package net.Kyap.KPItemsRarity.client.tooltip;

import net.Kyap.KPItemsRarity.ClientConfig;
import net.Kyap.KPItemsRarity.KPItemsRarity;
import net.Kyap.KPItemsRarity.client.ShareKey;
import net.Kyap.KPItemsRarity.data.ItemDefinition;
import net.Kyap.KPItemsRarity.data.RarityData;
import net.Kyap.KPItemsRarity.data.SetDefinition;
import net.Kyap.KPItemsRarity.stats.SetPieces;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Carte de panoplie. Maintenir Alt gauche sur une pièce :
 *  - la carte REMPLACE l'infobulle (plus rien ne se superpose) ;
 *  - elle reste FIGÉE à sa place tant qu'Alt est maintenu : on peut bouger la souris dessus
 *    et survoler les icônes des pièces pour voir leur nom et si on la porte.
 * Relâcher Alt ferme la carte. Le contenu vient de {@link SetCardContent}, cette classe le place et le dessine.
 *
 *   ┌───────────────────────────────────────┐
 *   │ PANOPLIE EN BOIS                  2/6 │
 *   │ Palier 1 · 6 pièces                   │
 *   │ [▣][▣][□][□][□]                       │  pièces portées encadrées, manquantes grisées
 *   ├──────────────────◆────────────────────┤
 *   │ ✔ 2 pièces   +2 Endurance             │
 *   │ ▸ 3 pièces   +1 Vie · +5 Endurance    │  palier actif
 *   │   4 pièces   ...                      │  pas encore atteint
 *   └───────────────────────────────────────┘
 */
@EventBusSubscriber(modid = KPItemsRarity.MOD_ID, value = Dist.CLIENT)
public final class SetCard {
    private SetCard() {}

    private static final int PAD = 7;
    /** Pas entre deux cases ; la case dessinée fait BOX px, l'objet (16 px) tient dans son cadre. */
    private static final int SLOT = 20, BOX = SLOT - 2;
    private static final int LINE_GUTTER = 7;
    /** En-tête : marge, titre (11), sous-titre (10), espace, rangée de cases, espace. */
    private static final int HEADER_H = PAD + 11 + 10 + 3 + SLOT + 3 + PAD - 4, SLOTS_TOP = 24;
    private static final int SCROLL_STEP = 22;
    private static final long CYCLE_MS = 1000; // une pièce "au choix" par seconde
    private static final int EMPTY_BG = 0x50000000, DIM_MISSING = 0xA0101420, DIM_OTHER_CHOICE = 0x70101420, DOTS_BG = 0xC0101420;

    private static String openSet;
    private static int anchorX, anchorY;
    private static int scroll, scrollMax;

    /** Une case dessinée à la dernière image : la pièce affichée et où (pour le survol). */
    private record PieceSlot(String shownId, int x, int y) {
        boolean contains(int mx, int my) {
            return mx >= x && mx < x + BOX && my >= y && my < y + BOX;
        }
    }

    private static final List<PieceSlot> pieceSlots = new ArrayList<>();

    public static boolean isOpen() {
        if (openSet != null && !ShareKey.isSetDetailsDown()) openSet = null;
        return openSet != null;
    }

    /** Ouvre (fige) la carte pour cet objet s'il fait partie d'une panoplie. */
    public static boolean open(ItemStack stack, int mouseX, int mouseY) {
        String setId = setOf(stack);
        if (setId == null) return false;
        if (!setId.equals(openSet)) scroll = 0;
        openSet = setId;
        anchorX = mouseX;
        anchorY = mouseY;
        return true;
    }

    private static String setOf(ItemStack stack) {
        ItemDefinition def = RarityData.itemDef(stack);
        return def != null && def.hasSet() && RarityData.set(def.set) != null ? def.set : null;
    }

    // ---------------------------------------------------------------- Événements

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (!isOpen()) return;
        SetDefinition set = RarityData.set(openSet);
        if (set == null) {
            openSet = null;
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        g.pose().pushPose();
        try {
            SetCardContent card = SetCardContent.of(openSet, set, Minecraft.getInstance().player);
            render(g, Minecraft.getInstance().font, card);
            hoverPiece(g, event.getMouseX(), event.getMouseY(), card.worn);
        } finally {
            g.pose().popPose();
        }
    }

    @SubscribeEvent
    public static void onClose(ScreenEvent.Closing event) {
        openSet = null;
    }

    /** Molette pendant que la carte est figée : fait défiler les bonus (l'en-tête reste). */
    @SubscribeEvent
    public static void onScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (!isOpen()) return;
        if (scrollMax > 0) scroll = Math.max(0, Math.min(scrollMax, scroll - (int) Math.signum(event.getScrollDeltaY()) * SCROLL_STEP));
        event.setCanceled(true);
    }

    // ---------------------------------------------------------------- Rendu

    /** Où et à quelle taille la carte est dessinée, et la part visible du corps quand il défile. */
    private record Layout(int x, int y, int width, int height, int view, int bodyH) {
        int left() { return x + PAD; }
        int right() { return x + width - PAD; }
        int bodyTop() { return y + HEADER_H + 6; }
        boolean scrolling() { return bodyH > view; }
    }

    /**
     * Même mise en page que les infobulles d'objet : mêmes marges, tout aligné à gauche au même endroit,
     * hauteur limitée (les bonus défilent à la molette), lore tout en bas.
     */
    private static void render(GuiGraphics g, Font font, SetCardContent card) {
        int width = width(font, card);
        List<SetCardContent.Line> body = withLore(font, card, width - 2 * PAD);
        Layout layout = layout(g, body, width);
        scrollMax = Math.max(0, layout.bodyH() - layout.view());
        scroll = Math.min(scroll, scrollMax);

        g.pose().translate(0, 0, 450);
        RpgFrame.draw(g, layout.x(), layout.y(), width, layout.height(), HEADER_H, card.accent,
                card.tier >= RpgFrame.MAX_TIER, RpgFrame.ornateLevelForTier(card.tier));
        drawHeader(g, font, card, layout.left(), layout.right(), layout.y() + PAD);
        drawSlots(g, card, layout.left(), layout.right(), layout.y() + PAD + SLOTS_TOP);
        drawBody(g, font, card, body, layout);
    }

    /** Comme les infobulles : la largeur commune, élargie si une ligne ne tient pas. */
    private static int width(Font font, SetCardContent card) {
        int titleW = font.width(card.title) + 14 + font.width(card.count);
        int subtitleW = font.width(card.subtitle) + 10 + font.width(RpgFrame.tierPips(card.tier));
        int slotsW = Math.min(card.slots.size(), 8) * SLOT;
        int contentW = Math.max(titleW, Math.max(subtitleW, slotsW));
        for (SetCardContent.Line line : card.body) contentW = Math.max(contentW, line.width(font) + LINE_GUTTER);
        return Math.min(ClientConfig.tooltipMaxWidth(), Math.max(ClientConfig.tooltipWidth(), contentW + 2 * PAD));
    }

    /** Les bonus, puis le lore coupé à la largeur de la carte. */
    private static List<SetCardContent.Line> withLore(Font font, SetCardContent card, int inner) {
        List<SetCardContent.Line> body = new ArrayList<>(card.body);
        if (card.lore != null) {
            body.add(new SetCardContent.Gap());
            body.add(new SetCardContent.Rule());
            for (FormattedCharSequence line : font.split(card.lore, inner - LINE_GUTTER)) body.add(new SetCardContent.LoreLine(line));
        }
        return body;
    }

    /** Hauteur limitée à l'écran (le corps défile au-delà) et position figée près de la souris, gardée dans l'écran. */
    private static Layout layout(GuiGraphics g, List<SetCardContent.Line> body, int width) {
        int bodyH = 0;
        for (SetCardContent.Line line : body) bodyH += line.height();
        int screenW = g.guiWidth(), screenH = g.guiHeight();
        int view = Math.min(bodyH, Math.max(30, Math.min(ClientConfig.maxBodyHeight(), screenH - 8 - HEADER_H - 10)));
        int height = HEADER_H + 6 + view + PAD - 2;
        int x = anchorX + 12, y = anchorY - 12;
        if (x + width + 4 > screenW) x = Math.max(4, anchorX - 16 - width);
        if (y + height + 4 > screenH) y = screenH - height - 4;
        return new Layout(x, Math.max(4, y), width, height, view, bodyH);
    }

    /** Le corps, découpé à sa zone visible quand il défile, avec sa barre de défilement. */
    private static void drawBody(GuiGraphics g, Font font, SetCardContent card, List<SetCardContent.Line> body, Layout layout) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        int top = layout.bodyTop();
        boolean scrolling = layout.scrolling();
        int textRight = layout.right() - (scrolling ? LINE_GUTTER : 0);
        if (scrolling) g.enableScissor(layout.x() + 1, top - 1, layout.x() + layout.width() - 1, top + layout.view() + 1);
        int y = top - scroll;
        for (SetCardContent.Line line : body) {
            line.draw(g, font, layout.left(), textRight, y, card.accent);
            y += line.height();
        }
        if (scrolling) {
            g.disableScissor();
            RpgFrame.scrollChrome(g, font, layout.x(), layout.width(), layout.right(), top, layout.view(), layout.bodyH(),
                    scroll, scrollMax, card.accent);
        }
        g.pose().popPose();
    }

    private static void drawHeader(GuiGraphics g, Font font, SetCardContent card, int left, int right, int y) {
        g.drawString(font, card.title, left, y, 0xFFFFFF, true);
        g.drawString(font, card.count, right - font.width(card.count), y, card.wornCount > 0 ? RpgFrame.POSITIVE : RpgFrame.TEXT_DIM, true);
        y += 11;
        g.drawString(font, card.subtitle, left, y, RpgFrame.TEXT_DIM, true);
        Component pips = RpgFrame.tierPips(card.tier);
        g.drawString(font, pips, right - font.width(pips), y, 0xFFFFFF, true);
    }

    // ---------------------------------------------------------------- Cases des pièces

    /**
     * Une case par emplacement. Les pièces "au choix" (épée / hache...) partagent une case qui les fait
     * défiler, avec un petit point par pièce en bas : la pièce affichée en clair, celle portée en vert.
     */
    private static void drawSlots(GuiGraphics g, SetCardContent card, int left, int right, int y) {
        pieceSlots.clear();
        int x = left;
        for (List<String> group : card.slots) {
            if (x + SLOT > right + 2) break;
            String shownId = cyclingPiece(group);
            ItemStack shown = SetPieces.stackOf(shownId);
            if (shown == null) continue;
            boolean slotFilled = group.stream().anyMatch(card.worn::contains);
            drawSlotFrame(g, x, y, slotFilled, card.accent);
            g.renderFakeItem(shown, x + 1, y + 1);
            drawSlotOverlay(g, group, shownId, card, x, y, slotFilled);
            pieceSlots.add(new PieceSlot(shownId, x, y));
            x += SLOT;
        }
    }

    /** La pièce à montrer : la seule, ou chacune à son tour (une par seconde) s'il y en a plusieurs au choix. */
    private static String cyclingPiece(List<String> group) {
        int index = (int) ((Util.getMillis() / CYCLE_MS) % group.size());
        return group.get(index);
    }

    /** Fond et contour de la case, à la couleur de la panoplie si une de ses pièces est portée. */
    private static void drawSlotFrame(GuiGraphics g, int x, int y, boolean filled, int accent) {
        g.fill(x, y, x + BOX, y + BOX, filled ? RpgFrame.argb(accent, 0x30) : EMPTY_BG);
        RpgFrame.outline(g, x, y, BOX, BOX, filled ? RpgFrame.argb(accent, 0xC0) : RpgFrame.argb(RpgFrame.TEXT_FAINT, 0x80));
    }

    /** Voile sur une pièce pas portée (plus léger si une autre pièce au choix l'est), puis les points de choix. */
    private static void drawSlotOverlay(GuiGraphics g, List<String> group, String shownId, SetCardContent card,
                                        int x, int y, boolean slotFilled) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        if (!card.worn.contains(shownId)) g.fill(x + 1, y + 1, x + BOX - 1, y + BOX - 1, slotFilled ? DIM_OTHER_CHOICE : DIM_MISSING);
        if (group.size() > 1) drawChoiceDots(g, group, shownId, card.worn, card.accent, x, y);
        g.pose().popPose();
    }

    /** Un point de 2 px par pièce au choix : couleur de la panoplie pour celle affichée, vert pour celle portée. */
    private static void drawChoiceDots(GuiGraphics g, List<String> group, String shownId, Set<String> worn, int accent, int x, int y) {
        int dotsW = group.size() * 3 - 1;
        int dx = x + (BOX - dotsW) / 2, dy = y + BOX - 4;
        g.fill(dx - 1, dy - 1, dx + dotsW + 1, dy + 3, DOTS_BG);
        for (String piece : group) {
            int color = piece.equals(shownId) ? RpgFrame.argb(accent, 0xFF)
                    : worn.contains(piece) ? RpgFrame.argb(RpgFrame.POSITIVE, 0xC0)
                    : RpgFrame.argb(RpgFrame.TEXT_FAINT, 0xA0);
            g.fill(dx, dy, dx + 2, dy + 2, color);
            dx += 3;
        }
    }

    /** Petite infobulle sur la case survolée : nom de la pièce, type, portée ou non, pièces au choix. */
    private static void hoverPiece(GuiGraphics g, int mouseX, int mouseY, Set<String> worn) {
        for (PieceSlot slot : pieceSlots) {
            if (!slot.contains(mouseX, mouseY)) continue;
            ItemStack stack = SetPieces.stackOf(slot.shownId());
            if (stack != null) drawPieceTooltip(g, stack, slot.shownId(), worn.contains(slot.shownId()), mouseX, mouseY);
            return;
        }
    }

    private static void drawPieceTooltip(GuiGraphics g, ItemStack stack, String id, boolean worn, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        Component name = stack.getHoverName().copy().withStyle(s -> s.withColor(RpgFrame.TEXT));
        Component type = ItemTypes.typeName(stack);
        Component state = Component.translatable(worn ? "setcard.kpitemsrarity.worn" : "setcard.kpitemsrarity.missing");
        List<String> others = openSet != null ? SetPieces.alternatives(openSet, id) : List.of();
        Component choice = others.isEmpty() ? null : Component.translatable("tooltip.kpitemsrarity.rpg.set_choice", SetPieces.names(others));

        int w = Math.max(Math.max(font.width(name), font.width(type) + 10 + font.width(state)), choice == null ? 0 : font.width(choice)) + 12;
        int h = choice == null ? 28 : 39;
        int x = Math.min(mouseX + 10, g.guiWidth() - w - 4), y = Math.max(4, mouseY - h - 4);
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        RpgFrame.draw(g, x, y, w, h, 0, worn ? RpgFrame.POSITIVE : RpgFrame.TEXT_DIM, false);
        g.drawString(font, name, x + 6, y + 5, 0xFFFFFF, true);
        g.drawString(font, type, x + 6, y + 16, RpgFrame.TEXT_DIM, true);
        g.drawString(font, state, x + w - 6 - font.width(state), y + 16, worn ? RpgFrame.POSITIVE : RpgFrame.TEXT_FAINT, true);
        if (choice != null) g.drawString(font, choice, x + 6, y + 27, RpgFrame.TEXT_DIM, true);
        g.pose().popPose();
    }
}
