package net.Kyap.ItemsRarity.item.custom;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Remplace BismuthItem / SulfurItem / FlouriteItem (identiques à part le texte).
 * Les chances d'amélioration ne sont plus codées ici : elles viennent de rarity_materials/*.json
 * et sont ajoutées au tooltip par TooltipHandler, pour n'importe quel matériau.
 */
public class UpgradeMaterialItem extends Item {
    private final String descriptionKey;
    private final int descriptionLines;

    public UpgradeMaterialItem(Properties properties, String name, int descriptionLines) {
        super(properties);
        this.descriptionKey = "item.itemsrarity." + name + ".desc";
        this.descriptionLines = descriptionLines;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        for (int i = 1; i <= descriptionLines; i++) {
            tooltip.add(Component.translatable(descriptionKey + i).withStyle(ChatFormatting.GRAY));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
