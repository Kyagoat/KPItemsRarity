package net.Kyap.KPItemsRarity.client.tooltip.item;

import net.Kyap.KPItemsRarity.KPItemsRarity;
import net.Kyap.KPItemsRarity.client.tooltip.card.Elem;
import net.Kyap.KPItemsRarity.client.tooltip.card.RpgFrame;
import net.Kyap.KPItemsRarity.client.tooltip.text.Texts;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.GatherSkippedAttributeTooltipsEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Ce qui ne vient pas du mod : nourriture, et les lignes que le vanilla et les autres mods ajoutent à l'infobulle
 * (les attributs vanilla de l'objet sont rangés avec nos stats, voir StatRows). Celles qui ressemblent à des stats ("3 Attack Range") prennent nos couleurs et
 * vont avec les stats ; les textes sont recollés en phrases et vont en bas.
 */
@EventBusSubscriber(modid = KPItemsRarity.MOD_ID, value = Dist.CLIENT)
final class ForeignLines {
    private ForeignLines() {}

    private static final Pattern STAT_LINE = Pattern.compile("^\\s*([+-]?\\d+(?:[.,]\\d+)?\\s*%?)\\s+(.+)$");

    /** Les lignes des autres mods, triées : celles qui ressemblent à des stats, et le texte (en phrases). */
    record Split(List<Elem> statLines, List<Component> texts) {}

    /** Le bloc vanilla des attributs ("Dans la main principale : ...") ne sert à rien : l'en-tête et les stats le disent déjà. */
    @SubscribeEvent
    public static void onSkipAttributes(GatherSkippedAttributeTooltipsEvent event) {
        event.setSkipAll(true);
    }

    /** Nourriture : ce qu'elle rend. */
    static void addFood(List<Elem> body, ItemStack stack) {
        var food = stack.get(DataComponents.FOOD);
        if (food == null) return;
        Component left = Component.literal("+" + food.nutrition() + " ").withStyle(s -> s.withColor(RpgFrame.POSITIVE))
                .append(Component.translatable("tooltip.kpitemsrarity.rpg.nutrition").withStyle(s -> s.withColor(RpgFrame.TEXT_SOFT)));
        Component right = Component.translatable("tooltip.kpitemsrarity.rpg.saturation", Texts.number(food.saturation()))
                .withStyle(s -> s.withColor(RpgFrame.TEXT_FAINT));
        body.add(new Elem.Line(left, right));
    }

    /**
     * Les lignes de l'infobulle vanilla qu'on n'affiche pas déjà autrement (nom, enchantements, durabilité,
     * nom du mod ajouté par JEI & co sont retirés), triées en lignes-stats et en texte.
     */
    static Split split(List<Component> vanilla, ItemStack stack, Set<String> enchantTexts) {
        String modName = modName(stack);
        String durability = stack.isDamageableItem()
                ? Component.translatable("item.durability", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage()).getString()
                : null;
        List<Elem> statLines = new ArrayList<>();
        List<Component> texts = new ArrayList<>();
        for (Component c : withoutAttributeBlock(vanilla)) {
            String text = c.getString();
            if (text.isBlank() || text.equals(modName) || text.equals(durability) || enchantTexts.contains(text)) continue;
            if (STAT_LINE.matcher(text).matches()) statLines.add(new Elem.Line(asStatLine(c)));
            else texts.add(c);
        }
        return new Split(statLines, joinBrokenLines(texts));
    }

    /** Sans la première ligne (le nom) ni un bloc d'attributs qu'un autre mod aurait remis ("Dans la main : ..."). */
    private static List<Component> withoutAttributeBlock(List<Component> vanilla) {
        Set<String> attributeHeaders = new HashSet<>();
        for (EquipmentSlotGroup group : EquipmentSlotGroup.values()) {
            attributeHeaders.add(Component.translatable("item.modifiers." + group.getSerializedName()).getString());
        }
        List<Component> out = new ArrayList<>();
        boolean inAttributes = false;
        for (int i = 1; i < vanilla.size(); i++) {
            Component c = vanilla.get(i);
            String s = c.getString();
            if (attributeHeaders.contains(s)) {
                inAttributes = true;
                continue;
            }
            if (inAttributes && s.startsWith(" ")) continue;
            inAttributes = false;
            out.add(c);
        }
        return out;
    }

    /** "3 Attack Range" -> "+3 Portée d'attaque" aux couleurs de nos stats. */
    private static Component asStatLine(Component line) {
        var m = STAT_LINE.matcher(line.getString());
        if (!m.matches()) return line.copy().withStyle(s -> s.withColor(RpgFrame.TEXT_SOFT));
        String value = m.group(1).trim();
        if (!value.startsWith("+") && !value.startsWith("-")) value = "+" + value;
        int color = value.startsWith("-") ? RpgFrame.NEGATIVE : RpgFrame.POSITIVE;
        return Component.literal(value + " ").withStyle(s -> s.withColor(color))
                .append(Component.literal(m.group(2)).withStyle(s -> s.withColor(RpgFrame.TEXT_SOFT)));
    }

    /**
     * Les mods coupent souvent leur description au milieu d'une phrase : on recolle les lignes qui ne finissent
     * pas une phrase, pour que le texte suive la largeur de l'infobulle. En gris clair, comme nos explications.
     */
    private static List<Component> joinBrokenLines(List<Component> lines) {
        List<Component> out = new ArrayList<>();
        StringBuilder current = null;
        for (Component c : lines) {
            String text = c.getString().trim();
            if (current == null) current = new StringBuilder(text);
            else current.append(' ').append(text);
            if (text.matches(".*[.!?:;)\\]]$")) {
                out.add(Component.literal(current.toString()).withStyle(st -> st.withColor(RpgFrame.TEXT_DIM)));
                current = null;
            }
        }
        if (current != null) out.add(Component.literal(current.toString()).withStyle(st -> st.withColor(RpgFrame.TEXT_DIM)));
        return out;
    }

    /** Nom affiché du mod de l'objet (la ligne "Items Rarity" ajoutée par JEI & co). */
    private static String modName(ItemStack stack) {
        String ns = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        return ModList.get().getModContainerById(ns).map(c -> c.getModInfo().getDisplayName()).orElse("");
    }
}
