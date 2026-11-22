package net.Kyap.KPItemsRarity.client.tooltip.setcard;

import net.Kyap.KPItemsRarity.KPItemsRarity;
import net.Kyap.KPItemsRarity.client.ShareKey;
import net.Kyap.KPItemsRarity.client.tooltip.card.Card;
import net.Kyap.KPItemsRarity.client.tooltip.card.CardRenderer;
import net.Kyap.KPItemsRarity.client.tooltip.card.FrameStyle;
import net.Kyap.KPItemsRarity.client.tooltip.card.LiveCard;
import net.Kyap.KPItemsRarity.client.tooltip.card.Palette;
import net.Kyap.KPItemsRarity.client.tooltip.card.RpgFrame;
import net.Kyap.KPItemsRarity.client.tooltip.text.ItemTypes;
import net.Kyap.KPItemsRarity.client.tooltip.text.Texts;
import net.Kyap.KPItemsRarity.data.ItemDefinition;
import net.Kyap.KPItemsRarity.data.RarityData;
import net.Kyap.KPItemsRarity.data.SetDefinition;
import net.Kyap.KPItemsRarity.stats.SetLayout;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Carte de panoplie. Maintenir Alt gauche sur une pièce :
 *  - la carte REMPLACE l'infobulle (plus rien ne se superpose) ;
 *  - elle reste FIGÉE à sa place tant qu'Alt est maintenu : on peut bouger la souris dessus
 *    et survoler les icônes des pièces pour voir leur nom et si on la porte.
 * Relâcher Alt ferme la carte. Le contenu vient de {@link SetCardContent} ; cette classe dessine l'en-tête (titre et
 * cases des pièces) et confie le reste au moteur commun des fenêtres ({@link CardRenderer}), comme l'infobulle d'objet.
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

    private static final int PAD = CardRenderer.PAD;
    /** Pas entre deux cases ; la case dessinée fait BOX px, l'objet (16 px) tient dans son cadre. */
    private static final int SLOT = 20, BOX = SLOT - 2;
    /** En-tête : marge, titre (11), sous-titre (10), espace, rangée de cases, espace. */
    private static final int HEADER_H = PAD + 11 + 10 + 3 + SLOT + 3 + PAD - 4, SLOTS_TOP = 24;
    /** Au-dessus des infobulles ; la petite infobulle d'une pièce encore au-dessus. */
    private static final int Z = 450, PIECE_Z = Z + 300;
    private static final long CYCLE_MS = 1000; // une pièce "au choix" par seconde
    private static final int EMPTY_BG = 0x50000000, DIM_MISSING = 0xA0101420, DIM_OTHER_CHOICE = 0x70101420, DOTS_BG = 0xC0101420;

    private static final LiveCard SHOWN = new LiveCard();
    @Nullable private static String openSet;
    private static int anchorX, anchorY;
    /** Le contenu de la dernière carte construite (pour le survol des cases). */
    @Nullable private static SetCardContent content;

    /** Une case dessinée à la dernière image : la pièce affichée et où (pour le survol). */
    private record PieceSlot(SetLayout.Piece shown, int x, int y) {
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
        openSet = setId;
        anchorX = mouseX;
        anchorY = mouseY;
        return true;
    }

    @Nullable
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
        String setId = openSet;
        SHOWN.render(g, Minecraft.getInstance().font, setId, () -> card(setId, set), anchorX, anchorY, Z);
        if (content != null) hoverPiece(g, event.getMouseX(), event.getMouseY(), content.worn);
    }

    private static Card card(String setId, SetDefinition set) {
        content = SetCardContent.of(setId, set, Minecraft.getInstance().player);
        return new Card(new PiecesHeader(content), content.body, null, FrameStyle.ofTier(content.tier));
    }

    @SubscribeEvent
    public static void onClose(ScreenEvent.Closing event) {
        openSet = null;
    }

    /** Molette pendant que la carte est figée : fait défiler les bonus (l'en-tête reste). */
    @SubscribeEvent
    public static void onScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (!isOpen()) return;
        SHOWN.wheel(event.getScrollDeltaY());
        event.setCanceled(true);
    }

    // ---------------------------------------------------------------- En-tête

    /**
     * L'en-tête de la carte : nom de la panoplie et pièces portées, palier, puis une case par emplacement.
     * Le corps (bonus par palier, lore) est dessiné par le moteur commun.
     */
    private record PiecesHeader(SetCardContent content) implements Card.Header {
        @Override
        public int width(Font font) {
            int titleW = font.width(content.title) + 14 + font.width(content.count);
            int subtitleW = font.width(content.subtitle) + 10 + font.width(Texts.tierPips(content.tier));
            int slotsW = Math.min(content.slots.size(), 8) * SLOT;
            return Math.max(titleW, Math.max(subtitleW, slotsW));
        }

        @Override
        public int height() {
            return HEADER_H;
        }

        @Override
        public void draw(GuiGraphics g, Font font, int left, int right, int top) {
            int y = top + PAD;
            g.drawString(font, content.title, left, y, 0xFFFFFF, true);
            g.drawString(font, content.count, right - font.width(content.count), y, content.wornCount > 0 ? Palette.POSITIVE : Palette.TEXT_DIM, true);
            g.drawString(font, content.subtitle, left, y + 11, Palette.TEXT_DIM, true);
            Component pips = Texts.tierPips(content.tier);
            g.drawString(font, pips, right - font.width(pips), y + 11, 0xFFFFFF, true);
            drawSlots(g, content, left, right, y + SLOTS_TOP);
        }
    }

    // ---------------------------------------------------------------- Cases des pièces

    /**
     * Une case par emplacement. Les pièces "au choix" (épée / hache...) partagent une case qui les fait
     * défiler, avec un petit point par pièce en bas : la pièce affichée en clair, celle portée en vert.
     */
    private static void drawSlots(GuiGraphics g, SetCardContent card, int left, int right, int y) {
        pieceSlots.clear();
        int x = left;
        for (List<SetLayout.Piece> group : card.slots) {
            if (x + SLOT > right + 2) break;
            SetLayout.Piece shown = cyclingPiece(group);
            boolean slotFilled = group.stream().anyMatch(piece -> card.worn.contains(piece.id()));
            drawSlotFrame(g, x, y, slotFilled, card.accent);
            g.renderFakeItem(shown.stack(), x + 1, y + 1);
            drawSlotOverlay(g, group, shown, card, x, y, slotFilled);
            pieceSlots.add(new PieceSlot(shown, x, y));
            x += SLOT;
        }
    }

    /** La pièce à montrer : la seule, ou chacune à son tour (une par seconde) s'il y en a plusieurs au choix. */
    private static SetLayout.Piece cyclingPiece(List<SetLayout.Piece> group) {
        int index = (int) ((Util.getMillis() / CYCLE_MS) % group.size());
        return group.get(index);
    }

    /** Fond et contour de la case, à la couleur de la panoplie si une de ses pièces est portée. */
    private static void drawSlotFrame(GuiGraphics g, int x, int y, boolean filled, int accent) {
        g.fill(x, y, x + BOX, y + BOX, filled ? Palette.argb(accent, 0x30) : EMPTY_BG);
        RpgFrame.outline(g, x, y, BOX, BOX, filled ? Palette.argb(accent, 0xC0) : Palette.argb(Palette.TEXT_FAINT, 0x80));
    }

    /** Voile sur une pièce pas portée (plus léger si une autre pièce au choix l'est), puis les points de choix. */
    private static void drawSlotOverlay(GuiGraphics g, List<SetLayout.Piece> group, SetLayout.Piece shown, SetCardContent card,
                                        int x, int y, boolean slotFilled) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        if (!card.worn.contains(shown.id())) g.fill(x + 1, y + 1, x + BOX - 1, y + BOX - 1, slotFilled ? DIM_OTHER_CHOICE : DIM_MISSING);
        if (group.size() > 1) drawChoiceDots(g, group, shown, card.worn, card.accent, x, y);
        g.pose().popPose();
    }

    /** Un point de 2 px par pièce au choix : couleur de la panoplie pour celle affichée, vert pour celle portée. */
    private static void drawChoiceDots(GuiGraphics g, List<SetLayout.Piece> group, SetLayout.Piece shown, Set<String> worn, int accent, int x, int y) {
        int dotsW = group.size() * 3 - 1;
        int dx = x + (BOX - dotsW) / 2, dy = y + BOX - 4;
        g.fill(dx - 1, dy - 1, dx + dotsW + 1, dy + 3, DOTS_BG);
        for (SetLayout.Piece piece : group) {
            int color = piece == shown ? Palette.argb(accent, 0xFF)
                    : worn.contains(piece.id()) ? Palette.argb(Palette.POSITIVE, 0xC0)
                    : Palette.argb(Palette.TEXT_FAINT, 0xA0);
            g.fill(dx, dy, dx + 2, dy + 2, color);
            dx += 3;
        }
    }

    /** Petite infobulle sur la case survolée : nom de la pièce, type, portée ou non, pièces au choix. */
    private static void hoverPiece(GuiGraphics g, int mouseX, int mouseY, Set<String> worn) {
        for (PieceSlot slot : pieceSlots) {
            if (!slot.contains(mouseX, mouseY)) continue;
            drawPieceTooltip(g, slot.shown(), worn.contains(slot.shown().id()), mouseX, mouseY);
            return;
        }
    }

    private static void drawPieceTooltip(GuiGraphics g, SetLayout.Piece piece, boolean worn, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        Component name = piece.stack().getHoverName().copy().withStyle(s -> s.withColor(Palette.TEXT));
        Component type = ItemTypes.typeName(piece.stack());
        Component state = Component.translatable(worn ? "setcard.kpitemsrarity.worn" : "setcard.kpitemsrarity.missing");
        List<SetLayout.Piece> others = openSet != null ? SetLayout.of(openSet).alternatives(piece.id()) : List.of();
        Component choice = others.isEmpty() ? null : Component.translatable("tooltip.kpitemsrarity.rpg.set_choice", SetLayout.names(others));

        int w = Math.max(Math.max(font.width(name), font.width(type) + 10 + font.width(state)), choice == null ? 0 : font.width(choice)) + 12;
        int h = choice == null ? 28 : 39;
        int x = Math.min(mouseX + 10, g.guiWidth() - w - 4), y = Math.max(4, mouseY - h - 4);
        g.pose().pushPose();
        g.pose().translate(0, 0, PIECE_Z);
        RpgFrame.draw(g, x, y, w, h, 0, FrameStyle.plain(worn ? Palette.POSITIVE : Palette.TEXT_DIM));
        g.drawString(font, name, x + 6, y + 5, 0xFFFFFF, true);
        g.drawString(font, type, x + 6, y + 16, Palette.TEXT_DIM, true);
        g.drawString(font, state, x + w - 6 - font.width(state), y + 16, worn ? Palette.POSITIVE : Palette.TEXT_FAINT, true);
        if (choice != null) g.drawString(font, choice, x + 6, y + 27, Palette.TEXT_DIM, true);
        g.pose().popPose();
    }
}
