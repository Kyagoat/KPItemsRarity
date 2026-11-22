package net.Kyap.KPItemsRarity.client.tooltip.item;

import net.Kyap.KPItemsRarity.client.ShareKey;
import net.Kyap.KPItemsRarity.client.tooltip.card.Card;
import net.Kyap.KPItemsRarity.client.tooltip.card.Elem;
import net.Kyap.KPItemsRarity.client.tooltip.card.RpgFrame;
import net.Kyap.KPItemsRarity.client.tooltip.text.ItemDescriptions;
import net.Kyap.KPItemsRarity.client.tooltip.text.ItemTypes;
import net.Kyap.KPItemsRarity.client.tooltip.text.LoreText;
import net.Kyap.KPItemsRarity.client.tooltip.text.Texts;
import net.Kyap.KPItemsRarity.component.ItemStats;
import net.Kyap.KPItemsRarity.component.ModDataComponents;
import net.Kyap.KPItemsRarity.data.ItemDefinition;
import net.Kyap.KPItemsRarity.data.RarityData;
import net.Kyap.KPItemsRarity.data.SetDefinition;
import net.Kyap.KPItemsRarity.data.StatDefinition;
import net.Kyap.KPItemsRarity.stats.DynamicRarity;
import net.Kyap.KPItemsRarity.stats.EquippedStats;
import net.Kyap.KPItemsRarity.stats.SetPieces;
import net.Kyap.KPItemsRarity.util.ModRarities.ModRarity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
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
 * Ce que montre l'infobulle d'un objet, rangé en une {@link Card} : l'en-tête ({@link ItemHeader}), puis le corps
 * de haut en bas :
 *
 *   stats et attributs vanilla (StatRows) · nourriture, lignes-stats des autres mods (ForeignLines)
 *   exo · puits de forgemagie · enchantements · panoplie · conditions et passifs (PassiveRows) · qualité (Maj)
 *   ─── texte : lignes des autres mods, puis le lore d'un équipement ou la description d'un objet simple
 *
 * et la durabilité en pied. Les objets hors du mod (matériaux, blocs, nourriture...) ont la même infobulle.
 */
final class ItemCard {
    private static final int WELL = 0x6FD8E8;

    private final ItemStack stack;
    @Nullable private final ItemDefinition def;
    @Nullable private final ItemStats stats;
    /** Équipement du mod (définition ou jets), ou n'importe quel autre objet. */
    private final boolean gear;
    private final List<Elem> body = new ArrayList<>();

    private ItemCard(ItemStack stack) {
        this.stack = stack;
        this.def = RarityData.itemDef(stack);
        this.stats = stack.get(ModDataComponents.STATS);
        this.gear = def != null || stats != null;
    }

    /**
     * @param vanilla lignes de l'infobulle vanilla (et des autres mods)
     * @param images  composants non textuels de l'infobulle (cases d'appât de Tide, cartes...)
     */
    static Card of(ItemStack stack, List<Component> vanilla, List<ClientTooltipComponent> images, Font font) {
        return new ItemCard(stack).build(vanilla, images, font);
    }

    private Card build(List<Component> vanilla, List<ClientTooltipComponent> images, Font font) {
        ModRarity rarity = gear ? DynamicRarity.rarityOf(stack) : ItemTypes.fromVanilla(stack.getRarity());
        int accent = RpgFrame.rarityColor(rarity);

        StatRows.add(body, stack, def, stats, font);
        ForeignLines.addFood(body, stack);
        int afterStats = body.size();
        addExo();
        addWell();
        Set<String> enchantTexts = addEnchantments();
        addSet();
        PassiveRows.addConditions(body, def);
        PassiveRows.addPassives(body, def);
        List<Component> texts = addForeignLines(vanilla, images, enchantTexts, afterStats);
        addQuality();
        if (!texts.isEmpty()) {
            if (!body.isEmpty()) body.add(new Elem.Rule());
            body.add(new Elem.Text(texts, RpgFrame.TEXT_DIM));
        }
        return new Card(new ItemHeader(stack, def, stats, rarity, accent), body, durability(), accent,
                RpgFrame.pulses(rarity), RpgFrame.ornateLevel(rarity));
    }

    @Nullable
    private Component durability() {
        if (!stack.isDamageableItem()) return null;
        return Component.translatable("tooltip.kpitemsrarity.rpg.durability", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage())
                .withStyle(s -> s.withColor(RpgFrame.DURABILITY));
    }

    // ---------------------------------------------------------------- Forgemagie et qualité

    /** La ligne exotique ajoutée à la forge, sous son propre titre. */
    private void addExo() {
        if (stats == null || !stats.hasExo()) return;
        body.add(Elem.Gap.SECTION);
        body.add(new Elem.Title(Component.translatable("tooltip.kpitemsrarity.rpg.exo"), RpgFrame.EXO));
        StatDefinition sd = RarityData.stat(stats.exoStat());
        Component value = Component.literal((sd != null ? sd.formatValue(stats.exoValue(), true) : "+" + stats.exoValue()) + " ")
                .append(sd != null ? sd.name() : Component.literal(stats.exoStat()));
        body.add(new Elem.Line(Component.literal("✦ ").append(value).withStyle(s -> s.withColor(RpgFrame.EXO))));
    }

    /** Puits de forgemagie : ce que les échecs ont mis de côté pour les prochaines runes. */
    private void addWell() {
        if (stats == null || stats.well() <= 0) return;
        body.add(new Elem.Line(Component.translatable("tooltip.kpitemsrarity.well", stats.well()).withStyle(s -> s.withColor(WELL))));
    }

    /** Maj : la qualité du jet (où se situent les lignes dans leurs fourchettes). */
    private void addQuality() {
        if (stats == null || !Screen.hasShiftDown()) return;
        body.add(Elem.Gap.SECTION);
        body.add(new Elem.Line(Component.translatable("tooltip.kpitemsrarity.rpg.quality").withStyle(s -> s.withColor(RpgFrame.TEXT_DIM)),
                Component.literal(Math.round(DynamicRarity.quality(stack) * 100) + " %").withStyle(s -> s.withColor(RpgFrame.TEXT_SOFT))));
    }

    // ---------------------------------------------------------------- Enchantements, panoplie

    /** @return le texte de chaque enchantement (pour ne pas les répéter parmi les lignes vanilla). */
    private Set<String> addEnchantments() {
        Set<String> texts = new HashSet<>();
        ItemEnchantments enchants = stack.getTagEnchantments();
        if (enchants.isEmpty()) return texts;
        body.add(Elem.Gap.SECTION);
        body.add(new Elem.Title(Component.translatable("tooltip.kpitemsrarity.rpg.enchantments"), RpgFrame.MAGIC));
        for (var entry : enchants.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            String name = Enchantment.getFullname(holder, entry.getIntValue()).getString();
            texts.add(name);
            int color = holder.is(EnchantmentTags.CURSE) ? RpgFrame.NEGATIVE : RpgFrame.mix(RpgFrame.MAGIC, 0xFFFFFF, 0.3f);
            body.add(new Elem.Line(Component.literal("◆ ").withStyle(s -> s.withColor(RpgFrame.MAGIC))
                    .append(Component.literal(name).withStyle(s -> s.withColor(color)))));
        }
        return texts;
    }

    /** "Panoplie X ◆◆◇◇◇  2/4          Maintiens [Alt]" : le détail est dans la carte de panoplie. */
    private void addSet() {
        if (def == null || !def.hasSet()) return;
        SetDefinition set = RarityData.set(def.set);
        if (set == null) return;
        Player player = Minecraft.getInstance().player;
        int worn = player != null ? EquippedStats.countSetPieces(player).getOrDefault(def.set, 0) : 0;
        int total = SetPieces.pieceCount(def.set);
        int tier = SetPieces.tierOfSet(def.set);
        Component left = set.name().withStyle(s -> s.withColor(RpgFrame.tierColor(tier)))
                .append(Component.literal(" ")).append(RpgFrame.tierPips(tier))
                .append(Component.literal("  " + worn + "/" + total).withStyle(s -> s.withColor(worn > 0 ? RpgFrame.POSITIVE : RpgFrame.TEXT_DIM)));
        Component right = Component.translatable("tooltip.kpitemsrarity.rpg.set_hint", Texts.shortKey(ShareKey.SET_DETAILS.getTranslatedKeyMessage()))
                .withStyle(s -> s.withColor(RpgFrame.TEXT_FAINT));
        body.add(Elem.Gap.SECTION);
        body.add(new Elem.Line(left, right));
    }

    // ---------------------------------------------------------------- Autres mods, lore

    /**
     * Les lignes-stats des autres mods (et leurs images) vont juste après nos stats. Le texte va en bas, suivi du
     * lore d'un équipement ou de la description d'un objet simple (écrite, ou automatique : voir ItemDescriptions).
     * @return le texte du bas
     */
    private List<Component> addForeignLines(List<Component> vanilla, List<ClientTooltipComponent> images, Set<String> enchantTexts,
                                            int afterStats) {
        ForeignLines.Split foreign = ForeignLines.split(vanilla, stack, enchantTexts);
        List<Elem> statLines = new ArrayList<>(foreign.statLines());
        List<Component> texts = new ArrayList<>(foreign.texts());
        ItemDescriptions.Description description = gear ? null : ItemDescriptions.describe(stack);
        // La description dit déjà la même chose (effets d'une potion...) : on ne répète pas les lignes vanilla.
        if (description != null && description.replacesVanilla()) {
            statLines.clear();
            texts.clear();
        }
        for (ClientTooltipComponent image : images) statLines.add(new Elem.Image(image));
        body.addAll(afterStats, statLines);
        // Une seule légende : si l'objet a déjà sa propre description (son mod), pas de description automatique.
        if (description != null && description.auto() && !texts.isEmpty()) description = null;
        if (gear) {
            Component lore = LoreText.item(stack, def);
            if (lore != null) texts.add(lore);
        } else if (description != null) {
            texts.addAll(description.parts());
        }
        return texts;
    }
}
