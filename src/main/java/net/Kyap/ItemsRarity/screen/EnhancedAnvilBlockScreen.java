package net.Kyap.ItemsRarity.screen;

import net.Kyap.ItemsRarity.ItemsRarity;
import net.Kyap.ItemsRarity.client.RpgFrame;
import net.Kyap.ItemsRarity.component.ItemStats;
import net.Kyap.ItemsRarity.component.ModDataComponents;
import net.Kyap.ItemsRarity.component.RuneData;
import net.Kyap.ItemsRarity.forge.ForgeMagic;
import net.Kyap.ItemsRarity.item.ModItems;
import net.Kyap.ItemsRarity.network.ForgeLogPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Écran de la table de forgemagie : objet en haut, rune / pierre / ressource à gauche,
 * boutons Forger et Briser, chance de réussite et puits de l'objet, journal des résultats à droite.
 */
public class EnhancedAnvilBlockScreen extends AbstractContainerScreen<EnhancedAnvilBlockMenu> {
    private static final ResourceLocation TEXTURE = ItemsRarity.id("textures/gui/enhanced_anvil.png");

    private Button forgeButton, breakButton;

    public EnhancedAnvilBlockScreen(EnhancedAnvilBlockMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        this.inventoryLabelY = 10000;
        this.titleLabelY = 10000;
        ForgeLogPayload.LOG.clear();
        forgeButton = addRenderableWidget(Button.builder(Component.translatable("forge.itemsrarity.button.forge"),
                b -> click(EnhancedAnvilBlockMenu.BUTTON_FORGE)).bounds(leftPos + 56, topPos + 42, 64, 14).build());
        breakButton = addRenderableWidget(Button.builder(Component.translatable("forge.itemsrarity.button.break"),
                b -> click(EnhancedAnvilBlockMenu.BUTTON_BREAK)).bounds(leftPos + 56, topPos + 59, 64, 14).build());
        forgeButton.setTooltip(Tooltip.create(Component.translatable("forge.itemsrarity.button.forge.tip")));
        breakButton.setTooltip(Tooltip.create(Component.translatable("forge.itemsrarity.button.break.tip")));
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ItemStack gear = menu.getGearStack(), rune = menu.getRuneStack();
        forgeButton.active = !gear.isEmpty() && !rune.isEmpty();
        breakButton.active = !gear.isEmpty() || !rune.isEmpty();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        // Fantôme d'une rune dans la case vide, pour montrer quoi y mettre
        if (menu.getRuneStack().isEmpty()) {
            ItemStack ghost = net.Kyap.ItemsRarity.item.custom.RuneItem.of("strength", RuneData.BASE, 1);
            graphics.renderFakeItem(ghost, leftPos + 27, topPos + 47);
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            graphics.fill(leftPos + 27, topPos + 47, leftPos + 43, topPos + 63, 0x90404040);
            graphics.pose().popPose();
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        ItemStack gear = menu.getGearStack(), rune = menu.getRuneStack();
        // Chance et puits, à droite des boutons
        int x = 124, y = 44;
        RuneData data = rune.get(ModDataComponents.RUNE.get());
        if (!gear.isEmpty() && data != null) {
            float chance = ForgeMagic.chance(gear, data);
            Component text = chance < 0 ? Component.translatable("forge.itemsrarity.chance_none").withStyle(ChatFormatting.RED)
                    : Component.translatable("forge.itemsrarity.chance", Math.round(chance * 100))
                    .withStyle(chance >= 0.6f ? ChatFormatting.GREEN : chance >= 0.25f ? ChatFormatting.YELLOW : ChatFormatting.RED);
            graphics.drawString(font, text, x, y, 0xFFFFFF, true);
        } else if (!gear.isEmpty() && (rune.is(ModItems.FLOURITE.get()) || rune.is(ModItems.SULFUR.get()) || rune.is(ModItems.BISMUTH.get()))) {
            int pct = rune.is(ModItems.BISMUTH.get()) ? 90 : rune.is(ModItems.SULFUR.get()) ? 60 : 35;
            graphics.drawString(font, Component.translatable("forge.itemsrarity.chance", pct).withStyle(ChatFormatting.GOLD), x, y, 0xFFFFFF, true);
        }
        ItemStats stats = gear.get(ModDataComponents.STATS.get());
        if (stats != null && stats.well() > 0) {
            graphics.drawString(font, Component.translatable("forge.itemsrarity.well", stats.well()).withStyle(ChatFormatting.AQUA), x, y + 17, 0xFFFFFF, true);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // En 1.21, super.render() dessine déjà le fond flouté : ne PAS rappeler renderBackground()
        super.render(graphics, mouseX, mouseY, partialTick);
        renderLog(graphics);
        renderTooltip(graphics, mouseX, mouseY);
        renderRuneButtonTooltip(graphics, mouseX, mouseY);
    }

    // ---------------------------------------------------------------- Panneau de droite : lignes + journal

    private static final int PANEL_W = 176, ROW_H = 12, BTN_W = 16, BTN_H = 10;
    /** Boutons de rune dessinés à cette image : {x, y, ligne, taille}. */
    private final java.util.List<int[]> runeButtons = new java.util.ArrayList<>();

    private int panelX() {
        int x = leftPos + imageWidth + 4;
        return x + PANEL_W > width - 2 ? Math.max(2, leftPos - 4 - PANEL_W) : x;
    }

    /**
     * Les lignes de l'objet, chacune avec trois boutons (rune, Pa, Ra) qui prennent la rune directement
     * dans l'inventaire : plus besoin de la poser. Maj + clic : recommence jusqu'à ce que la ligne monte.
     * Dessous, le journal des derniers résultats.
     */
    private void renderLog(GuiGraphics g) {
        runeButtons.clear();
        int x = panelX(), y = topPos;
        ItemStack gear = menu.getGearStack();
        ItemStats stats = gear.get(ModDataComponents.STATS.get());
        var def = net.Kyap.ItemsRarity.data.RarityData.itemDef(gear);
        java.util.List<String> ids = stats != null ? new java.util.ArrayList<>(stats.lines().keySet()) : java.util.List.of();
        java.util.List<FormattedCharSequence> logLines = new java.util.ArrayList<>();
        for (Component c : ForgeLogPayload.LOG) logLines.addAll(font.split(c, PANEL_W - 12));
        int rowsH = ids.isEmpty() ? 0 : 14 + ids.size() * ROW_H + 4;
        int logH = logLines.isEmpty() ? 0 : 14 + logLines.size() * 10;
        int h = Math.min(imageHeight, Math.max(30, 6 + rowsH + logH));
        if (ids.isEmpty() && logLines.isEmpty()) {
            logLines.add(Component.translatable("forge.itemsrarity.help").getVisualOrderText());
            h = 30;
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 100);
        RpgFrame.draw(g, x, y, PANEL_W, h, 0, RpgFrame.TEXT_FAINT, false);
        int cy = y + 5;
        if (!ids.isEmpty()) {
            g.drawString(font, Component.translatable("forge.itemsrarity.lines").withStyle(ChatFormatting.GRAY), x + 6, cy, 0xFFFFFF, true);
            cy += 13;
            var inv = minecraft.player.getInventory();
            for (int i = 0; i < ids.size(); i++) {
                String id = ids.get(i);
                var stat = net.Kyap.ItemsRarity.data.RarityData.stat(id);
                if (stat == null) continue;
                int value = stats.get(id);
                int[] r = def != null ? net.Kyap.ItemsRarity.util.StatEngine.range(def, id, stats.upgrades()) : null;
                int color = r != null && value > r[1] ? RpgFrame.OVERMAX : value < 0 ? RpgFrame.NEGATIVE : RpgFrame.POSITIVE;
                Component label = Component.literal(stat.formatValue(value, true) + " ").withStyle(st -> st.withColor(color))
                        .append(stat.name().copy().withStyle(st -> st.withColor(RpgFrame.TEXT_SOFT)));
                int bx0 = x + PANEL_W - 6 - 3 * (BTN_W + 2);
                g.drawString(font, fitText(label, bx0 - x - 8), x + 6, cy + 1, 0xFFFFFF, true);
                if (!stat.base) {
                    for (int size = 0; size < 3; size++) {
                        int bx = bx0 + size * (BTN_W + 2);
                        int count = countRunes(inv, id, EnhancedAnvilBlockMenu.SIZES[size]);
                        boolean on = count > 0;
                        int edge = on ? 0xFF000000 | stat.rgb() : 0xFF3A3F4A;
                        g.fill(bx, cy, bx + BTN_W, cy + BTN_H, on ? 0x80000000 : 0x40000000);
                        g.fill(bx, cy, bx + BTN_W, cy + 1, edge);
                        g.fill(bx, cy + BTN_H - 1, bx + BTN_W, cy + BTN_H, edge);
                        String t = size == 0 ? "+" : size == 1 ? "Pa" : "Ra";
                        g.drawString(font, t, bx + (BTN_W - font.width(t)) / 2, cy + 1, on ? 0xFFFFFF : 0x6A6F7A, false);
                        runeButtons.add(new int[]{bx, cy, i, size, count});
                    }
                }
                cy += ROW_H;
            }
            cy += 4;
        }
        if (!logLines.isEmpty()) {
            if (!ids.isEmpty()) g.fill(x + 4, cy - 3, x + PANEL_W - 4, cy - 2, RpgFrame.argb(RpgFrame.TEXT_FAINT, 0x60));
            for (FormattedCharSequence line : logLines) {
                if (cy + 9 > y + h - 2) break;
                g.drawString(font, line, x + 6, cy, 0xFFFFFF, true);
                cy += 10;
            }
        }
        g.pose().popPose();
    }

    private FormattedCharSequence fitText(Component text, int maxW) {
        if (font.width(text) <= maxW) return text.getVisualOrderText();
        return net.minecraft.locale.Language.getInstance().getVisualOrder(net.minecraft.network.chat.FormattedText.composite(
                font.substrByWidth(text, maxW - font.width("…")), net.minecraft.network.chat.FormattedText.of("…")));
    }

    private static int countRunes(net.minecraft.world.entity.player.Inventory inv, String stat, int power) {
        RuneData wanted = new RuneData(stat, power);
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && wanted.equals(s.get(ModDataComponents.RUNE.get()))) n += s.getCount();
        }
        return n;
    }

    private int[] buttonAt(double mx, double my) {
        for (int[] b : runeButtons) if (mx >= b[0] && mx < b[0] + BTN_W && my >= b[1] && my < b[1] + BTN_H) return b;
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int[] b = buttonAt(mouseX, mouseY);
        if (b != null && button == 0) {
            if (b[4] > 0) {
                click(EnhancedAnvilBlockMenu.lineButton(b[2], b[3], hasShiftDown()));
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Survol d'un bouton de rune : sa rune, combien tu en as, la chance, et l'astuce Maj. */
    private void renderRuneButtonTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int[] b = buttonAt(mouseX, mouseY);
        if (b == null) return;
        ItemStack gear = menu.getGearStack();
        ItemStats stats = gear.get(ModDataComponents.STATS.get());
        if (stats == null) return;
        var ids = new java.util.ArrayList<>(stats.lines().keySet());
        if (b[2] >= ids.size()) return;
        RuneData rune = new RuneData(ids.get(b[2]), EnhancedAnvilBlockMenu.SIZES[b[3]]);
        java.util.List<Component> lines = new java.util.ArrayList<>();
        lines.add(net.Kyap.ItemsRarity.item.custom.RuneItem.of(rune.stat(), rune.power(), 1).getHoverName());
        lines.add(Component.translatable("forge.itemsrarity.in_inventory", b[4]).withStyle(b[4] > 0 ? ChatFormatting.GRAY : ChatFormatting.RED));
        float chance = ForgeMagic.chance(gear, rune);
        if (chance >= 0) lines.add(Component.translatable("forge.itemsrarity.chance", Math.round(chance * 100)).withStyle(ChatFormatting.YELLOW));
        lines.add(Component.translatable("forge.itemsrarity.shift_repeat").withStyle(ChatFormatting.DARK_GRAY));
        g.renderComponentTooltip(font, lines, mouseX, mouseY);
    }
}
