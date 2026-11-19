package net.Kyap.KPItemsRarity.client.tooltip;

import net.Kyap.KPItemsRarity.client.tooltip.TooltipModel.Elem;
import net.Kyap.KPItemsRarity.client.tooltip.TooltipModel.Row;
import net.Kyap.KPItemsRarity.client.tooltip.TooltipModel.RowElem;
import net.Kyap.KPItemsRarity.events.AttributeEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Ce qui vient du vanilla et des autres mods : nourriture, attributs restants, lignes d'infobulle étrangères. */
final class VanillaRows {
    private VanillaRows() {}

    /** Nourriture : ce qu'elle rend. */
    static void foodRows(ItemStack stack, List<Elem> body) {
        var food = stack.get(DataComponents.FOOD);
        if (food == null) return;
        body.add(new RowElem(new Row(Component.literal("+" + food.nutrition() + " ").withStyle(s -> s.withColor(RpgFrame.POSITIVE))
                .append(Component.translatable("tooltip.kpitemsrarity.rpg.nutrition").withStyle(s -> s.withColor(RpgFrame.TEXT_SOFT))),
                Component.translatable("tooltip.kpitemsrarity.rpg.saturation", fmt(food.saturation())).withStyle(s -> s.withColor(RpgFrame.TEXT_FAINT)), 0)));
    }

    static double sum(ItemAttributeModifiers mods, Holder<Attribute> attribute, AttributeModifier.Operation op) {
        double total = 0;
        for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
            if (e.attribute().equals(attribute) && e.modifier().operation() == op) total += e.modifier().amount();
        }
        return total;
    }

    static String fmt(double v) {
        return Math.abs(v - Math.rint(v)) < 0.05 ? String.valueOf((long) Math.rint(v)) : String.format(Locale.ROOT, "%.1f", v);
    }

    /** Attributs vanilla restants (robustesse, recul...) : ni les nôtres, ni dégâts/vitesse/armure (déjà en haut). */
    static List<Row> vanillaAttributeRows(ItemStack stack) {
        List<Row> rows = new ArrayList<>();
        for (ItemAttributeModifiers.Entry e : stack.getAttributeModifiers().modifiers()) {
            if (AttributeEvents.isOurModifier(e.modifier().id())) continue;
            Holder<Attribute> attr = e.attribute();
            if (attr.equals(Attributes.ATTACK_DAMAGE) || attr.equals(Attributes.ATTACK_SPEED) || attr.equals(Attributes.ARMOR)) continue;
            double amount = e.modifier().amount();
            if (amount == 0) continue;
            String text = switch (e.modifier().operation()) {
                case ADD_VALUE -> (amount > 0 ? "+" : "") + fmt(attr.equals(Attributes.KNOCKBACK_RESISTANCE) ? amount * 10 : amount);
                default -> (amount > 0 ? "+" : "") + fmt(amount * 100) + " %";
            };
            int color = amount > 0 ? RpgFrame.mix(RpgFrame.POSITIVE, 0x8FB8FF, 0.5f) : RpgFrame.NEGATIVE;
            Component left = Component.literal(text + " ").withStyle(s -> s.withColor(color))
                    .append(Component.translatable(attr.value().getDescriptionId()).withStyle(s -> s.withColor(RpgFrame.TEXT_SOFT)));
            rows.add(new Row(left, Component.empty(), 0));
        }
        return rows;
    }

    /**
     * Ce qui reste de l'infobulle vanilla une fois retirés : le nom, nos lignes, les enchantements,
     * le bloc des attributs ("Dans la main principale : ...") et la durabilité (affichés autrement).
     */
    static List<Component> extraLines(List<Component> vanilla, ItemStack stack, Set<String> enchantTexts) {
        Set<String> attributeHeaders = new HashSet<>();
        for (EquipmentSlotGroup group : EquipmentSlotGroup.values()) {
            attributeHeaders.add(Component.translatable("item.modifiers." + group.getSerializedName()).getString());
        }
        String durability = stack.isDamageableItem()
                ? Component.translatable("item.durability", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage()).getString()
                : null;

        List<Component> out = new ArrayList<>();
        boolean inAttributes = false;
        for (int i = 1; i < vanilla.size(); i++) {
            Component c = vanilla.get(i);
            if (TooltipHandler.isOurs(c)) continue;
            String s = c.getString();
            if (attributeHeaders.contains(s)) {
                inAttributes = true;
                while (!out.isEmpty() && out.get(out.size() - 1).getString().isBlank()) out.remove(out.size() - 1);
                continue;
            }
            if (inAttributes) {
                if (s.startsWith(" ")) continue;
                inAttributes = false;
            }
            if (enchantTexts.contains(s) || s.equals(durability)) continue;
            out.add(c);
        }
        while (!out.isEmpty() && out.get(0).getString().isBlank()) out.remove(0);
        while (!out.isEmpty() && out.get(out.size() - 1).getString().isBlank()) out.remove(out.size() - 1);
        return out;
    }
}
