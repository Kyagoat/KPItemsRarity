package net.Kyap.KPItemsRarity.client.tooltip;

import net.Kyap.KPItemsRarity.client.ShareKey;
import net.Kyap.KPItemsRarity.client.tooltip.TooltipModel.*;
import net.Kyap.KPItemsRarity.component.ItemStats;
import net.Kyap.KPItemsRarity.data.ItemDefinition;
import net.Kyap.KPItemsRarity.data.RarityData;
import net.Kyap.KPItemsRarity.data.RollRange;
import net.Kyap.KPItemsRarity.data.SetDefinition;
import net.Kyap.KPItemsRarity.data.StatDefinition;
import net.Kyap.KPItemsRarity.stats.DynamicRarity;
import net.Kyap.KPItemsRarity.stats.EquippedStats;
import net.Kyap.KPItemsRarity.stats.ItemRolls;
import net.Kyap.KPItemsRarity.stats.SetPieces;
import net.Kyap.KPItemsRarity.util.ModRarities.ModRarity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ce que montre l'infobulle RPG d'un objet, sans rien mesurer ni dessiner : en-tête (nom, rareté, type,
 * valeur de base), corps (stats, enchantements, panoplie, conditions, passifs, lignes des autres mods),
 * texte du bas (lore, description) et durabilité.
 */
final class TooltipContent {
    final ItemStack stack;
    /** Équipement du mod (définition ou jets), ou n'importe quel autre objet (matériau, bloc, nourriture...). */
    final boolean gear;
    final ModRarity rarity;
    final int accent;
    final Component name;
    final Component rarityLabel;
    final Component subtitle;
    @Nullable final BaseValue base;
    final List<Elem> body = new ArrayList<>();
    /** Texte libre tout en bas : lignes des autres mods, lore, description d'un objet simple. */
    final List<Component> footerText = new ArrayList<>();
    @Nullable final Component durability;

    private TooltipContent(ItemStack stack, @Nullable ItemDefinition def, @Nullable ItemStats stats,
                           List<Component> vanilla, List<ClientTooltipComponent> images, Font font) {
        this.stack = stack;
        this.gear = def != null || stats != null;
        this.rarity = gear ? DynamicRarity.rarityOf(stack) : ItemTypes.fromVanilla(stack.getRarity());
        this.accent = RpgFrame.rarityColor(rarity);
        int upgrades = stats != null ? stats.upgrades() : 0;

        this.name = stack.getHoverName().copy().withStyle(Style.EMPTY.withBold(true)
                .withColor(TextColor.fromRgb(RpgFrame.mix(accent, 0xFFFFFF, 0.25f))));
        this.rarityLabel = rarityLabel(rarity, accent, upgrades);
        MutableComponent sub = ItemTypes.typeName(stack).copy();
        if (def != null) sub.append(" · ").append(Component.translatable("tooltip.kpitemsrarity.tier", def.tier));
        this.subtitle = sub;
        this.base = BaseValue.of(stack, def, stats);

        addStatRows(def, stats, upgrades, font);
        for (Row row : VanillaRows.vanillaAttributeRows(stack)) body.add(new RowElem(row));
        VanillaRows.foodRows(stack, body);
        int afterStats = body.size();
        addExo(stats);
        addWell(stats);
        Set<String> enchantTexts = addEnchantments();
        addSet(def);
        PassiveRows.addConditions(body, def);
        PassiveRows.addPassives(body, def);
        addOtherMods(vanilla, images, enchantTexts, afterStats, def);
        addQuality(stats);
        this.durability = stack.isDamageableItem()
                ? Component.translatable("tooltip.kpitemsrarity.rpg.durability", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage())
                : null;
    }

    static TooltipContent of(ItemStack stack, @Nullable ItemDefinition def, @Nullable ItemStats stats,
                             List<Component> vanilla, List<ClientTooltipComponent> images, Font font) {
        return new TooltipContent(stack, def, stats, vanilla, images, font);
    }

    private static Component rarityLabel(ModRarity rarity, int accent, int upgrades) {
        MutableComponent label = Component.empty();
        if (upgrades > 0) label.append(Component.literal("✦".repeat(upgrades) + " ").withStyle(s -> s.withColor(TooltipHandler.OVERMAX_COLOR)));
        label.append(Component.translatable("rarity.kpitemsrarity." + rarity.getId()).withStyle(s -> s.withColor(accent)));
        return label;
    }

    // ---------------------------------------------------------------- Stats

    /** Les jets dans l'ordre de la définition (fourchette à droite), ou les stats possibles d'un objet pas encore tiré. */
    private void addStatRows(@Nullable ItemDefinition def, @Nullable ItemStats stats, int upgrades, Font font) {
        if (stats != null) {
            Set<String> shown = new HashSet<>();
            if (def != null) {
                for (String statId : def.stats.keySet()) {
                    shown.add(statId);
                    if (!stats.lines().containsKey(statId)) continue;
                    StatDefinition sd = RarityData.stat(statId);
                    if (sd != null && sd.base) continue; // affichée en grand dans l'en-tête
                    body.add(new RowElem(StatRows.statRow(statId, stats.get(statId), ItemRolls.range(def, statId, upgrades))));
                    StatRows.addStatHelp(body, font, sd);
                }
            }
            stats.lines().forEach((statId, value) -> {
                if (!shown.contains(statId)) body.add(new RowElem(StatRows.statRow(statId, value, null)));
            });
            if (!Screen.hasShiftDown()) addShiftHint(font);
        } else if (def != null) {
            for (String statId : def.stats.keySet()) {
                StatDefinition sd = RarityData.stat(statId);
                RollRange r = def.range(statId);
                if (sd == null || sd.base || r == null) continue;
                body.add(new RowElem(StatRows.possibleRow(sd, r)));
            }
        }
    }

    /** Indice discret "Maj : ce que fait chaque stat", sur la stat la plus courte (là où il tient le mieux). */
    private void addShiftHint(Font font) {
        int best = -1, bestW = Integer.MAX_VALUE;
        for (int i = 0; i < body.size(); i++) {
            if (body.get(i) instanceof RowElem r && r.row().right().getString().isEmpty() && font.width(r.row().left()) < bestW) {
                best = i;
                bestW = font.width(r.row().left());
            }
        }
        if (best >= 0 && body.get(best) instanceof RowElem r) {
            Component hint = Component.translatable("tooltip.kpitemsrarity.rpg.help_hint").withStyle(st -> st.withColor(RpgFrame.TEXT_FAINT));
            body.set(best, new RowElem(r.row().withHint(hint)));
        }
    }

    private void addExo(@Nullable ItemStats stats) {
        if (stats == null || !stats.hasExo()) return;
        body.add(new GapElem(3));
        body.add(new TitleElem(Component.translatable("tooltip.kpitemsrarity.rpg.exo"), RpgFrame.EXO));
        StatDefinition sd = RarityData.stat(stats.exoStat());
        Component exo = Component.literal("✦ ").withStyle(s -> s.withColor(RpgFrame.EXO))
                .append(Component.literal((sd != null ? sd.formatValue(stats.exoValue(), true) : "+" + stats.exoValue()) + " ")
                        .append(sd != null ? sd.name() : Component.literal(stats.exoStat()))
                        .withStyle(s -> s.withColor(RpgFrame.EXO)));
        body.add(new RowElem(new Row(exo, Component.empty(), 0)));
    }

    /** Puits de forgemagie : ce que les échecs ont mis de côté pour les prochaines runes. */
    private void addWell(@Nullable ItemStats stats) {
        if (stats == null || stats.well() <= 0) return;
        body.add(new RowElem(new Row(Component.translatable("tooltip.kpitemsrarity.well", stats.well())
                .withStyle(st -> st.withColor(0x6FD8E8)), Component.empty(), 0)));
    }

    private void addQuality(@Nullable ItemStats stats) {
        if (stats == null || !Screen.hasShiftDown()) return;
        body.add(new GapElem(3));
        body.add(new RowElem(new Row(Component.translatable("tooltip.kpitemsrarity.rpg.quality").withStyle(s -> s.withColor(RpgFrame.TEXT_DIM)),
                Component.literal(Math.round(DynamicRarity.quality(stack) * 100) + " %").withStyle(s -> s.withColor(RpgFrame.TEXT_SOFT)), 0)));
    }

    // ---------------------------------------------------------------- Enchantements, panoplie

    /** @return le texte de chaque enchantement (pour ne pas les répéter dans les lignes vanilla). */
    private Set<String> addEnchantments() {
        Set<String> texts = new HashSet<>();
        ItemEnchantments enchants = stack.getTagEnchantments();
        if (enchants.isEmpty()) return texts;
        body.add(new GapElem(3));
        body.add(new TitleElem(Component.translatable("tooltip.kpitemsrarity.rpg.enchantments"), RpgFrame.MAGIC));
        for (var entry : enchants.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            Component full = Enchantment.getFullname(holder, entry.getIntValue());
            texts.add(full.getString());
            int color = holder.is(EnchantmentTags.CURSE) ? RpgFrame.NEGATIVE : RpgFrame.mix(RpgFrame.MAGIC, 0xFFFFFF, 0.3f);
            Component line = Component.literal("◆ ").withStyle(s -> s.withColor(RpgFrame.MAGIC))
                    .append(Component.literal(full.getString()).withStyle(s -> s.withColor(color)));
            body.add(new RowElem(new Row(line, Component.empty(), 0)));
        }
        return texts;
    }

    /** "Panoplie X ●● 2/4   Maintiens [Alt]", et la mention "au choix" si la pièce partage sa case. */
    private void addSet(@Nullable ItemDefinition def) {
        if (def == null || !def.hasSet()) return;
        SetDefinition set = RarityData.set(def.set);
        if (set == null) return;
        Player player = Minecraft.getInstance().player;
        int worn = player != null ? EquippedStats.countSetPieces(player).getOrDefault(def.set, 0) : 0;
        int total = SetPieces.pieceCount(def.set);
        int setTier = SetPieces.tierOfSet(def.set);
        body.add(new GapElem(3));
        Component left = set.name().withStyle(s -> s.withColor(RpgFrame.tierColor(setTier)))
                .append(Component.literal(" ")).append(RpgFrame.tierPips(setTier))
                .append(Component.literal("  " + worn + "/" + total).withStyle(s -> s.withColor(worn > 0 ? RpgFrame.POSITIVE : RpgFrame.TEXT_DIM)));
        Component right = Component.translatable("tooltip.kpitemsrarity.rpg.set_hint", TooltipText.shortKey(ShareKey.SET_DETAILS.getTranslatedKeyMessage()))
                .withStyle(s -> s.withColor(RpgFrame.TEXT_FAINT));
        body.add(new RowElem(new Row(left, right, 0)));
    }

    // ---------------------------------------------------------------- Autres mods, lore

    /**
     * Lignes vanilla et des autres mods : celles qui ressemblent à des stats vont avec les stats (mêmes couleurs),
     * les textes vont en bas. Le nom du mod (ajouté par JEI & co) est retiré. Puis le lore d'un équipement,
     * ou la description d'un objet simple (écrite ou automatique, voir ItemDescriptions).
     */
    private void addOtherMods(List<Component> vanilla, List<ClientTooltipComponent> images, Set<String> enchantTexts,
                              int afterStats, @Nullable ItemDefinition def) {
        List<Component> extras = VanillaRows.extraLines(vanilla, stack, enchantTexts);
        ItemDescriptions.Description description = gear ? null : ItemDescriptions.describe(stack);
        List<Elem> modRows = new ArrayList<>();
        List<Component> modTexts = new ArrayList<>();
        String modName = TooltipText.modName(stack);
        for (Component c : extras) {
            String str = c.getString();
            if (str.isBlank() || str.equals(modName)) continue;
            if (TooltipText.STAT_LINE.matcher(str).matches()) modRows.add(new RowElem(new Row(TooltipText.asStatLine(c), Component.empty(), 0)));
            else modTexts.add(c);
        }
        modTexts = TooltipText.joinBrokenLines(modTexts);
        // Si la description dit déjà la même chose (effets d'une potion...), on ne répète pas les lignes vanilla.
        if (description != null && description.replacesVanilla()) {
            modRows.clear();
            modTexts.clear();
        }
        for (ClientTooltipComponent image : images) modRows.add(new ImageElem(image)); // Tide : emplacements d'appât...
        body.addAll(afterStats, modRows);
        footerText.addAll(modTexts);
        // Une seule légende : si l'objet a déjà sa propre description (son mod), pas de description automatique.
        if (description != null && description.auto() && !modTexts.isEmpty()) description = null;
        if (gear) {
            Component lore = LoreText.item(stack, def);
            if (lore != null) footerText.add(lore);
        } else if (description != null) {
            footerText.addAll(description.parts());
        }
    }
}
