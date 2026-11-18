package net.Kyap.ItemsRarity.client;

import net.Kyap.ItemsRarity.component.ItemStats;
import net.Kyap.ItemsRarity.component.ModDataComponents;
import net.Kyap.ItemsRarity.component.RuneData;
import net.Kyap.ItemsRarity.data.RarityData;
import net.Kyap.ItemsRarity.forge.ForgeMagic;
import net.Kyap.ItemsRarity.item.custom.RuneItem;
import net.Kyap.ItemsRarity.network.ForgeLogPayload;
import net.Kyap.ItemsRarity.screen.EnhancedAnvilBlockMenu;
import net.Kyap.ItemsRarity.util.StatEngine;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Panneau de forgemagie "à la Dofus" au milieu de la scène Cinematic Interactions : une ligne par stat de
 * l'objet (une ligne sur deux en semi-transparent, l'autre transparente), les stats à gauche et, à droite
 * de chaque ligne, trois petites cases de rune (rune, Pa, Ra)
 * avec le nombre que tu as dans ton inventaire. Clic sur une case : le personnage pose la rune sur la
 * table et frappe. Dessous : chance, puits et journal.
 */
public final class ForgeOverlay {
    private ForgeOverlay() {}

    private static final int W = 240, LINE_H = 16, SLOT = 14;
    /** Fond des lignes : une sur deux semi-transparente, l'autre transparente. */
    private static final int ROW_DARK = 0x88080B14, ROW_CLEAR = 0x00000000, HEAD = 0xA0080B14;

    /** Une case de rune cliquable : position écran, ligne de l'objet, taille (0 rune, 1 Pa, 2 Ra), nombre possédé. */
    public record RuneSlot(int x, int y, int line, int size, int count, RuneData rune) {
        public boolean contains(double mx, double my) {
            return mx >= x && mx < x + SLOT && my >= y && my < y + SLOT;
        }
    }

    /** Dessine le panneau. @return les cases de rune (pour les clics). */
    public static List<RuneSlot> render(GuiGraphics g, Font font, ItemStack gear, ItemStack placedRune, Inventory inv,
                                        double mouseX, double mouseY) {
        List<RuneSlot> slots = new ArrayList<>();
        ItemStats stats = gear.get(ModDataComponents.STATS.get());
        var def = RarityData.itemDef(gear);
        List<String> ids = stats != null ? new ArrayList<>(stats.lines().keySet()) : List.of();
        List<FormattedCharSequence> log = new ArrayList<>();
        int kept = 0;
        for (Component c : ForgeLogPayload.LOG) {
            if (kept++ >= 7) break;
            log.addAll(font.split(c, W - 12));
        }
        int linesShown = 0;
        for (String id : ids) {
            var st = RarityData.stat(id);
            if (st != null && !st.base) linesShown++;
        }
        if (stats == null && log.isEmpty()) return slots;

        // Au milieu de l'écran : les stats à gauche, les runes à ajouter à droite
        int x = (g.guiWidth() - W) / 2;
        int h = (stats != null ? 14 + linesShown * LINE_H + 14 : 0) + (log.isEmpty() ? 0 : 6 + log.size() * 10);
        int y = Math.max(6, (g.guiHeight() - h) / 2);
        int cy = y;
        RuneSlot hovered = null;
        int row = 0;
        if (stats != null) {
            g.fill(x, cy, x + W, cy + 14, HEAD);
            g.fill(x, cy + 13, x + W, cy + 14, 0x60C8D0E0);
            g.drawString(font, gear.getHoverName(), x + 6, cy + 3, 0xFFFFFF, true);
            Component runesTitle = Component.translatable("forge.itemsrarity.runes_column").withStyle(ChatFormatting.GRAY);
            g.drawString(font, runesTitle, x + W - 6 - font.width(runesTitle), cy + 3, 0xFFFFFF, true);
            cy += 14;
            for (int i = 0; i < ids.size(); i++) {
                String id = ids.get(i);
                var stat = RarityData.stat(id);
                if (stat == null || stat.base) continue;
                int v = stats.get(id);
                int[] r = def != null ? StatEngine.range(def, id, stats.upgrades()) : null;
                int color = r != null && v > r[1] ? RpgFrame.OVERMAX : v < 0 ? RpgFrame.NEGATIVE : RpgFrame.POSITIVE;
                Component label = Component.literal(stat.formatValue(v, true) + " ").withStyle(s -> s.withColor(color))
                        .append(stat.name().copy().withStyle(s -> s.withColor(RpgFrame.TEXT_SOFT)));
                if (r != null) label = label.copy().append(Component.literal(" " + stat.formatRange(r[0], r[1])).withStyle(s -> s.withColor(RpgFrame.TEXT_FAINT)));
                g.fill(x, cy, x + W, cy + LINE_H, row++ % 2 == 0 ? ROW_DARK : ROW_CLEAR);
                int slotsX = x + W - 6 - 3 * (SLOT + 2);
                g.drawString(font, fit(font, label, slotsX - x - 14), x + 6, cy + 4, 0xFFFFFF, true);
                for (int size = 0; size < 3; size++) {
                    int sx = slotsX + size * (SLOT + 2);
                    int sy = cy + 1;
                    RuneData rune = new RuneData(id, EnhancedAnvilBlockMenu.SIZES[size]);
                    int count = count(inv, rune);
                    RuneSlot slot = new RuneSlot(sx, sy, i, size, count, rune);
                    slots.add(slot);
                    boolean over = slot.contains(mouseX, mouseY);
                    if (over) hovered = slot;
                    int edge = count > 0 ? (over ? 0xFFFFFFFF : 0xFF000000 | stat.rgb()) : 0xFF30343E;
                    g.fill(sx, sy, sx + SLOT, sy + SLOT, count > 0 ? (over ? 0x60FFFFFF : 0x50000000) : 0x30000000);
                    g.fill(sx, sy, sx + SLOT, sy + 1, edge);
                    g.fill(sx, sy + SLOT - 1, sx + SLOT, sy + SLOT, edge);
                    g.fill(sx, sy, sx + 1, sy + SLOT, edge);
                    g.fill(sx + SLOT - 1, sy, sx + SLOT, sy + SLOT, edge);
                    g.pose().pushPose();
                    g.pose().translate(sx + 1, sy + 1, 0);
                    g.pose().scale(12 / 16f, 12 / 16f, 1f);
                    g.renderFakeItem(RuneItem.of(id, rune.power(), 1), 0, 0);
                    g.pose().popPose();
                    if (count == 0) {
                        g.pose().pushPose();
                        g.pose().translate(0, 0, 200);
                        g.fill(sx + 1, sy + 1, sx + SLOT - 1, sy + SLOT - 1, 0xA0101420);
                        g.pose().popPose();
                    } else {
                        String n = count > 99 ? "99+" : String.valueOf(count);
                        g.pose().pushPose();
                        g.pose().translate(0, 0, 250);
                        g.pose().scale(0.5f, 0.5f, 1f);
                        g.drawString(font, n, (sx + SLOT - 1) * 2 - font.width(n), (sy + SLOT - 5) * 2, 0xFFFFFF, true);
                        g.pose().popPose();
                    }
                }
                cy += LINE_H;
            }
            // Chance de la rune posée sur la table (exo...) et puits
            RuneData placed = placedRune.get(ModDataComponents.RUNE.get());
            Component foot = stats.well() > 0 ? Component.translatable("forge.itemsrarity.well", stats.well()).withStyle(ChatFormatting.AQUA) : Component.empty();
            if (placed != null) {
                float c = ForgeMagic.chance(gear, placed);
                Component ch = c < 0 ? Component.translatable("forge.itemsrarity.chance_none").withStyle(ChatFormatting.RED)
                        : Component.translatable("forge.itemsrarity.chance_table", Math.round(c * 100)).withStyle(ChatFormatting.YELLOW);
                foot = foot.getString().isEmpty() ? ch : foot.copy().append("  ").append(ch);
            }
            g.fill(x, cy, x + W, cy + 14, HEAD);
            g.fill(x, cy, x + W, cy + 1, 0x60C8D0E0);
            g.drawString(font, foot, x + 6, cy + 3, 0xFFFFFF, true);
            cy += 14;
        }
        if (!log.isEmpty()) {
            cy += 6;
            g.fill(x, cy - 2, x + W, cy + log.size() * 10 + 2, 0x60080B14);
            for (FormattedCharSequence line : log) {
                g.drawString(font, line, x + 6, cy, 0xFFFFFF, true);
                cy += 10;
            }
        }
        // Survol d'une case : la rune, combien, la chance
        if (hovered != null) {
            List<Component> tip = new ArrayList<>();
            tip.add(RuneItem.of(hovered.rune().stat(), hovered.rune().power(), 1).getHoverName());
            tip.add(Component.translatable("forge.itemsrarity.in_inventory", hovered.count())
                    .withStyle(hovered.count() > 0 ? ChatFormatting.GRAY : ChatFormatting.RED));
            float c = ForgeMagic.chance(gear, hovered.rune());
            if (c >= 0) tip.add(Component.translatable("forge.itemsrarity.chance", Math.round(c * 100)).withStyle(ChatFormatting.YELLOW));
            tip.add(Component.translatable("forge.itemsrarity.shift_repeat").withStyle(ChatFormatting.DARK_GRAY));
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.renderComponentTooltip(font, tip, (int) mouseX, (int) mouseY);
            g.pose().popPose();
        }
        return slots;
    }

    public static int count(Inventory inv, RuneData wanted) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && wanted.equals(s.get(ModDataComponents.RUNE.get()))) n += s.getCount();
        }
        return n;
    }

    private static FormattedCharSequence fit(Font font, Component text, int maxW) {
        if (font.width(text) <= maxW) return text.getVisualOrderText();
        return net.minecraft.locale.Language.getInstance().getVisualOrder(net.minecraft.network.chat.FormattedText.composite(
                font.substrByWidth(text, maxW - font.width("…")), net.minecraft.network.chat.FormattedText.of("…")));
    }
}
