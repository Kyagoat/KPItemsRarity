package net.Kyap.KPItemsRarity.client.tooltip;

import net.Kyap.KPItemsRarity.data.RarityData;
import net.Kyap.KPItemsRarity.data.SetDefinition;
import net.Kyap.KPItemsRarity.data.StatDefinition;
import net.Kyap.KPItemsRarity.stats.EquippedStats;
import net.Kyap.KPItemsRarity.stats.GearSlots;
import net.Kyap.KPItemsRarity.stats.SetPieces;
import net.Kyap.KPItemsRarity.util.ItemIds;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ce que montre la carte d'une panoplie, sans rien mesurer ni dessiner : en-tête (nom, pièces portées,
 * palier), cases des pièces, et un bloc par palier de bonus.
 */
final class SetCardContent {
    final String setId;
    final int tier;
    final int accent;
    final int wornCount;
    final Component title;
    final Component count;
    final Component subtitle;
    @Nullable final Component lore;
    /** Une case par emplacement ; les pièces au choix partagent la même case. */
    final List<List<String>> slots;
    /** Identifiants des pièces que porte le joueur (équipement et accessoires). */
    final Set<String> worn;
    final List<Line> body = new ArrayList<>();

    private SetCardContent(String setId, SetDefinition set, @Nullable Player player) {
        this.setId = setId;
        List<String> pieceIds = SetPieces.sortedPieces(setId);
        this.slots = SetPieces.slotGroups(setId, pieceIds);
        this.worn = wornIds(player);
        this.wornCount = player != null ? EquippedStats.countSetPieces(player).getOrDefault(setId, 0) : 0;
        int total = SetPieces.pieceCount(setId);
        this.tier = SetPieces.tierOf(pieceIds);
        this.accent = RpgFrame.tierColor(tier);
        this.title = set.name().withStyle(Style.EMPTY.withBold(true).withColor(TextColor.fromRgb(accent)));
        this.count = Component.literal(wornCount + "/" + total);
        this.subtitle = subtitle(SetPieces.firstTier(pieceIds), total);
        this.lore = LoreText.set(set);
        addTiers(set);
    }

    static SetCardContent of(String setId, SetDefinition set, @Nullable Player player) {
        return new SetCardContent(setId, set, player);
    }

    /** Chaque palier : un petit titre (▸ actif, ✔ atteint), puis ses bonus un par ligne, la contrepartie en dernier. */
    private void addTiers(SetDefinition set) {
        var thresholds = set.thresholds();
        var active = thresholds.floorEntry(wornCount);
        boolean first = true;
        for (var entry : thresholds.entrySet()) {
            int n = entry.getKey();
            boolean reached = n <= wornCount;
            boolean isActive = active != null && active.getKey() == n;
            if (!first) body.add(new Gap());
            first = false;
            String mark = isActive ? "▸ " : reached ? "✔ " : "";
            int titleColor = isActive ? RpgFrame.POSITIVE : reached ? RpgFrame.TEXT_SOFT : RpgFrame.TEXT_FAINT;
            body.add(new Title(Component.literal(mark).append(Component.translatable("setcard.kpitemsrarity.pieces", n)), titleColor));
            List<Map.Entry<String, Integer>> lines = new ArrayList<>(entry.getValue().entrySet());
            lines.sort(Comparator.comparingInt(b -> b.getValue() < 0 ? 1 : 0));
            for (Map.Entry<String, Integer> b : lines) {
                StatDefinition stat = RarityData.stat(b.getKey());
                if (stat != null) body.add(new Bonus(bonusText(stat, b.getValue(), reached)));
            }
        }
    }

    private static Component bonusText(StatDefinition stat, int value, boolean reached) {
        int valueColor = !reached ? RpgFrame.TEXT_FAINT : value < 0 ? RpgFrame.NEGATIVE : RpgFrame.POSITIVE;
        int nameColor = reached ? RpgFrame.TEXT_SOFT : RpgFrame.TEXT_FAINT;
        return Component.literal(stat.formatValue(value, true) + " ").withStyle(st -> st.withColor(valueColor))
                .append(stat.name().withStyle(st -> st.withColor(nameColor)));
    }

    /** "Palier 2 · 5 pièces" (ou seulement le nombre de pièces si aucune n'a de définition). */
    private static Component subtitle(int tier, int total) {
        Component pieces = Component.translatable("setcard.kpitemsrarity.pieces", total);
        if (tier < 0) return pieces;
        MutableComponent out = Component.translatable("tooltip.kpitemsrarity.tier", tier);
        return out.append(" · ").append(pieces);
    }

    /** Pièces portées par le joueur : équipement et accessoires (comme pour le compte des pièces). */
    static Set<String> wornIds(@Nullable Player player) {
        Set<String> worn = new HashSet<>();
        if (player == null) return worn;
        for (ItemStack s : GearSlots.wornGear(player)) worn.add(ItemIds.of(s));
        for (ItemStack s : GearSlots.wornAccessories(player)) worn.add(ItemIds.of(s));
        return worn;
    }

    // ---------------------------------------------------------------- Lignes du corps

    /** Une ligne du corps de la carte : elle connaît sa hauteur, sa largeur et se dessine. */
    sealed interface Line permits Title, Bonus, LoreLine, Gap, Rule {
        int height();

        int width(Font font);

        void draw(GuiGraphics g, Font font, int left, int right, int y, int accent);
    }

    /** Titre de palier ("▸ 3 pièces") avec son trait. */
    record Title(Component text, int color) implements Line {
        public int height() {
            return 12;
        }

        public int width(Font font) {
            return font.width(text) + 20;
        }

        public void draw(GuiGraphics g, Font font, int left, int right, int y, int accent) {
            RpgFrame.sectionTitle(g, font, text, left, y, right - left, color);
        }
    }

    /** Un bonus ("+2 Force"). */
    record Bonus(Component text) implements Line {
        public int height() {
            return 11;
        }

        public int width(Font font) {
            return font.width(text);
        }

        public void draw(GuiGraphics g, Font font, int left, int right, int y, int accent) {
            g.drawString(font, text, left, y, 0xFFFFFF, true);
        }
    }

    /** Une ligne du lore, déjà coupée à la largeur de la carte. */
    record LoreLine(FormattedCharSequence line) implements Line {
        public int height() {
            return 10;
        }

        public int width(Font font) {
            return 0;
        }

        public void draw(GuiGraphics g, Font font, int left, int right, int y, int accent) {
            g.drawString(font, line, left, y, RpgFrame.TEXT_DIM, true);
        }
    }

    record Gap() implements Line {
        public int height() {
            return 3;
        }

        public int width(Font font) {
            return 0;
        }

        public void draw(GuiGraphics g, Font font, int left, int right, int y, int accent) {}
    }

    /** Trait de séparation (avant le lore). */
    record Rule() implements Line {
        public int height() {
            return 3;
        }

        public int width(Font font) {
            return 0;
        }

        public void draw(GuiGraphics g, Font font, int left, int right, int y, int accent) {
            g.fill(left, y + 1, right, y + 2, RpgFrame.argb(accent, 0x40));
        }
    }
}
