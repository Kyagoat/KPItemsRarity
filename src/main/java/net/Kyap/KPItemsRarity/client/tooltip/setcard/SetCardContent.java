package net.Kyap.KPItemsRarity.client.tooltip.setcard;

import net.Kyap.KPItemsRarity.client.tooltip.card.Elem;
import net.Kyap.KPItemsRarity.client.tooltip.card.Palette;
import net.Kyap.KPItemsRarity.client.tooltip.text.LoreText;
import net.Kyap.KPItemsRarity.data.RarityData;
import net.Kyap.KPItemsRarity.data.SetDefinition;
import net.Kyap.KPItemsRarity.data.StatDefinition;
import net.Kyap.KPItemsRarity.stats.EquippedStats;
import net.Kyap.KPItemsRarity.stats.GearSlots;
import net.Kyap.KPItemsRarity.stats.SetLayout;
import net.Kyap.KPItemsRarity.util.ItemIds;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
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
 * palier), cases des pièces, un bloc par palier de bonus et le lore. La carte le place et le dessine.
 */
final class SetCardContent {
    final String setId;
    final int tier;
    final int accent;
    final int wornCount;
    final Component title;
    final Component count;
    final Component subtitle;
    /** Une case par emplacement ; les pièces au choix partagent la même case. */
    final List<List<SetLayout.Piece>> slots;
    /** Identifiants des pièces que porte le joueur (équipement et accessoires). */
    final Set<String> worn;
    /** Les bonus par palier, puis le lore. */
    final List<Elem> body = new ArrayList<>();

    private SetCardContent(String setId, SetDefinition set, @Nullable Player player) {
        this.setId = setId;
        SetLayout layout = SetLayout.of(setId);
        this.slots = layout.slots();
        this.worn = wornIds(player);
        this.wornCount = player != null ? EquippedStats.countSetPieces(player).getOrDefault(setId, 0) : 0;
        this.tier = layout.tier();
        this.accent = Palette.tier(tier);
        this.title = set.name().withStyle(Style.EMPTY.withBold(true).withColor(TextColor.fromRgb(accent)));
        this.count = Component.literal(wornCount + "/" + layout.wearable());
        this.subtitle = subtitle(layout.firstTier(), layout.wearable());
        addTiers(set);
        Component lore = LoreText.set(set);
        if (lore != null) {
            body.add(new Elem.Rule());
            body.add(new Elem.Text(List.of(lore), Palette.TEXT_DIM));
        }
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
            if (!first) body.add(Elem.Gap.SECTION);
            first = false;
            String mark = isActive ? "▸ " : reached ? "✔ " : "";
            int titleColor = isActive ? Palette.POSITIVE : reached ? Palette.TEXT_SOFT : Palette.TEXT_FAINT;
            body.add(new Elem.Title(Component.literal(mark).append(Component.translatable("setcard.kpitemsrarity.pieces", n)), titleColor));
            List<Map.Entry<String, Integer>> lines = new ArrayList<>(entry.getValue().entrySet());
            lines.sort(Comparator.comparingInt(b -> b.getValue() < 0 ? 1 : 0));
            for (Map.Entry<String, Integer> b : lines) {
                StatDefinition stat = RarityData.stat(b.getKey());
                if (stat != null) body.add(new Elem.Line(bonusText(stat, b.getValue(), reached)));
            }
        }
    }

    private static Component bonusText(StatDefinition stat, int value, boolean reached) {
        int valueColor = !reached ? Palette.TEXT_FAINT : value < 0 ? Palette.NEGATIVE : Palette.POSITIVE;
        int nameColor = reached ? Palette.TEXT_SOFT : Palette.TEXT_FAINT;
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
}
