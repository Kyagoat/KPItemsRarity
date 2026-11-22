package net.Kyap.KPItemsRarity.client.tooltip.text;

import net.Kyap.KPItemsRarity.data.ItemDefinition;
import net.Kyap.KPItemsRarity.data.RarityData;
import net.Kyap.KPItemsRarity.stats.GearSlots;
import net.Kyap.KPItemsRarity.util.ModRarities.ModRarity;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.neoforged.neoforge.common.ItemAbilities;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Le type d'objet affiché sous le nom ("Épée", "Bague"...) et la rareté des objets hors du mod. */
public final class ItemTypes {
    private ItemTypes() {}

    /** Rareté vanilla (ou d'un autre mod) -> couleur du cadre. */
    public static ModRarity fromVanilla(Rarity rarity) {
        String name = rarity.name();
        if (name.contains("MYTHIC")) return ModRarity.MYTHIC;
        if (name.contains("LEGENDARY")) return ModRarity.LEGENDARY;
        return switch (rarity.ordinal()) {
            case 1 -> ModRarity.UNCOMMON;
            case 2 -> ModRarity.RARE;
            case 3 -> ModRarity.EPIC;
            default -> name.equals("COMMON") ? ModRarity.COMMON : ModRarity.RARE;
        };
    }

    /** Classes d'objet reconnues, dans l'ordre où on les teste (la première qui correspond donne le type). */
    private record ByClass(Class<? extends Item> type, String key) {}

    private static final List<ByClass> WEAPONS_AND_TOOLS = List.of(
            new ByClass(SwordItem.class, "sword"), new ByClass(AxeItem.class, "axe"), new ByClass(PickaxeItem.class, "pickaxe"),
            new ByClass(ShovelItem.class, "shovel"), new ByClass(HoeItem.class, "hoe"), new ByClass(MaceItem.class, "mace"),
            new ByClass(TridentItem.class, "trident"), new ByClass(CrossbowItem.class, "crossbow"), new ByClass(BowItem.class, "bow"),
            new ByClass(FishingRodItem.class, "fishing_rod"));

    private static final List<ByClass> OTHERS = List.of(
            new ByClass(EnchantedBookItem.class, "enchanted_book"), new ByClass(PotionItem.class, "potion"),
            new ByClass(SpawnEggItem.class, "spawn_egg"));

    /** "Épée", "Casque", "Bague", "Nourriture"... (clés type.kpitemsrarity.*). */
    public static Component typeName(ItemStack stack) {
        return Component.translatable("type.kpitemsrarity." + typeKey(stack));
    }

    private static String typeKey(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof ArmorItem armor) return armorKey(armor);
        String key = byClass(item, WEAPONS_AND_TOOLS);
        if (key != null) return key;
        if (item instanceof ShieldItem || stack.canPerformAction(ItemAbilities.SHIELD_BLOCK)) return "shield";
        key = byClass(item, OTHERS);
        if (key != null) return key;
        if (stack.has(DataComponents.FOOD)) return "food";
        if (stack.has(DataComponents.JUKEBOX_PLAYABLE)) return "music_disc";
        if (item instanceof BlockItem) return "block";
        if (stack.has(DataComponents.TOOL)) return "tool";
        ItemDefinition def = RarityData.itemDef(stack);
        if (def != null) return def.slot != null && GearSlots.ACCESSORY_SLOTS.contains(def.slot) ? def.slot : "gear";
        return "material";
    }

    private static String armorKey(ArmorItem armor) {
        return switch (armor.getType()) {
            case HELMET -> "helmet";
            case CHESTPLATE -> "chestplate";
            case LEGGINGS -> "leggings";
            case BOOTS -> "boots";
            default -> "armor";
        };
    }

    @Nullable
    private static String byClass(Item item, List<ByClass> table) {
        for (ByClass entry : table) if (entry.type().isInstance(item)) return entry.key();
        return null;
    }
}
