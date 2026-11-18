package net.Kyap.ItemsRarity.item.custom;

import net.Kyap.ItemsRarity.component.ModDataComponents;
import net.Kyap.ItemsRarity.component.RuneData;
import net.Kyap.ItemsRarity.data.RarityData;
import net.Kyap.ItemsRarity.data.StatDefinition;
import net.Kyap.ItemsRarity.forge.ForgeMagic;
import net.Kyap.ItemsRarity.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Rune de forgemagie : "Rune de Force", "Rune Pa de Force", "Rune Ra de Force"... (voir RuneData). */
public class RuneItem extends Item {
    public RuneItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(String stat, int power, int count) {
        ItemStack stack = new ItemStack(ModItems.RUNE.get(), count);
        stack.set(ModDataComponents.RUNE.get(), new RuneData(stat, power));
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        RuneData rune = stack.get(ModDataComponents.RUNE.get());
        StatDefinition stat = rune != null ? RarityData.stat(rune.stat()) : null;
        if (stat == null) return Component.translatable("item.itemsrarity.rune.unknown");
        return Component.translatable("item.itemsrarity.rune." + rune.sizeKey(), stat.name())
                .withStyle(s -> s.withColor(stat.rgb()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        RuneData rune = stack.get(ModDataComponents.RUNE.get());
        StatDefinition stat = rune != null ? RarityData.stat(rune.stat()) : null;
        if (stat == null) return;
        int points = ForgeMagic.runePoints(stat, rune.power());
        tooltip.add(Component.translatable("item.itemsrarity.rune.desc", stat.formatValue(points, true), stat.name())
                .withStyle(ChatFormatting.GRAY));
    }

    /** Couleur de la gravure (calque 1 du modèle) : celle de la stat. */
    public static int tint(ItemStack stack) {
        RuneData rune = stack.get(ModDataComponents.RUNE.get());
        StatDefinition stat = rune != null ? RarityData.stat(rune.stat()) : null;
        return stat != null ? 0xFF000000 | stat.rgb() : 0xFFFFFFFF;
    }
}
