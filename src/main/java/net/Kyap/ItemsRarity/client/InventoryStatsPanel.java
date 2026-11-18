package net.Kyap.ItemsRarity.client;

import net.Kyap.ItemsRarity.ItemsRarity;
import net.Kyap.ItemsRarity.data.RarityData;
import net.Kyap.ItemsRarity.data.StatDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fiche de personnage directement sur l'inventaire (E).
 * Un petit bouton à côté du livre de recettes l'ouvre / la ferme ; elle s'affiche à droite de
 * l'inventaire (à gauche si l'écran est trop étroit). Bandeau Vie / Armure / Dégâts, puis les
 * caractéristiques du personnage par famille.
 *
 * Textures (remplaçables par un pack de ressources) :
 *   assets/itemsrarity/textures/gui/sprites/stats/panel.png               (nine-slice, voir .mcmeta)
 *   assets/itemsrarity/textures/gui/sprites/stats/button_small.png / button_small_highlighted.png  (10x10)
 */
@EventBusSubscriber(modid = ItemsRarity.MOD_ID, value = Dist.CLIENT)
public final class InventoryStatsPanel {
    private InventoryStatsPanel() {}

    public static final ResourceLocation PANEL = ItemsRarity.id("stats/panel");
    public static final WidgetSprites BUTTON = new WidgetSprites(
            ItemsRarity.id("stats/button_small"), ItemsRarity.id("stats/button_small_highlighted"));
    /** Petit bouton en haut à droite du cadre du personnage (là où Apothic Attributes mettait le sien). */
    private static final int BUTTON_X = 63, BUTTON_Y = 9, BUTTON_SIZE = 10;

    private static final int WIDTH = 132;
    private static final int GAP = 2;
    private static final int PAD = 7;
    private static final int ROW = 10;
    private static final int TEXT = 0x404040;
    private static final int SUBTLE = 0x7A7A7A;

    /** Ouvert par défaut ; mémorisé pour la session. */
    private static boolean open = true;
    private static double scroll = 0;
    private static ImageButton button;

    /**
     * Écrans d'autres mods qui veulent la fiche, avec le personnage à afficher (ex. KP PNJ : l'équipement d'un
     * villageois). Appelé par réflexion, sans dépendance : registerScreen(Class, Function<Screen, LivingEntity>).
     */
    private static final Map<Class<?>, java.util.function.Function<net.minecraft.client.gui.screens.Screen, LivingEntity>> EXTRA =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static void registerScreen(Class<?> screen, java.util.function.Function<net.minecraft.client.gui.screens.Screen, LivingEntity> subject) {
        EXTRA.put(screen, subject);
    }

    /** Le personnage dont la fiche est affichée : le joueur, ou celui d'un écran enregistré. */
    private static LivingEntity subject(net.minecraft.client.gui.screens.Screen screen) {
        var f = EXTRA.get(screen.getClass());
        return f != null ? f.apply(screen) : Minecraft.getInstance().player;
    }

    // ---------------------------------------------------------------- Bouton

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen) && !EXTRA.containsKey(event.getScreen().getClass())) return;
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) event.getScreen();
        button = new ImageButton(screen.getGuiLeft() + BUTTON_X, screen.getGuiTop() + BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE, BUTTON, b -> {
            open = !open;
            scroll = 0;
        });
        button.setTooltip(Tooltip.create(Component.translatable("panel.itemsrarity.toggle")));
        event.addListener(button);
    }

    /** Le livre de recettes décale l'inventaire : on suit. */
    @SubscribeEvent
    public static void onRenderPre(ScreenEvent.Render.Pre event) {
        if ((event.getScreen() instanceof InventoryScreen || EXTRA.containsKey(event.getScreen().getClass()))
                && event.getScreen() instanceof AbstractContainerScreen<?> screen && button != null) {
            button.setPosition(screen.getGuiLeft() + BUTTON_X, screen.getGuiTop() + BUTTON_Y);
        }
    }

    // ---------------------------------------------------------------- Placement

    /**
     * Écrans où la fiche s'affiche : l'inventaire de survie, l'inventaire créatif et l'inventaire
     * Curios (on y voit ses stats changer en direct en équipant ses accessoires). Curios n'est pas
     * une dépendance : on reconnaît son écran par le nom de sa classe.
     */
    private static AbstractContainerScreen<?> panelScreen(net.minecraft.client.gui.screens.Screen screen) {
        return screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen || isCuriosScreen(screen)
                || EXTRA.containsKey(screen.getClass()) ? (AbstractContainerScreen<?>) screen : null;
    }

    private static final String CURIOS_SCREEN = "top.theillusivec4.curios.client.gui.CuriosScreen";

    private static boolean isCuriosScreen(net.minecraft.client.gui.screens.Screen screen) {
        return screen instanceof AbstractContainerScreen<?> && screen.getClass().getName().equals(CURIOS_SCREEN);
    }

    /** x du panneau (à droite de l'inventaire, sinon à gauche), ou Integer.MIN_VALUE s'il n'y a pas la place. */
    private static int panelX(AbstractContainerScreen<?> screen) {
        int right = screen.getGuiLeft() + screen.getXSize() + GAP;
        int left = screen.getGuiLeft() - GAP - WIDTH;
        if (right + WIDTH <= screen.width) return right;
        boolean book = screen instanceof InventoryScreen inv && inv.getRecipeBookComponent().isVisible();
        if (!book && left >= 0) return left;
        return Integer.MIN_VALUE;
    }

    private static boolean isOnRight(AbstractContainerScreen<?> screen, int x) {
        return x > screen.getGuiLeft();
    }

    private static boolean isInside(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        if (!open) return false;
        int x = panelX(screen);
        if (x == Integer.MIN_VALUE) return false;
        int y = screen.getGuiTop();
        return mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + screen.getYSize();
    }

    /** Les effets de potion s'affichent à droite de l'inventaire : on les pousse après le panneau. */
    @SubscribeEvent
    public static void onMobEffects(ScreenEvent.RenderInventoryMobEffects event) {
        if (!open || !(panelScreen(event.getScreen()) instanceof AbstractContainerScreen<?> screen)) return;
        int x = panelX(screen);
        if (x == Integer.MIN_VALUE || !isOnRight(screen, x)) return;
        int newOffset = x + WIDTH + GAP;
        int space = screen.width - newOffset;
        if (space < 32) return; // pas la place : tant pis, on laisse
        event.setHorizontalOffset(newOffset);
        event.setCompact(space < 120);
    }

    // ---------------------------------------------------------------- Souris

    @SubscribeEvent
    public static void onClick(ScreenEvent.MouseButtonPressed.Pre event) {
        // Sinon un clic sur le panneau compte comme "en dehors de l'inventaire" et jette l'objet tenu.
        if (panelScreen(event.getScreen()) instanceof AbstractContainerScreen<?> screen && isInside(screen, event.getMouseX(), event.getMouseY())) {
            int[] r = toggleRect;
            if (r != null && event.getMouseX() >= r[0] && event.getMouseX() < r[2] && event.getMouseY() >= r[1] && event.getMouseY() < r[3]) {
                showAll = !showAll;
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRelease(ScreenEvent.MouseButtonReleased.Pre event) {
        if (panelScreen(event.getScreen()) instanceof AbstractContainerScreen<?> screen && isInside(screen, event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (panelScreen(event.getScreen()) instanceof AbstractContainerScreen<?> screen && isInside(screen, event.getMouseX(), event.getMouseY())) {
            scroll -= event.getScrollDeltaY() * ROW * 2;
            event.setCanceled(true);
        }
    }

    // ---------------------------------------------------------------- Rendu

    private record Row(Component label, String value, int color, boolean header, List<Component> details) {
        Row(Component label, String value, int color, boolean header) {
            this(label, value, color, header, List.of());
        }

        boolean zero() {
            return !header && color == ZERO;
        }

        boolean malus() {
            return !header && color == MALUS;
        }
    }

    /** Couleur des stats à 0 : affichées quand même, pour montrer qu'on peut en obtenir. */
    private static final int ZERO = 0xA8A8A8;
    /** Ligne survolée (pour son détail), mise à jour à chaque image. */
    private static List<Component> hoverDetails = List.of();

    private static final ResourceLocation HEART = ResourceLocation.withDefaultNamespace("hud/heart/full");
    private static final ResourceLocation ARMOR = ResourceLocation.withDefaultNamespace("hud/armor_full");
    private static final ItemStack SWORD = new ItemStack(Items.IRON_SWORD);

    @SubscribeEvent
    public static void onRender(ContainerScreenEvent.Render.Background event) {
        if (!open || !(panelScreen(event.getContainerScreen()) instanceof AbstractContainerScreen<?> screen)) return;
        LivingEntity player = subject(screen);
        if (player == null) return;
        int x = panelX(screen);
        if (x == Integer.MIN_VALUE) return;

        GuiGraphics g = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        int y = screen.getGuiTop();
        int h = screen.getYSize();
        int left = x + PAD;
        int innerRight = x + WIDTH - PAD;

        g.blitSprite(PANEL, x, y, WIDTH, h);

        // Titre
        Component title = Component.translatable("panel.itemsrarity.title");
        g.drawString(font, title, x + (WIDTH - font.width(title)) / 2, y + 6, TEXT, false);

        // Bandeau : Vie, Armure, Dégâts (valeurs totales du personnage)
        int bandY = y + 18;
        g.fill(left - 1, bandY, innerRight + 1, bandY + 14, 0xFF8B8B8B);
        g.fill(left, bandY + 1, innerRight + 1, bandY + 14, 0xFFFFFFFF);
        g.fill(left, bandY + 1, innerRight, bandY + 13, 0xFFB5B5B5);
        int third = (innerRight - left) / 3;
        drawBig(g, font, left + 2, bandY + 3, null, HEART, number(player.getMaxHealth()));
        drawBig(g, font, left + third + 2, bandY + 3, null, ARMOR, number(player.getArmorValue()));
        drawBig(g, font, left + 2 * third + 2, bandY + 3, SWORD, null, number(player.getAttributeValue(Attributes.ATTACK_DAMAGE)));
        if (isHovering(left, bandY, third, 14)) hoverKey = "panel.itemsrarity.health";
        else if (isHovering(left + third, bandY, third, 14)) hoverKey = "panel.itemsrarity.armor";
        else if (isHovering(left + 2 * third, bandY, third, 14)) hoverKey = "panel.itemsrarity.damage";
        else hoverKey = null;

        // Liste des caractéristiques
        List<Row> rows = buildRows(player);
        int top = bandY + 18;
        int bottom = y + h - PAD;
        int viewHeight = bottom - top;
        int contentHeight = rows.size() * ROW;
        int maxScroll = Math.max(0, contentHeight - viewHeight);
        scroll = Mth.clamp(scroll, 0, maxScroll);

        int right = innerRight - (maxScroll > 0 ? 4 : 0);
        g.enableScissor(left - 1, top, right + 1, bottom);
        int rowY = top - (int) scroll;
        int stripe = 0;
        hoverDetails = List.of();
        toggleRect = null;
        for (Row row : rows) {
            if (row.header()) stripe = 0;
            if (rowY + ROW > top && rowY < bottom) {
                drawRow(g, font, row, left, right, rowY, stripe++ % 2 == 1);
                if (!row.details().isEmpty() && isHovering(left, Math.max(rowY, top), right - left, Math.min(rowY + ROW, bottom) - Math.max(rowY, top))) {
                    hoverDetails = row.details();
                }
            }
            rowY += ROW;
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int arrowX = left + (right - left - font.width("▾")) / 2;
            if (scroll < maxScroll) g.drawString(font, "▾", arrowX, bottom - 3, 0xFF373737, false);
            if (scroll > 0) g.drawString(font, "▴", arrowX, top - 2, 0xFF373737, false);
            int barX = innerRight - 2;
            int barH = Math.max(12, viewHeight * viewHeight / contentHeight);
            int barY = top + (int) ((viewHeight - barH) * (scroll / maxScroll));
            g.fill(barX, top, barX + 2, bottom, 0xFF8B8B8B);
            g.fill(barX, barY, barX + 2, barY + barH, 0xFF373737);
        }
    }

    /** Nom de la case du bandeau sous la souris (infobulle), mis à jour à chaque image. */
    private static String hoverKey;
    private static int lastMouseX, lastMouseY;

    private static boolean isHovering(int x, int y, int w, int h) {
        return lastMouseX >= x && lastMouseX < x + w && lastMouseY >= y && lastMouseY < y + h;
    }

    @SubscribeEvent
    public static void onRenderPost(ScreenEvent.Render.Post event) {
        lastMouseX = event.getMouseX();
        lastMouseY = event.getMouseY();
        if (!open || panelScreen(event.getScreen()) == null || SetCard.isOpen()) return;
        if (hoverKey != null) {
            event.getGuiGraphics().renderTooltip(Minecraft.getInstance().font,
                    Component.translatable(hoverKey), event.getMouseX(), event.getMouseY());
        } else if (!hoverDetails.isEmpty()) {
            renderDetails(event.getGuiGraphics(), Minecraft.getInstance().font, hoverDetails, event.getMouseX(), event.getMouseY());
        }
    }

    private static void drawBig(GuiGraphics g, Font font, int x, int y, ItemStack item, ResourceLocation sprite, String value) {
        if (sprite != null) {
            g.blitSprite(sprite, x, y, 9, 9);
        } else {
            g.pose().pushPose();
            g.pose().translate(x - 0.5f, y - 0.5f, 0);
            g.pose().scale(0.625f, 0.625f, 1f);
            g.renderFakeItem(item, 0, 0);
            g.pose().popPose();
        }
        g.drawString(font, value, x + 12, y + 1, TEXT, false);
    }

    private static void drawRow(GuiGraphics g, Font font, Row row, int left, int right, int y, boolean stripe) {
        if (row.header()) {
            // Famille : icône et nom à sa couleur, filet de la même couleur
            g.drawString(font, row.label(), left, y + 2, row.color(), false);
            g.fill(left + font.width(row.label()) + 3, y + 6, right, y + 7, 0xFF000000 | row.color());
            return;
        }
        if (row.color() == TOGGLE) {
            boolean hover = isHovering(left, y, right - left, ROW);
            Component label = row.label();
            g.drawString(font, label, left + (right - left - font.width(label)) / 2, y + 1, hover ? TEXT : SUBTLE, false);
            toggleRect = new int[]{left, y, right, y + ROW};
            return;
        }
        if (stripe) g.fill(left - 1, y, right + 1, y + ROW, 0x18000000);
        int x = left;
        g.fill(x, y + 1, x + 7, y + 8, row.zero() ? 0xFF9A9A9A : 0xFF373737);
        g.fill(x + 1, y + 2, x + 6, y + 7, 0xFF000000 | row.color());
        x += 10;
        int valueWidth = font.width(row.value());
        int maxLabel = right - x - valueWidth - 3;
        String label = row.label().getString();
        if (font.width(label) > maxLabel) label = font.plainSubstrByWidth(label, maxLabel - font.width("…")) + "…";
        g.drawString(font, label, x, y + 1, row.zero() ? 0x8A8A8A : TEXT, false);
        g.drawString(font, row.value(), right - valueWidth, y + 1, row.zero() ? 0x8A8A8A : row.malus() ? MALUS : darken(row.color()), false);
    }

    // ---------------------------------------------------------------- Contenu

    /** Stats déjà dans le bandeau. */
    private static final java.util.Set<String> IN_BAND = java.util.Set.of("vitality", "resistance");

    /**
     * TOUTES les caractéristiques du personnage, même à 0 (en gris : on voit ce qu'on peut obtenir).
     * Valeur "totale" (base comprise : 5 % de critique, 40 d'endurance, 3 de maîtrise de garde...) ou "bonus"
     * (ce qu'apportent équipement, panoplies, potions et autres mods), selon la stat (StatDefinition.showsTotal).
     * Au survol : détail base / bonus.
     */
    private static List<Row> buildRows(LivingEntity player) {
        SOURCE_LINES.clear();
        Map<String, List<Row>> active = new LinkedHashMap<>(), idle = new LinkedHashMap<>();
        for (String category : CATEGORIES) {
            active.put(category, new ArrayList<>());
            idle.put(category, new ArrayList<>());
        }
        for (StatDefinition stat : RarityData.statsSorted()) {
            if (stat.base || IN_BAND.contains(stat.id)) continue;
            var holder = stat.attributeHolder();
            if (holder.isEmpty()) continue; // mod absent : on n'affiche pas
            AttributeInstance instance = player.getAttribute(holder.get());
            if (instance == null) continue;

            double bonus = 0;
            for (AttributeModifier modifier : instance.getModifiers()) {
                if (modifier.operation() == stat.parsedOperation()) bonus += modifier.amount();
            }
            int bonusPoints = (int) Math.round(bonus / stat.scale);
            boolean total = stat.showsTotal();
            int points = total ? (int) Math.round(instance.getValue() / stat.scale) : bonusPoints;
            int basePoints = (int) Math.round(instance.getBaseValue() / stat.scale);
            int gained = total ? points - basePoints : bonusPoints;

            // Survol : nom, ce que fait la stat (une ligne courte), base, puis QUI la donne
            List<Component> details = new ArrayList<>();
            details.add(stat.name().withStyle(st -> st.withColor(stat.rgb())));
            String descKey = "stat." + stat.namespace + "." + stat.id + ".desc";
            if (net.minecraft.locale.Language.getInstance().has(descKey)) {
                details.add(Component.translatable(descKey).withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC));
            }
            if (total) {
                details.add(Component.translatable("panel.itemsrarity.detail.base", stat.formatValue(basePoints, false))
                        .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            }
            details.addAll(sources(player, stat, instance));

            String category = stat.category.toLowerCase(Locale.ROOT);
            int color = gained == 0 && points == 0 ? ZERO : gained < 0 ? MALUS : stat.rgb();
            Row row = new Row(stat.name(), stat.formatValue(points, !total && points != 0), color, false, details);
            (gained != 0 ? active : idle).computeIfAbsent(category, k -> new ArrayList<>()).add(row);
        }
        if (player instanceof Player p) active.get("combat").add(0, attackRangeRow(p));

        List<Row> rows = new ArrayList<>();
        int hidden = 0;
        for (String category : active.keySet()) {
            List<Row> on = active.get(category), off = idle.getOrDefault(category, List.of());
            if (on.isEmpty() && (off.isEmpty() || !showAll)) {
                hidden += off.size();
                continue;
            }
            // Ce qui bouge d'abord (les plus grosses valeurs en tête), puis le reste en gris si demandé
            rows.add(new Row(Component.literal(CATEGORY_ICONS.getOrDefault(category, "•") + " ")
                    .append(Component.translatable("panel.itemsrarity.category." + category)), "", CATEGORY_COLORS.getOrDefault(category, 0x7A7A7A), true));
            rows.addAll(on);
            if (showAll) rows.addAll(off);
            else hidden += off.size();
        }
        if (hidden > 0 || showAll) {
            rows.add(new Row(showAll ? Component.translatable("panel.itemsrarity.hide_idle")
                    : Component.translatable("panel.itemsrarity.show_idle", hidden), "", TOGGLE, false));
        }
        return rows;
    }

    /** Ordre des familles : ce qui sert le plus souvent en haut. */
    private static final List<String> CATEGORIES = List.of("combat", "defense", "mobility", "harvest", "utility", "magic");
    private static final Map<String, String> CATEGORY_ICONS = Map.of("combat", "⚔", "defense", "⛨", "mobility", "➶",
            "harvest", "⛏", "utility", "✧", "magic", "✦");
    private static final Map<String, Integer> CATEGORY_COLORS = Map.of("combat", 0x9A3B2E, "defense", 0x3B5E9A, "mobility", 0x2E8A5A,
            "harvest", 0x8A6A2E, "utility", 0x6A5A8A, "magic", 0x7A3B9A);
    /** Couleur d'une stat en négatif (malus de panoplie...). */
    private static final int MALUS = 0xC0392B;
    /** Ligne "afficher / masquer les stats inactives". */
    private static final int TOGGLE = -2;
    private static boolean showAll = false;
    private static int[] toggleRect;

    /**
     * D'où vient une stat : chaque pièce portée, chaque panoplie, chaque accessoire, puis le reste
     * (effets de potion, autres mods) regroupé. Une ligne par source, la plus forte en premier.
     */
    private static List<Component> sources(LivingEntity player, StatDefinition stat, AttributeInstance instance) {
        record Source(Component name, int points, List<ItemStack> icon) {}
        List<Source> list = new ArrayList<>();
        double other = 0;
        String statPart = "/" + stat.id;
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (modifier.operation() != stat.parsedOperation()) continue;
            ResourceLocation id = modifier.id();
            int points = (int) Math.round(modifier.amount() / stat.scale);
            String path = id.getPath();
            if (id.getNamespace().equals(ItemsRarity.MOD_ID) && path.startsWith(net.Kyap.ItemsRarity.events.AttributeEvents.STAT_PREFIX + stat.id + "/")) {
                String slot = path.substring(path.lastIndexOf('/') + 1);
                list.add(new Source(slotSource(player, slot, path), points, one(slotStack(player, slot))));
            } else if (id.getNamespace().equals(ItemsRarity.MOD_ID) && path.startsWith(net.Kyap.ItemsRarity.events.SetBonusEvents.SET_PREFIX) && path.endsWith(statPart)) {
                list.add(new Source(setSource(player, path), points, setIcons(player, path)));
            } else if (id.getNamespace().equals(ItemsRarity.MOD_ID) && path.startsWith(net.Kyap.ItemsRarity.events.AttributeEvents.STAT_PREFIX)) {
                // une autre de nos stats sur le même attribut (ex. Force et Dégâts) : comptée avec elle
            } else {
                other += modifier.amount();
            }
        }
        int otherPoints = (int) Math.round(other / stat.scale);
        if (otherPoints != 0) list.add(new Source(Component.translatable("panel.itemsrarity.source.other"), otherPoints, List.of()));
        List<Component> out = new ArrayList<>();
        if (list.isEmpty()) {
            out.add(Component.translatable("panel.itemsrarity.source.none").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            return out;
        }
        list.sort((x, y) -> Integer.compare(Math.abs(y.points()), Math.abs(x.points())));
        for (Source src : list.subList(0, Math.min(8, list.size()))) {
            int color = src.points() < 0 ? 0xFF6B6B : 0x7FE08A;
            Component value = Component.literal(stat.formatValue(src.points(), true)).withStyle(st -> st.withColor(color));
            Component name = src.name().copy().withStyle(net.minecraft.ChatFormatting.GRAY);
            Component line = value.copy().append("  ").append(name);
            SOURCE_LINES.put(line, new SourceLine(value, src.icon(), name));
            out.add(line);
        }
        if (list.size() > 8) out.add(Component.literal("…").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        return out;
    }

    /** Ligne "source" de l'infobulle : valeur, petite icône de l'objet, puis son nom (comme la carte de panoplie). */
    private record SourceLine(Component value, List<ItemStack> icons, Component name) {
        /** Icône affichée : une panoplie fait défiler ses pièces portées (une par seconde). */
        ItemStack icon() {
            if (icons.isEmpty()) return ItemStack.EMPTY;
            return icons.get((int) ((net.minecraft.Util.getMillis() / CYCLE_MS) % icons.size()));
        }
    }
    private static final long CYCLE_MS = 1000;

    private static List<ItemStack> one(ItemStack stack) {
        return stack.isEmpty() ? List.of() : List.of(stack);
    }
    private static final Map<Component, SourceLine> SOURCE_LINES = new java.util.IdentityHashMap<>();
    /** Icône de 10 px dans une petite case de 12 px (le même cadre que les pièces de la carte de panoplie). */
    private static final int ICON = 10, BOX = 12, ICON_LINE = 14;

    private static ItemStack slotStack(LivingEntity player, String slot) {
        for (net.minecraft.world.entity.EquipmentSlotGroup group : net.minecraft.world.entity.EquipmentSlotGroup.values()) {
            if (!group.getSerializedName().equals(slot)) continue;
            for (net.minecraft.world.entity.EquipmentSlot equip : net.minecraft.world.entity.EquipmentSlot.values()) {
                if (group.test(equip) && !player.getItemBySlot(equip).isEmpty()) return player.getItemBySlot(equip);
            }
        }
        return ItemStack.EMPTY;
    }

    /** Icônes d'une panoplie : toutes ses pièces portées (équipement puis accessoires), qui défilent. */
    private static List<ItemStack> setIcons(LivingEntity player, String path) {
        List<ItemStack> worn = new ArrayList<>();
        for (net.minecraft.world.entity.EquipmentSlot equip : net.Kyap.ItemsRarity.util.StatEngine.GEAR_SLOTS) {
            ItemStack stack = player.getItemBySlot(equip);
            if (net.Kyap.ItemsRarity.util.StatEngine.isWornCorrectly(stack, equip)) worn.add(stack);
        }
        worn.addAll(net.Kyap.ItemsRarity.compat.CuriosCompat.equipped(player));
        List<ItemStack> out = new ArrayList<>();
        java.util.Set<net.minecraft.world.item.Item> seen = new java.util.HashSet<>();
        for (ItemStack stack : worn) {
            var def = RarityData.itemDef(stack);
            if (def == null || !def.hasSet() || !seen.add(stack.getItem())) continue;
            String key = net.Kyap.ItemsRarity.events.SetBonusEvents.modifierId(def.set, "x").getPath();
            if (path.startsWith(key.substring(0, key.length() - 1))) out.add(stack);
        }
        return out;
    }

    /**
     * Infobulle d'une stat dessinée à la main (même cadre que les infobulles d'objets) : texte coupé
     * en lignes étroites, et pour chaque source "valeur  [icône] nom".
     */
    private static void renderDetails(GuiGraphics g, Font font, List<Component> details, int mouseX, int mouseY) {
        record Line(List<net.minecraft.util.FormattedCharSequence> text, SourceLine source) {}
        List<Line> lines = new ArrayList<>();
        int w = 0, h = 0;
        for (Component c : details) {
            SourceLine src = SOURCE_LINES.get(c);
            if (src != null) {
                lines.add(new Line(null, src));
                w = Math.max(w, font.width(src.value()) + 4 + (src.icon().isEmpty() ? 0 : BOX + 3) + font.width(src.name()));
                h += src.icon().isEmpty() ? 10 : ICON_LINE;
            } else {
                var split = font.split(c, 170);
                lines.add(new Line(split, null));
                for (var part : split) w = Math.max(w, font.width(part));
                h += split.size() * 10;
            }
        }
        int pad = 5, bw = w + 2 * pad, bh = h + 2 * pad - 1;
        int x = mouseX + 12, y = mouseY - 12;
        if (x + bw > g.guiWidth() - 4) x = Math.max(4, mouseX - 16 - bw);
        if (y + bh > g.guiHeight() - 4) y = g.guiHeight() - 4 - bh;
        y = Math.max(4, y);
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        RpgFrame.draw(g, x, y, bw, bh, 0, RpgFrame.TEXT_FAINT, false);
        int cy = y + pad;
        for (Line line : lines) {
            if (line.source() == null) {
                for (var part : line.text()) {
                    g.drawString(font, part, x + pad, cy, 0xFFFFFF, true);
                    cy += 10;
                }
                continue;
            }
            SourceLine src = line.source();
            boolean icon = !src.icon().isEmpty();
            int ty = cy + (icon ? 3 : 0);
            int tx = x + pad;
            g.drawString(font, src.value(), tx, ty, 0xFFFFFF, true);
            tx += font.width(src.value()) + 4;
            if (icon) {
                // Petite case : fond sombre, liseré à la couleur de la valeur (vert bonus, rouge malus)
                boolean malus = src.value().getString().startsWith("-");
                int edge = RpgFrame.argb(malus ? 0xFF6B6B : 0x7FE08A, 0xB0);
                g.fill(tx, cy, tx + BOX, cy + BOX, malus ? 0x40FF4040 : 0x4040FF80);
                g.fill(tx, cy, tx + BOX, cy + 1, edge);
                g.fill(tx, cy + BOX - 1, tx + BOX, cy + BOX, edge);
                g.fill(tx, cy, tx + 1, cy + BOX, edge);
                g.fill(tx + BOX - 1, cy, tx + BOX, cy + BOX, edge);
                g.pose().pushPose();
                g.pose().translate(tx + 1, cy + 1, 0);
                g.pose().scale(ICON / 16f, ICON / 16f, 1f);
                g.renderFakeItem(src.icon(), 0, 0);
                g.pose().popPose();
                tx += BOX + 3;
            }
            g.drawString(font, src.name(), tx, ty, 0xFFFFFF, true);
            cy += icon ? ICON_LINE : 10;
        }
        g.pose().popPose();
    }

    /** Nom de l'objet porté dans l'emplacement d'où vient un modificateur (casque, main, accessoire Curios...). */
    private static Component slotSource(LivingEntity player, String slot, String path) {
        for (net.minecraft.world.entity.EquipmentSlotGroup group : net.minecraft.world.entity.EquipmentSlotGroup.values()) {
            if (!group.getSerializedName().equals(slot)) continue;
            for (net.minecraft.world.entity.EquipmentSlot equip : net.minecraft.world.entity.EquipmentSlot.values()) {
                if (group.test(equip) && !player.getItemBySlot(equip).isEmpty()) return player.getItemBySlot(equip).getHoverName();
            }
        }
        if (slot.startsWith("curio_") || path.contains("/curio_")) return Component.translatable("panel.itemsrarity.source.accessory");
        return Component.literal(slot);
    }

    private static Component setSource(LivingEntity player, String path) {
        // set/<namespace>_<set>/<stat> : on retrouve la panoplie portée qui correspond
        for (var entry : net.Kyap.ItemsRarity.util.StatEngine.countSetPieces(player).entrySet()) {
            var set = RarityData.set(entry.getKey());
            String key = net.Kyap.ItemsRarity.events.SetBonusEvents.modifierId(entry.getKey(), "x").getPath();
            if (set != null && path.startsWith(key.substring(0, key.length() - 1))) {
                return set.name().copy().append(" (" + entry.getValue() + "/" + SetCard.pieceCount(entry.getKey()) + ")");
            }
        }
        return Component.translatable("panel.itemsrarity.source.set");
    }

    /** Portée d'attaque réelle avec l'arme en main (celle de Better Combat s'il est installé). */
    private static Row attackRangeRow(Player player) {
        double interaction = player.entityInteractionRange();
        double range = betterCombatRange(player);
        if (range <= 0) range = interaction;
        String value = String.format(Locale.ROOT, "%.2f", range).replace('.', ',') + " m";
        List<Component> details = new ArrayList<>();
        details.add(Component.translatable("panel.itemsrarity.attack_range").withStyle(s -> s.withColor(RANGE_COLOR)));
        details.add(Component.translatable("panel.itemsrarity.attack_range.detail", player.getMainHandItem().getHoverName())
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        return new Row(Component.translatable("panel.itemsrarity.attack_range"), value, RANGE_COLOR, false, details);
    }

    private static final int RANGE_COLOR = 0x7FD1C7;
    private static java.lang.reflect.Method bcRange;
    private static boolean bcChecked;

    /** Better Combat n'est pas une dépendance : on l'appelle par réflexion, -1 s'il est absent. */
    private static double betterCombatRange(Player player) {
        if (!bcChecked) {
            bcChecked = true;
            try {
                bcRange = Class.forName("net.bettercombat.logic.PlayerAttackHelper")
                        .getMethod("getRangeForItem", Player.class, net.minecraft.world.item.ItemStack.class);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                bcRange = null;
            }
        }
        if (bcRange == null) return -1;
        try {
            return (double) bcRange.invoke(null, player, player.getMainHandItem());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
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
