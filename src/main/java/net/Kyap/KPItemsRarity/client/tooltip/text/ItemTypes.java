package net.Kyap.KPItemsRarity.client.tooltip.text;

import net.Kyap.KPItemsRarity.data.ItemDefinition;
import net.Kyap.KPItemsRarity.data.RarityData;
import net.Kyap.KPItemsRarity.stats.GearSlots;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.neoforged.neoforge.common.ItemAbilities;

import java.util.List;
import java.util.function.Predicate;

import static net.Kyap.KPItemsRarity.client.tooltip.text.ItemRule.has;
import static net.Kyap.KPItemsRarity.client.tooltip.text.ItemRule.isA;

/**
 * Le type d'objet écrit sous son nom : "Épée", "Casque", "Bague", "Nourriture"... (clés type.kpitemsrarity.<type>).
 * Les règles se lisent de haut en bas, la première qui correspond gagne.
 */
public final class ItemTypes {
    private ItemTypes() {}

    private static final List<ItemRule> RULES = List.of(
            new ItemRule("helmet", armor(ArmorItem.Type.HELMET)),
            new ItemRule("chestplate", armor(ArmorItem.Type.CHESTPLATE)),
            new ItemRule("leggings", armor(ArmorItem.Type.LEGGINGS)),
            new ItemRule("boots", armor(ArmorItem.Type.BOOTS)),
            new ItemRule("armor", isA(ArmorItem.class)), // armure de loup ou de cheval
            new ItemRule("sword", isA(SwordItem.class)),
            new ItemRule("axe", isA(AxeItem.class)),
            new ItemRule("pickaxe", isA(PickaxeItem.class)),
            new ItemRule("shovel", isA(ShovelItem.class)),
            new ItemRule("hoe", isA(HoeItem.class)),
            new ItemRule("mace", isA(MaceItem.class)),
            new ItemRule("trident", isA(TridentItem.class)),
            new ItemRule("crossbow", isA(CrossbowItem.class)),
            new ItemRule("bow", isA(BowItem.class)),
            new ItemRule("fishing_rod", isA(FishingRodItem.class)),
            new ItemRule("shield", isA(ShieldItem.class).or(stack -> stack.canPerformAction(ItemAbilities.SHIELD_BLOCK))),
            new ItemRule("enchanted_book", isA(EnchantedBookItem.class)),
            new ItemRule("potion", isA(PotionItem.class)),
            new ItemRule("spawn_egg", isA(SpawnEggItem.class)),
            new ItemRule("food", has(DataComponents.FOOD)),
            new ItemRule("music_disc", has(DataComponents.JUKEBOX_PLAYABLE)),
            new ItemRule("block", isA(BlockItem.class)),
            new ItemRule("tool", has(DataComponents.TOOL)));

    public static Component typeName(ItemStack stack) {
        return Component.translatable("type.kpitemsrarity." + typeKey(stack));
    }

    private static String typeKey(ItemStack stack) {
        String type = ItemRule.first(RULES, stack);
        if (type != null) return type;
        // Équipement du mod sans classe vanilla : son emplacement d'accessoire ("ring"...), sinon "Équipement".
        ItemDefinition def = RarityData.itemDef(stack);
        if (def != null) return def.slot != null && GearSlots.ACCESSORY_SLOTS.contains(def.slot) ? def.slot : "gear";
        return "material";
    }

    private static Predicate<ItemStack> armor(ArmorItem.Type piece) {
        return stack -> stack.getItem() instanceof ArmorItem armor && armor.getType() == piece;
    }
}
