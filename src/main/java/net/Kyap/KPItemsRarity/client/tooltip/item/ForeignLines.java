package net.Kyap.KPItemsRarity.client.tooltip.item;

import net.Kyap.KPItemsRarity.KPItemsRarity;
import net.Kyap.KPItemsRarity.client.tooltip.card.Elem;
import net.Kyap.KPItemsRarity.client.tooltip.card.Palette;
import net.Kyap.KPItemsRarity.mixin.ClientTextTooltipAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.inventory.Slot;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Les lignes que le vanilla et les autres mods mettent dans l'infobulle d'un objet, relues pour l'infobulle RPG :
 *
 *   - celles qui ressemblent à une stat ("3 Attack Range") prennent nos couleurs et vont avec nos stats ;
 *   - les images (cases d'appât de Tide, contenu d'une sacoche...) vont aussi avec les stats ;
 *   - le texte est recollé en phrases et va en bas.
 *
 * On retire ce que l'infobulle RPG montre déjà : nom, enchantements, durabilité, bloc d'attributs, nom du mod.
 * Les attributs vanilla de l'objet, eux, sont lus directement sur l'objet (voir StatRows).
 */
@EventBusSubscriber(modid = KPItemsRarity.MOD_ID, value = Dist.CLIENT)
final class ForeignLines {
    private ForeignLines() {}

    private static final Pattern STAT_LINE = Pattern.compile("^\\s*([+-]?\\d+(?:[.,]\\d+)?\\s*%?)\\s+(.+)$");
    /** Fin de phrase : on peut aller à la ligne après. */
    private static final Pattern SENTENCE_END = Pattern.compile(".*[.!?:;)\\]]$");

    /** Les lignes triées : celles qui ressemblent à des stats, les images, et le texte (en phrases). */
    record Split(List<Elem> statLines, List<ClientTooltipComponent> images, List<Component> texts) {}

    /** Le bloc vanilla des attributs ("Dans la main principale : ...") ne sert à rien : l'en-tête et les stats le disent déjà. */
    @SubscribeEvent
    public static void onSkipAttributes(GatherSkippedAttributeTooltipsEvent event) {
        event.setSkipAll(true);
    }

    /** Trie les lignes de l'infobulle vanilla de l'objet. */
    static Split read(List<ClientTooltipComponent> vanilla, ItemStack stack, Set<String> enchantmentNames) {
        List<Component> lines = new ArrayList<>();
        List<ClientTooltipComponent> images = new ArrayList<>();
        for (ClientTooltipComponent component : vanilla) {
            if (component instanceof ClientTextTooltip text) lines.add(text(text));
            else images.add(component);
        }
        Set<String> alreadyShown = alreadyShown(stack, enchantmentNames);
        List<Elem> statLines = new ArrayList<>();
        List<Component> texts = new ArrayList<>();
        for (Component line : withoutNameAndAttributes(lines)) {
            String text = line.getString();
            if (text.isBlank() || alreadyShown.contains(text)) continue;
            Matcher stat = STAT_LINE.matcher(text);
            if (stat.matches()) statLines.add(new Elem.Line(asStatLine(stat)));
            else texts.add(line);
        }
        return new Split(statLines, images, joinBrokenLines(texts));
    }

    /**
     * L'objet de la case survolée, pour un écran qui dessine l'infobulle sans dire de quel objet elle parle (Curios) ;
     * seulement si l'infobulle commence bien par son nom.
     */
    static ItemStack hoveredSlotItem(List<ClientTooltipComponent> vanilla) {
        if (!(Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen)) return ItemStack.EMPTY;
        if (!(vanilla.getFirst() instanceof ClientTextTooltip firstLine)) return ItemStack.EMPTY;
        Slot slot = screen.getSlotUnderMouse();
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        return text(firstLine).getString().equals(stack.getHoverName().getString()) ? stack : ItemStack.EMPTY;
    }

    // ---------------------------------------------------------------- Tri

    /** Durabilité, enchantements, nom du mod (ajouté par JEI & co) : l'infobulle RPG les montre à sa façon. */
    private static Set<String> alreadyShown(ItemStack stack, Set<String> enchantmentNames) {
        Set<String> shown = new HashSet<>(enchantmentNames);
        String namespace = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        ModList.get().getModContainerById(namespace).ifPresent(mod -> shown.add(mod.getModInfo().getDisplayName()));
        if (stack.isDamageableItem()) {
            shown.add(Component.translatable("item.durability", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage()).getString());
        }
        return shown;
    }

    /** Sans la première ligne (le nom) ni un bloc d'attributs qu'un autre mod aurait remis ("Dans la main : ..."). */
    private static List<Component> withoutNameAndAttributes(List<Component> lines) {
        Set<String> attributeHeaders = new HashSet<>();
        for (EquipmentSlotGroup group : EquipmentSlotGroup.values()) {
            attributeHeaders.add(Component.translatable("item.modifiers." + group.getSerializedName()).getString());
        }
        List<Component> out = new ArrayList<>();
        boolean inAttributes = false;
        for (Component line : lines.subList(Math.min(1, lines.size()), lines.size())) {
            String text = line.getString();
            if (attributeHeaders.contains(text)) {
                inAttributes = true;
            } else if (!inAttributes || !text.startsWith(" ")) {
                inAttributes = false;
                out.add(line);
            }
        }
        return out;
    }

    /** "3 Attack Range" -> "+3 Attack Range" aux couleurs de nos stats. */
    private static Component asStatLine(Matcher stat) {
        String value = stat.group(1).trim();
        if (!value.startsWith("+") && !value.startsWith("-")) value = "+" + value;
        int color = value.startsWith("-") ? Palette.NEGATIVE : Palette.POSITIVE;
        return Component.literal(value + " ").withStyle(style -> style.withColor(color))
                .append(Component.literal(stat.group(2)).withStyle(style -> style.withColor(Palette.TEXT_SOFT)));
    }

    /**
     * Les mods coupent souvent leur description au milieu d'une phrase : on recolle les lignes qui ne finissent pas
     * une phrase, pour que le texte suive la largeur de l'infobulle. En gris, comme nos explications.
     */
    private static List<Component> joinBrokenLines(List<Component> lines) {
        List<Component> sentences = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (Component line : lines) {
            if (!current.isEmpty()) current.append(' ');
            String text = line.getString().trim();
            current.append(text);
            if (SENTENCE_END.matcher(text).matches()) {
                sentences.add(dim(current.toString()));
                current.setLength(0);
            }
        }
        if (!current.isEmpty()) sentences.add(dim(current.toString()));
        return sentences;
    }

    private static Component dim(String text) {
        return Component.literal(text).withStyle(style -> style.withColor(Palette.TEXT_DIM));
    }

    // ---------------------------------------------------------------- Lecture

    /** Une ligne prête à dessiner, remise en texte avec ses styles (pour la relire et la remettre à nos couleurs). */
    private static Component text(ClientTextTooltip line) {
        FormattedCharSequence characters = ((ClientTextTooltipAccessor) line).kpitemsrarity$getText();
        MutableComponent out = Component.empty();
        StringBuilder run = new StringBuilder();
        Style[] runStyle = {Style.EMPTY};
        characters.accept((index, style, codePoint) -> {
            if (!style.equals(runStyle[0]) && !run.isEmpty()) {
                out.append(Component.literal(run.toString()).withStyle(runStyle[0]));
                run.setLength(0);
            }
            runStyle[0] = style;
            run.appendCodePoint(codePoint);
            return true;
        });
        if (!run.isEmpty()) out.append(Component.literal(run.toString()).withStyle(runStyle[0]));
        return out;
    }
}
