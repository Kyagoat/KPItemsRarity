package net.Kyap.ItemsRarity.client;

import net.Kyap.ItemsRarity.ItemsRarity;
import net.Kyap.ItemsRarity.component.ItemStats;
import net.Kyap.ItemsRarity.component.ModDataComponents;
import net.Kyap.ItemsRarity.data.ItemDefinition;
import net.Kyap.ItemsRarity.data.RarityData;
import net.Kyap.ItemsRarity.data.SetDefinition;
import net.Kyap.ItemsRarity.data.StatDefinition;
import net.Kyap.ItemsRarity.events.AttributeEvents;
import net.Kyap.ItemsRarity.util.ModRarities.ModRarity;
import net.Kyap.ItemsRarity.util.StatEngine;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.GatherSkippedAttributeTooltipsEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Infobulle façon Dofus, juste sous le nom :
 *   Peu commun · Palier 2
 *   6 Dégâts (5 - 7)
 *   +4 % Force (3 - 5)
 *   +1 Vie (0 - 1)
 *   ✦ +3 Agilité (exo)
 *   Panoplie de l'Éclaireur 2/5 · [Alt]
 * Le détail de la panoplie s'affiche dans une carte à part en maintenant Alt (SetCard).
 * Les modificateurs posés par le mod sont retirés de la section vanilla ("Dans la main : ...").
 */
@EventBusSubscriber(modid = ItemsRarity.MOD_ID, value = Dist.CLIENT)
public final class TooltipHandler {
    private TooltipHandler() {}

    public static final int EXO_COLOR = RpgFrame.EXO;
    public static final int OVERMAX_COLOR = 0xFFC94A;
    public static final int SET_COLOR = 0xF0D060;
    public static final int BASE_COLOR = 0xE8E8E8;

    @SubscribeEvent
    public static void onSkipAttributes(GatherSkippedAttributeTooltipsEvent event) {
        // Infobulle RPG : dégâts, armure et autres attributs y sont déjà affichés -> pas de bloc vanilla.
        if (net.Kyap.ItemsRarity.ClientConfig.rpgTooltips()) {
            event.setSkipAll(true);
            return;
        }
        ItemStats stats = event.getStack().get(ModDataComponents.STATS);
        if (stats == null) return;
        EquipmentSlotGroup group = StatEngine.slotGroupFor(event.getStack());
        stats.lines().keySet().forEach(statId -> {
            event.skipId(AttributeEvents.modifierId(statId, group));
            StatDefinition stat = RarityData.stat(statId);
            if (stat != null && stat.base) stat.attributeHolder().ifPresent(a -> event.skipId(AttributeEvents.baseModifierId(a)));
        });
        if (stats.hasExo()) event.skipId(AttributeEvents.modifierId(stats.exoStat(), group));
    }

    /**
     * Lignes ajoutées par le dernier appel (même objets Java) : l'infobulle RPG s'en sert pour ne pas
     * les afficher une deuxième fois parmi les lignes des autres mods.
     */
    private static final Set<Component> OURS = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    /** Texte de nos lignes : l'infobulle RPG relit des lignes déjà prêtes à dessiner (plus les mêmes objets). */
    private static final Set<String> OURS_TEXT = new java.util.HashSet<>();

    public static boolean isOurs(Component line) {
        if (OURS.contains(line)) return true;
        String text = line.getString().trim();
        if (text.isEmpty()) return false;
        if (OURS_TEXT.contains(text)) return true;
        // Ligne coupée en deux par le jeu (écran étroit) : un morceau d'une de nos lignes
        if (text.length() >= 6) for (String ours : OURS_TEXT) if (ours.contains(text)) return true;
        return false;
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        OURS.clear();
        OURS_TEXT.clear();
        ItemStack stack = event.getItemStack();
        ItemDefinition def = RarityData.itemDef(stack);
        ItemStats stats = stack.get(ModDataComponents.STATS);
        if (def == null && stats == null) return;

        List<Component> lines = new ArrayList<>();
        int upgrades = stats != null ? stats.upgrades() : 0;

        // En-tête : rareté (+ un ✦ par cran gagné à la forge) · palier
        ModRarity rarity = StatEngine.rarityOf(stack);
        MutableComponent header = Component.translatable("rarity.itemsrarity." + rarity.getId())
                .withStyle(rarity.getRarity().getStyleModifier());
        if (upgrades > 0) header.append(Component.literal(" " + "✦".repeat(upgrades)).withStyle(s -> s.withColor(OVERMAX_COLOR)));
        if (def != null) {
            header.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable("tooltip.itemsrarity.tier", def.tier).withStyle(ChatFormatting.DARK_GRAY));
        }
        lines.add(header);

        if (stats != null) {
            Set<String> shown = new HashSet<>();
            if (def != null) {
                for (String statId : def.stats.keySet()) {
                    shown.add(statId);
                    if (stats.lines().containsKey(statId)) {
                        lines.add(rolledLine(statId, stats.get(statId), StatEngine.range(def, statId, upgrades)));
                    }
                }
            }
            stats.lines().forEach((statId, value) -> {
                if (!shown.contains(statId)) lines.add(rolledLine(statId, value, null));
            });
            if (stats.hasExo()) lines.add(exoLine(stats.exoStat(), stats.exoValue()));
            if (Screen.hasShiftDown() && def != null) {
                lines.add(Component.translatable("tooltip.itemsrarity.quality",
                        Math.round(StatEngine.quality(stack) * 100)).withStyle(ChatFormatting.DARK_GRAY));
            }
        } else {
            // Pas encore tiré (créatif, JEI...) : jets possibles.
            def.stats.keySet().forEach(statId -> {
                if (RarityData.stat(statId) != null) lines.add(possibleLine(statId, def.range(statId)));
            });
        }

        // Panoplie : une seule ligne, le détail est dans la carte (Alt).
        if (def != null && def.hasSet()) {
            SetDefinition set = RarityData.set(def.set);
            if (set != null) lines.add(setLine(def.set, set));
        }

        List<Component> tooltip = event.getToolTip();
        tooltip.addAll(Math.min(1, tooltip.size()), lines);
        OURS.addAll(lines);
        for (Component c : lines) OURS_TEXT.add(c.getString().trim());
    }

    // ---------------------------------------------------------------- Lignes

    static Component rolledLine(String statId, int value, int[] range) {
        StatDefinition stat = RarityData.stat(statId);
        if (stat == null) return Component.literal(value + " " + statId).withStyle(ChatFormatting.GRAY);
        MutableComponent line = Component.literal(stat.formatValue(value, !stat.base) + " ").append(stat.name());
        if (!stat.isActive()) {
            // L'attribut vient d'un mod absent : ligne barrée, sans effet.
            return line.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.STRIKETHROUGH);
        }
        int color = stat.base ? BASE_COLOR : value < 0 ? 0xE05050 : stat.rgb();
        line.withStyle(s -> s.withColor(TextColor.fromRgb(color)));
        if (range != null) {
            line.append(Component.literal(" (" + stat.formatRange(range[0], range[1]) + ")").withStyle(ChatFormatting.DARK_GRAY));
            if (value > range[1]) line.withStyle(s -> s.withColor(OVERMAX_COLOR));
        }
        return line;
    }

    private static Component exoLine(String statId, int value) {
        StatDefinition stat = RarityData.stat(statId);
        MutableComponent name = stat != null ? stat.name() : Component.literal(statId);
        String amount = stat != null ? stat.formatValue(value, true) : "+" + value;
        return Component.literal("✦ ").withStyle(s -> s.withColor(OVERMAX_COLOR))
                .append(Component.literal(amount + " ").append(name).withStyle(s -> s.withColor(EXO_COLOR)))
                .append(Component.literal(" (exo)").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static Component possibleLine(String statId, int[] range) {
        StatDefinition stat = RarityData.stat(statId);
        if (stat == null || range == null) return Component.literal(statId).withStyle(ChatFormatting.DARK_GRAY);
        String unit = "percent".equalsIgnoreCase(stat.format) ? " %" : "";
        return Component.literal(stat.formatRange(range[0], range[1]) + unit + " ").append(stat.name())
                .withStyle(s -> s.withColor(TextColor.fromRgb(dim(stat.base ? BASE_COLOR : stat.rgb()))));
    }

    private static Component setLine(String setId, SetDefinition set) {
        Player player = Minecraft.getInstance().player;
        int worn = player != null ? StatEngine.countSetPieces(player).getOrDefault(setId, 0) : 0;
        int total = SetCard.pieceCount(setId);
        Component key = ShareKey.SET_DETAILS.getTranslatedKeyMessage();
        int tier = SetCard.tierOfSet(setId);
        return set.name().withStyle(s -> s.withColor(RpgFrame.tierColor(tier)))
                .append(Component.literal(" ")).append(RpgFrame.tierPips(tier))
                .append(Component.literal(" " + worn + "/" + total).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" · [").withStyle(ChatFormatting.DARK_GRAY))
                .append(key.copy().withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));
    }

    static int dim(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return ((r * 2 / 3) << 16) | ((g * 2 / 3) << 8) | (b * 2 / 3);
    }
}
