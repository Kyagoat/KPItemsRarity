package net.Kyap.KPItemsRarity.client.tooltip.item;

import net.Kyap.KPItemsRarity.client.ShareKey;
import net.Kyap.KPItemsRarity.client.tooltip.card.Card;
import net.Kyap.KPItemsRarity.client.tooltip.card.Elem;
import net.Kyap.KPItemsRarity.client.tooltip.card.FrameStyle;
import net.Kyap.KPItemsRarity.client.tooltip.card.Palette;
import net.Kyap.KPItemsRarity.client.tooltip.text.ItemDescriptions;
import net.Kyap.KPItemsRarity.client.tooltip.text.LoreText;
import net.Kyap.KPItemsRarity.client.tooltip.text.Texts;
import net.Kyap.KPItemsRarity.component.ItemStats;
import net.Kyap.KPItemsRarity.component.ModDataComponents;
import net.Kyap.KPItemsRarity.data.ItemDefinition;
import net.Kyap.KPItemsRarity.data.RarityData;
import net.Kyap.KPItemsRarity.data.SetDefinition;
import net.Kyap.KPItemsRarity.data.StatDefinition;
import net.Kyap.KPItemsRarity.rarity.ItemRarity;
import net.Kyap.KPItemsRarity.rarity.ModRarity;
import net.Kyap.KPItemsRarity.stats.EquippedStats;
import net.Kyap.KPItemsRarity.stats.SetLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
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
 * de haut en bas (voir {@link #build}), et la durabilité en pied. Les objets hors du mod (matériaux, blocs,
 * nourriture...) ont la même infobulle.
 */
final class ItemCard {
    private static final int WELL = 0x6FD8E8;

    private final ItemStack stack;
    @Nullable private final ItemDefinition def;
    @Nullable private final ItemStats stats;
    /** Équipement du mod (il a une définition ou des jets) ; sinon un objet simple, qui a une description. */
    private final boolean gear;
    private final Font font;
    private final List<Elem> body = new ArrayList<>();

    private ItemCard(ItemStack stack, Font font) {
        this.stack = stack;
        this.font = font;
        this.def = RarityData.itemDef(stack);
        this.stats = stack.get(ModDataComponents.STATS);
        this.gear = def != null || stats != null;
    }

    /**
     * @param vanilla    lignes de l'infobulle vanilla (et des autres mods)
     * @param shownSince depuis quand l'objet est survolé (le nom trop long défile au bout d'un moment)
     */
    static Card of(ItemStack stack, List<ClientTooltipComponent> vanilla, Font font, long shownSince) {
        return new ItemCard(stack, font).build(vanilla, shownSince);
    }

    private Card build(List<ClientTooltipComponent> vanilla, long shownSince) {
        ItemEnchantments enchantments = stack.getTagEnchantments();
        ForeignLines.Split foreign = ForeignLines.read(vanilla, stack, enchantmentNames(enchantments));
        ItemDescriptions.Description description = gear ? null : ItemDescriptions.describe(stack);
        // La description redit déjà les lignes vanilla (effets d'une potion...) : on ne les répète pas.
        boolean vanillaRepeated = description != null && description.coversVanillaLines();

        StatRows.add(body, stack, def, stats, font);
        addFood();
        if (!vanillaRepeated) body.addAll(foreign.statLines());
        foreign.images().forEach(image -> body.add(new Elem.Image(image)));
        addExo();
        addWell();
        addEnchantments(enchantments);
        addSet();
        PassiveRows.addConditions(body, def);
        PassiveRows.addPassives(body, def);
        addQuality();
        addBottomText(vanillaRepeated ? List.of() : foreign.texts(), description);

        ModRarity rarity = ItemRarity.of(stack);
        return new Card(new ItemHeader(stack, def, stats, rarity, shownSince), body, durability(), FrameStyle.of(rarity));
    }

    @Nullable
    private Component durability() {
        if (!stack.isDamageableItem()) return null;
        return Component.translatable("tooltip.kpitemsrarity.rpg.durability", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage())
                .withStyle(style -> style.withColor(Palette.DURABILITY));
    }

    // ---------------------------------------------------------------- Corps, dans l'ordre

    /** Nourriture : "+4 Nutrition", la saturation à droite. */
    private void addFood() {
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null) return;
        Component nutrition = Component.literal("+" + food.nutrition() + " ").withStyle(style -> style.withColor(Palette.POSITIVE))
                .append(Component.translatable("tooltip.kpitemsrarity.rpg.nutrition").withStyle(style -> style.withColor(Palette.TEXT_SOFT)));
        Component saturation = Component.translatable("tooltip.kpitemsrarity.rpg.saturation", Texts.number(food.saturation()))
                .withStyle(style -> style.withColor(Palette.TEXT_FAINT));
        body.add(new Elem.Line(nutrition, saturation));
    }

    /** La ligne exotique ajoutée à la forge, sous son propre titre. */
    private void addExo() {
        if (stats == null || !stats.hasExo()) return;
        StatDefinition stat = RarityData.stat(stats.exoStat());
        String value = stat != null ? stat.formatValue(stats.exoValue(), true) : "+" + stats.exoValue();
        Component name = stat != null ? stat.name() : Component.literal(stats.exoStat());
        body.add(Elem.Gap.SECTION);
        body.add(new Elem.Title(Component.translatable("tooltip.kpitemsrarity.rpg.exo"), Palette.EXO));
        body.add(new Elem.Line(Component.literal("✦ " + value + " ").append(name).withStyle(style -> style.withColor(Palette.EXO))));
    }

    /** Puits de forgemagie : ce que les échecs ont mis de côté pour les prochaines runes. */
    private void addWell() {
        if (stats == null || stats.well() <= 0) return;
        body.add(new Elem.Line(Component.translatable("tooltip.kpitemsrarity.well", stats.well()).withStyle(style -> style.withColor(WELL))));
    }

    private void addEnchantments(ItemEnchantments enchantments) {
        if (enchantments.isEmpty()) return;
        body.add(Elem.Gap.SECTION);
        body.add(new Elem.Title(Component.translatable("tooltip.kpitemsrarity.rpg.enchantments"), Palette.MAGIC));
        for (var entry : enchantments.entrySet()) {
            Holder<Enchantment> enchantment = entry.getKey();
            int color = enchantment.is(EnchantmentTags.CURSE) ? Palette.NEGATIVE : Palette.mix(Palette.MAGIC, 0xFFFFFF, 0.3f);
            Component name = Enchantment.getFullname(enchantment, entry.getIntValue());
            body.add(new Elem.Line(Component.literal("◆ ").withStyle(style -> style.withColor(Palette.MAGIC))
                    .append(Component.literal(name.getString()).withStyle(style -> style.withColor(color)))));
        }
    }

    /** Le texte de chaque enchantement, pour ne pas le répéter parmi les lignes vanilla. */
    private static Set<String> enchantmentNames(ItemEnchantments enchantments) {
        Set<String> names = new HashSet<>();
        for (var entry : enchantments.entrySet()) names.add(Enchantment.getFullname(entry.getKey(), entry.getIntValue()).getString());
        return names;
    }

    /** "Panoplie X ◆◆◇◇◇  2/4          Maintiens [Alt]" : le détail est dans la carte de panoplie. */
    private void addSet() {
        if (def == null || !def.hasSet()) return;
        SetDefinition set = RarityData.set(def.set);
        if (set == null) return;
        Player player = Minecraft.getInstance().player;
        int worn = player != null ? EquippedStats.countSetPieces(player).getOrDefault(def.set, 0) : 0;
        SetLayout layout = SetLayout.of(def.set);
        int tier = layout.tier();
        Component left = set.name().withStyle(style -> style.withColor(Palette.tier(tier)))
                .append(" ").append(Texts.tierPips(tier))
                .append(Component.literal("  " + worn + "/" + layout.wearable())
                        .withStyle(style -> style.withColor(worn > 0 ? Palette.POSITIVE : Palette.TEXT_DIM)));
        Component right = Component.translatable("tooltip.kpitemsrarity.rpg.set_hint", Texts.shortKey(ShareKey.SET_DETAILS.getTranslatedKeyMessage()))
                .withStyle(style -> style.withColor(Palette.TEXT_FAINT));
        body.add(Elem.Gap.SECTION);
        body.add(new Elem.Line(left, right));
    }

    /** Maj : la qualité du jet (où se situent les lignes dans leurs fourchettes). */
    private void addQuality() {
        if (stats == null || !Screen.hasShiftDown()) return;
        body.add(Elem.Gap.SECTION);
        body.add(new Elem.Line(Component.translatable("tooltip.kpitemsrarity.rpg.quality").withStyle(style -> style.withColor(Palette.TEXT_DIM)),
                Component.literal(Math.round(ItemRarity.quality(stack) * 100) + " %").withStyle(style -> style.withColor(Palette.TEXT_SOFT))));
    }

    /**
     * Le texte du bas, après un trait : celui des autres mods, puis le lore d'un équipement ou la description d'un
     * objet simple. Une description automatique s'efface devant le texte que le mod de l'objet écrit déjà.
     */
    private void addBottomText(List<Component> foreignTexts, @Nullable ItemDescriptions.Description description) {
        List<Component> texts = new ArrayList<>(foreignTexts);
        if (gear) {
            Component lore = LoreText.item(stack, def);
            if (lore != null) texts.add(lore);
        } else if (description != null && !(description.automatic() && !texts.isEmpty())) {
            texts.addAll(description.lines());
        }
        if (texts.isEmpty()) return;
        if (!body.isEmpty()) body.add(new Elem.Rule());
        body.add(new Elem.Text(texts, Palette.TEXT_DIM));
    }
}
