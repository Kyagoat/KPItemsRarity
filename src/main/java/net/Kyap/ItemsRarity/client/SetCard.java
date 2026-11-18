package net.Kyap.ItemsRarity.client;

import net.Kyap.ItemsRarity.ItemsRarity;
import net.Kyap.ItemsRarity.data.ItemDefinition;
import net.Kyap.ItemsRarity.data.RarityData;
import net.Kyap.ItemsRarity.data.SetDefinition;
import net.Kyap.ItemsRarity.data.StatDefinition;
import net.Kyap.ItemsRarity.util.StatEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Carte de panoplie. Maintenir Alt gauche sur une pièce :
 *  - la carte REMPLACE l'infobulle (plus rien ne se superpose) ;
 *  - elle reste FIGÉE à sa place tant qu'Alt est maintenu : on peut bouger la souris dessus
 *    et survoler les icônes des pièces pour voir leur nom et si on la porte.
 * Relâcher Alt ferme la carte.
 *
 *   ┌───────────────────────────────────────┐
 *   │ PANOPLIE DE LA GARDE DE FER       2/5 │
 *   │ Palier 2 · 5 pièces                   │
 *   │ [▣][▣][□][□][□]                       │  pièces portées encadrées, manquantes grisées
 *   ├──────────────────◆────────────────────┤
 *   │ ✔ 2 pièces   +2 Endurance             │
 *   │ ▸ 3 pièces   +1 Vie · +5 Endurance    │  palier actif
 *   │   4 pièces   ...                      │  pas encore atteint
 *   │       Survole une pièce pour son nom  │
 *   └───────────────────────────────────────┘
 */
@EventBusSubscriber(modid = ItemsRarity.MOD_ID, value = Dist.CLIENT)
public final class SetCard {
    private SetCard() {}

    private static final int PAD = 7;
    private static final int MAX_TEXT = 176;
    private static final int SLOT = 20; // case de 18 px : l'objet (16 px) tient dans le cadre
    private static final long CYCLE_MS = 1000; // une pièce "au choix" par seconde

    private static String openSet;
    private static int anchorX, anchorY;
    private static final List<int[]> iconRects = new ArrayList<>();
    private static final List<String> iconIds = new ArrayList<>();

    /**
     * Nombre de pièces portables en même temps, affiché "n/total" : deux pièces qui prennent la même case
     * (épée et hache, deux casques...) n'en comptent qu'une ; les anneaux ont deux cases.
     */
    public static int pieceCount(String setId) {
        Map<String, Integer> perKind = new java.util.HashMap<>();
        for (String id : RarityData.setPieces(setId)) perKind.merge(slotKind(id), 1, Integer::sum);
        int total = 0;
        for (var e : perKind.entrySet()) total += Math.min(e.getValue(), capacity(e.getKey()));
        return total;
    }

    /** Case occupée par une pièce : type d'armure, main droite, main gauche ou case d'accessoire. */
    static String slotKind(String id) {
        ResourceLocation loc = ResourceLocation.tryParse(id);
        if (loc == null) return id;
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(loc));
        if (stack.getItem() instanceof net.minecraft.world.item.ArmorItem armor) return armor.getType().getName();
        ItemDefinition def = RarityData.itemDef(stack);
        if (def != null && def.slot != null && StatEngine.ACCESSORY_SLOTS.contains(def.slot)) return def.slot;
        return StatEngine.slotGroupFor(stack) == net.minecraft.world.entity.EquipmentSlotGroup.OFFHAND ? "offhand" : "mainhand";
    }

    /** Nombre de cases de ce type (deux anneaux, une case pour tout le reste). */
    static int capacity(String kind) {
        return "ring".equals(kind) ? 2 : 1;
    }

    /**
     * Pièces de la même panoplie qui se disputent la même case que celle-ci (sans elle) : on n'en porte
     * qu'une à la fois. Vide si la pièce a sa case pour elle seule.
     */
    public static List<String> alternatives(String setId, String id) {
        String kind = slotKind(id);
        List<String> same = new ArrayList<>();
        for (String other : sortedPieces(setId)) if (slotKind(other).equals(kind)) same.add(other);
        if (same.size() <= capacity(kind)) return List.of();
        same.remove(id);
        return same;
    }

    /**
     * Cases de la carte : chaque pièce seule, sauf les pièces au choix d'un emplacement unique
     * (épée / hache, deux casques...) réunies dans une même case. Les anneaux (deux cases) restent séparés.
     */
    static List<List<String>> slotGroups(String setId, List<String> pieceIds) {
        List<List<String>> groups = new ArrayList<>();
        Map<String, List<String>> shared = new java.util.HashMap<>();
        for (String id : pieceIds) {
            String kind = slotKind(id);
            if (capacity(kind) == 1 && !alternatives(setId, id).isEmpty()) {
                List<String> group = shared.get(kind);
                if (group == null) {
                    group = new ArrayList<>();
                    shared.put(kind, group);
                    groups.add(group);
                }
                group.add(id);
            } else {
                groups.add(new ArrayList<>(List.of(id)));
            }
        }
        return groups;
    }

    /** Noms des pièces séparés par " / " (pour "au choix avec ..."). */
    public static Component names(List<String> ids) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < ids.size(); i++) {
            ResourceLocation loc = ResourceLocation.tryParse(ids.get(i));
            if (loc == null) continue;
            if (i > 0) out.append(" / ");
            out.append(new ItemStack(BuiltInRegistries.ITEM.get(loc)).getHoverName());
        }
        return out;
    }

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
            render(g, openSet, set);
            hoverPiece(g, event.getMouseX(), event.getMouseY());
        } finally {
            g.pose().popPose();
        }
    }

    @SubscribeEvent
    public static void onClose(ScreenEvent.Closing event) {
        openSet = null;
    }

    // ---------------------------------------------------------------- Défilement

    private static int scroll, scrollMax;

    /** Molette pendant que la carte est figée : fait défiler les bonus (l'en-tête reste). */
    @SubscribeEvent
    public static void onScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (!isOpen()) return;
        if (scrollMax > 0) scroll = Math.max(0, Math.min(scrollMax, scroll - (int) Math.signum(event.getScrollDeltaY()) * 22));
        event.setCanceled(true);
    }

    // ---------------------------------------------------------------- Rendu

    /** Une ligne du corps : titre de palier (avec trait), bonus, texte de lore, ou espace. */
    private record Entry(int kind, Component text, int color, FormattedCharSequence line) {
        static final int TITLE = 0, ROW = 1, TEXT = 2, GAP = 3;

        Entry(int kind, Component text, int color) {
            this(kind, text, color, null);
        }

        int height() {
            return switch (kind) { case TITLE -> 12; case ROW -> 11; case TEXT -> 10; default -> 3; };
        }
    }

    private static final int LINE_GUTTER = 7;

    /**
     * Même mise en page que les infobulles d'objet : mêmes marges, tout aligné à gauche au même endroit,
     * hauteur limitée (les bonus défilent à la molette), lore tout en bas.
     */
    private static void render(GuiGraphics g, String setId, SetDefinition set) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        Player player = mc.player;

        List<String> pieceIds = sortedPieces(setId);
        Set<String> worn = wornIds(player);
        int wornCount = player != null ? StatEngine.countSetPieces(player).getOrDefault(setId, 0) : 0;
        int total = pieceCount(setId);
        int tier = tierOf(pieceIds);
        int accent = RpgFrame.tierColor(tier);

        Component title = set.name().withStyle(Style.EMPTY.withBold(true).withColor(TextColor.fromRgb(accent)));
        Component count = Component.literal(wornCount + "/" + total);
        Component subtitle = subtitle(pieceIds, total);
        Component lore = LoreText.set(set);

        // ---- Corps : chaque palier = un petit titre, puis ses bonus un par ligne, alignés à gauche
        List<Entry> body = new ArrayList<>();
        var thresholds = set.thresholds();
        var active = thresholds.floorEntry(wornCount);
        boolean first = true;
        for (var entry : thresholds.entrySet()) {
            int n = entry.getKey();
            boolean reached = n <= wornCount;
            boolean isActive = active != null && active.getKey() == n;
            if (!first) body.add(new Entry(Entry.GAP, Component.empty(), 0));
            first = false;
            String mark = isActive ? "▸ " : reached ? "✔ " : "";
            int titleColor = isActive ? RpgFrame.POSITIVE : reached ? RpgFrame.TEXT_SOFT : RpgFrame.TEXT_FAINT;
            body.add(new Entry(Entry.TITLE, Component.literal(mark).append(label(n)), titleColor));
            // Bonus d'abord, puis la contrepartie (malus, en rouge) de la panoplie complète
            List<Map.Entry<String, Integer>> lines = new ArrayList<>(entry.getValue().entrySet());
            lines.sort(java.util.Comparator.comparingInt(b -> b.getValue() < 0 ? 1 : 0));
            for (Map.Entry<String, Integer> b : lines) {
                StatDefinition stat = RarityData.stat(b.getKey());
                if (stat == null) continue;
                int value = b.getValue();
                int valueColor = !reached ? RpgFrame.TEXT_FAINT : value < 0 ? RpgFrame.NEGATIVE : RpgFrame.POSITIVE;
                int nameColor = reached ? RpgFrame.TEXT_SOFT : RpgFrame.TEXT_FAINT;
                body.add(new Entry(Entry.ROW, Component.literal(stat.formatValue(value, true) + " ").withStyle(st -> st.withColor(valueColor))
                        .append(stat.name().withStyle(st -> st.withColor(nameColor))), 0xFFFFFF));
            }
        }

        // ---- Largeur : comme les infobulles (minimum commun, élargie si besoin)
        int contentW = Math.max(font.width(title) + 14 + font.width(count), Math.max(font.width(subtitle) + 10 + font.width(RpgFrame.tierPips(tier)), Math.min(slotGroups(setId, pieceIds).size(), 8) * SLOT));
        for (Entry e : body) contentW = Math.max(contentW, font.width(e.text()) + (e.kind() == Entry.TITLE ? 20 : 0) + LINE_GUTTER);
        int width = Math.min(net.Kyap.ItemsRarity.ClientConfig.tooltipMaxWidth(),
                Math.max(net.Kyap.ItemsRarity.ClientConfig.tooltipWidth(), contentW + 2 * PAD));
        int inner = width - 2 * PAD;

        if (lore != null) {
            body.add(new Entry(Entry.GAP, Component.empty(), 0));
            body.add(new Entry(Entry.GAP, Component.empty(), -1)); // trait de séparation
            for (FormattedCharSequence line : font.split(lore, inner - LINE_GUTTER)) {
                body.add(new Entry(Entry.TEXT, Component.empty(), RpgFrame.TEXT_DIM, line));
            }
        }

        int headerH = PAD + 11 + 10 + 3 + SLOT + 3 + PAD - 4;
        int bodyH = 0;
        for (Entry e : body) bodyH += e.height();
        int screenH = g.guiHeight(), screenW = g.guiWidth();
        int view = Math.min(bodyH, Math.max(30, Math.min(net.Kyap.ItemsRarity.ClientConfig.maxBodyHeight(), screenH - 8 - headerH - 10)));
        scrollMax = Math.max(0, bodyH - view);
        scroll = Math.min(scroll, scrollMax);
        boolean scrolling = scrollMax > 0;
        int height = headerH + 6 + view + PAD - 2;

        // Position figée, gardée dans l'écran
        int x = anchorX + 12, y = anchorY - 12;
        if (x + width + 4 > screenW) x = Math.max(4, anchorX - 16 - width);
        if (y + height + 4 > screenH) y = screenH - height - 4;
        y = Math.max(4, y);

        g.pose().translate(0, 0, 450);
        RpgFrame.draw(g, x, y, width, height, headerH, accent, tier >= 5, RpgFrame.ornateLevelForTier(tier));

        int left = x + PAD, right = x + width - PAD;
        int cy = y + PAD;
        g.drawString(font, title, left, cy, 0xFFFFFF, true);
        g.drawString(font, count, right - font.width(count), cy, wornCount > 0 ? RpgFrame.POSITIVE : RpgFrame.TEXT_DIM, true);
        cy += 11;
        g.drawString(font, subtitle, left, cy, RpgFrame.TEXT_DIM, true);
        Component pips = RpgFrame.tierPips(tier);
        g.drawString(font, pips, right - font.width(pips), cy, 0xFFFFFF, true);
        cy += 13;

        // Pièces : une case par emplacement. Les pièces "au choix" (épée / hache...) partagent une case
        // qui les fait défiler, avec un petit point par pièce en bas de la case.
        iconRects.clear();
        iconIds.clear();
        int ix = left;
        for (List<String> group : slotGroups(setId, pieceIds)) {
            if (ix + SLOT > right + 2) break;
            String id = group.size() == 1 ? group.get(0) : group.get((int) ((net.minecraft.Util.getMillis() / CYCLE_MS) % group.size()));
            ResourceLocation loc = ResourceLocation.tryParse(id);
            if (loc == null) continue;
            boolean has = false;
            for (String other : group) has |= worn.contains(other);
            g.fill(ix, cy, ix + SLOT - 2, cy + SLOT - 2, has ? RpgFrame.argb(accent, 0x30) : 0x50000000);
            int edge = has ? RpgFrame.argb(accent, 0xC0) : RpgFrame.argb(RpgFrame.TEXT_FAINT, 0x80);
            g.fill(ix, cy, ix + SLOT - 2, cy + 1, edge);
            g.fill(ix, cy + SLOT - 3, ix + SLOT - 2, cy + SLOT - 2, edge);
            g.fill(ix, cy, ix + 1, cy + SLOT - 2, edge);
            g.fill(ix + SLOT - 3, cy, ix + SLOT - 2, cy + SLOT - 2, edge);
            g.renderFakeItem(new ItemStack(BuiltInRegistries.ITEM.get(loc)), ix + 1, cy + 1);
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            if (!worn.contains(id)) g.fill(ix + 1, cy + 1, ix + SLOT - 3, cy + SLOT - 3, has ? 0x70101420 : 0xA0101420);
            if (group.size() > 1) {
                // Points : celui de la pièce affichée en clair, celui de la pièce portée en vert
                int dotsW = group.size() * 3 - 1;
                int dx = ix + (SLOT - 2 - dotsW) / 2, dy = cy + SLOT - 6;
                g.fill(dx - 1, dy - 1, dx + dotsW + 1, dy + 3, 0xC0101420);
                for (String other : group) {
                    int color = other.equals(id) ? RpgFrame.argb(accent, 0xFF)
                            : worn.contains(other) ? RpgFrame.argb(RpgFrame.POSITIVE, 0xC0)
                            : RpgFrame.argb(RpgFrame.TEXT_FAINT, 0xA0);
                    g.fill(dx, dy, dx + 2, dy + 2, color);
                    dx += 3;
                }
            }
            g.pose().popPose();
            iconRects.add(new int[]{ix, cy, ix + SLOT - 2, cy + SLOT - 2});
            iconIds.add(id);
            ix += SLOT;
        }

        // Corps (découpé, défile)
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        int bodyTop = y + headerH + 6;
        int textRight = right - (scrolling ? LINE_GUTTER : 0);
        if (scrolling) g.enableScissor(x + 1, bodyTop - 1, x + width - 1, bodyTop + view + 1);
        cy = bodyTop - scroll;
        for (Entry e : body) {
            switch (e.kind()) {
                case Entry.TITLE -> RpgFrame.sectionTitle(g, font, e.text(), left, cy, textRight - left, e.color());
                case Entry.ROW -> g.drawString(font, e.text(), left, cy, 0xFFFFFF, true);
                case Entry.TEXT -> g.drawString(font, e.line(), left, cy, e.color(), true);
                default -> {
                    if (e.color() == -1) g.fill(left, cy + 1, textRight, cy + 2, RpgFrame.argb(accent, 0x40));
                }
            }
            cy += e.height();
        }
        if (scrolling) {
            g.disableScissor();
            RpgFrame.scrollChrome(g, font, x, width, right, bodyTop, view, bodyH, scroll, scrollMax, accent);
        }
        g.pose().popPose();
    }

    /** Petite infobulle sur l'icône survolée : nom de la pièce, portée ou non. */
    private static void hoverPiece(GuiGraphics g, int mouseX, int mouseY) {
        for (int i = 0; i < iconRects.size(); i++) {
            int[] r = iconRects.get(i);
            if (mouseX < r[0] || mouseX >= r[2] || mouseY < r[1] || mouseY >= r[3]) continue;
            ResourceLocation loc = ResourceLocation.tryParse(iconIds.get(i));
            if (loc == null) return;
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(loc));
            Font font = Minecraft.getInstance().font;
            boolean has = wornIds(Minecraft.getInstance().player).contains(iconIds.get(i));
            Component name = stack.getHoverName().copy().withStyle(s -> s.withColor(RpgFrame.TEXT));
            Component type = RpgTooltip.typeName(stack);
            Component state = Component.translatable(has ? "setcard.itemsrarity.worn" : "setcard.itemsrarity.missing");
            List<String> others = openSet != null ? alternatives(openSet, iconIds.get(i)) : List.of();
            Component choice = others.isEmpty() ? null
                    : Component.translatable("tooltip.itemsrarity.rpg.set_choice", names(others));
            int w = Math.max(Math.max(font.width(name), font.width(type) + 10 + font.width(state)), choice == null ? 0 : font.width(choice)) + 12;
            int h = choice == null ? 28 : 39;
            int x = Math.min(mouseX + 10, g.guiWidth() - w - 4), y = Math.max(4, mouseY - h - 4);
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            RpgFrame.draw(g, x, y, w, h, 0, has ? RpgFrame.POSITIVE : RpgFrame.TEXT_DIM, false);
            g.drawString(font, name, x + 6, y + 5, 0xFFFFFF, true);
            g.drawString(font, type, x + 6, y + 16, RpgFrame.TEXT_DIM, true);
            g.drawString(font, state, x + w - 6 - font.width(state), y + 16, has ? RpgFrame.POSITIVE : RpgFrame.TEXT_FAINT, true);
            if (choice != null) g.drawString(font, choice, x + 6, y + 27, RpgFrame.TEXT_DIM, true);
            g.pose().popPose();
            return;
        }
    }

    private static Set<String> wornIds(Player player) {
        Set<String> worn = new HashSet<>();
        if (player == null) return worn;
        for (EquipmentSlot slot : StatEngine.GEAR_SLOTS) {
            ItemStack s = player.getItemBySlot(slot);
            if (StatEngine.isWornCorrectly(s, slot)) worn.add(StatEngine.itemId(s));
        }
        return worn;
    }

    /**
     * Pièces dans un ordre logique : armes (corps à corps, puis arcs), main gauche, accessoires,
     * puis bottes, jambières, plastron, casque. Une panoplie sans casque garde le même ordre.
     */
    static List<String> sortedPieces(String setId) {
        List<String> ids = new ArrayList<>(RarityData.setPieces(setId));
        // Les pièces qui prennent la même case restent côte à côte (épée / hache / arc...)
        Map<String, Integer> kindRank = new java.util.HashMap<>();
        for (String id : ids) kindRank.merge(slotKind(id), pieceRank(id), Math::min);
        ids.sort(java.util.Comparator.<String>comparingInt(id -> kindRank.get(slotKind(id)))
                .thenComparing(SetCard::slotKind)
                .thenComparingInt(SetCard::pieceRank));
        return ids;
    }

    private static int pieceRank(String id) {
        ResourceLocation loc = ResourceLocation.tryParse(id);
        if (loc == null) return 99;
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(loc));
        var item = stack.getItem();
        if (item instanceof net.minecraft.world.item.ArmorItem armor) {
            return switch (armor.getType()) {
                case BOOTS -> 10;
                case LEGGINGS -> 11;
                case CHESTPLATE -> 12;
                case HELMET -> 13;
                default -> 14;
            };
        }
        if (item instanceof net.minecraft.world.item.BowItem || item instanceof net.minecraft.world.item.CrossbowItem) return 1;
        if (item instanceof net.minecraft.world.item.ShieldItem || stack.canPerformAction(net.neoforged.neoforge.common.ItemAbilities.SHIELD_BLOCK)) return 2;
        ItemDefinition def = RarityData.itemDef(stack);
        if (def != null && def.slot != null && !def.slot.isBlank()) {
            return switch (def.slot) {
                case "amulet" -> 3;
                case "ring" -> 4;
                case "relic" -> 5;
                default -> 6;
            };
        }
        if (item instanceof net.minecraft.world.item.SwordItem || item instanceof net.minecraft.world.item.AxeItem
                || item instanceof net.minecraft.world.item.MaceItem || item instanceof net.minecraft.world.item.TridentItem) return 0;
        return 7; // outils et autres
    }

    /** Palier d'une panoplie (utilisé aussi par la ligne "panoplie" des infobulles). */
    public static int tierOfSet(String setId) {
        return tierOf(sortedPieces(setId));
    }

    /** Palier de la panoplie = palier de ses pièces (le plus haut). */
    private static int tierOf(List<String> pieceIds) {
        int tier = 1;
        for (String id : pieceIds) {
            ResourceLocation loc = ResourceLocation.tryParse(id);
            if (loc == null) continue;
            ItemDefinition def = RarityData.itemDef(new ItemStack(BuiltInRegistries.ITEM.get(loc)));
            if (def != null) tier = Math.max(tier, def.tier);
        }
        return tier;
    }

    private static Component label(int pieces) {
        return Component.translatable("setcard.itemsrarity.pieces", pieces);
    }

    private static Component subtitle(List<String> pieceIds, int total) {
        for (String id : pieceIds) {
            ResourceLocation loc = ResourceLocation.tryParse(id);
            if (loc == null) continue;
            ItemDefinition def = RarityData.itemDef(new ItemStack(BuiltInRegistries.ITEM.get(loc)));
            if (def == null) continue;
            return Component.translatable("tooltip.itemsrarity.tier", def.tier)
                    .append(" · ")
                    .append(Component.translatable("setcard.itemsrarity.pieces", total));
        }
        return Component.translatable("setcard.itemsrarity.pieces", total);
    }
}
