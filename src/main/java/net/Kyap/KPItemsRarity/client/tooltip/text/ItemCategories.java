package net.Kyap.KPItemsRarity.client.tooltip.text;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BannerPatternItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.LingeringPotionItem;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TippedArrowItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.Tags;

import java.util.List;

import static net.Kyap.KPItemsRarity.client.tooltip.text.ItemRule.has;
import static net.Kyap.KPItemsRarity.client.tooltip.text.ItemRule.isA;
import static net.Kyap.KPItemsRarity.client.tooltip.text.ItemRule.tagged;

/**
 * La catégorie d'un objet sans description écrite à la main : elle choisit sa phrase automatique
 * "desc.kpitemsrarity.auto.<catégorie>" dans les fichiers de langue.
 *
 * Les règles se lisent de haut en bas et la première qui correspond gagne : les plus précises d'abord (une flèche
 * à effet avant une flèche, une nourriture à effet avant une nourriture). Une catégorie sans phrase dans la langue
 * n'affiche rien (le seau vide).
 */
final class ItemCategories {
    private ItemCategories() {}

    private static final TagKey<Item> RAW_FISH = itemTag("c", "foods/raw_fish");
    private static final TagKey<Item> TIDE_FISH = itemTag("tide", "fish");

    private static final List<ItemRule> RULES = List.of(
            // Ce que l'objet contient : la phrase le lit sur l'objet ({mob}, {song}, {effects}).
            new ItemRule("spawn_egg", isA(SpawnEggItem.class)),
            new ItemRule("music_disc", has(DataComponents.JUKEBOX_PLAYABLE)),
            new ItemRule("tipped_arrow", isA(TippedArrowItem.class)),
            new ItemRule("lingering_potion", isA(LingeringPotionItem.class)),
            new ItemRule("splash_potion", isA(SplashPotionItem.class)),
            new ItemRule("potion", isA(PotionItem.class)),

            // Ce qui se mange.
            new ItemRule("food_effects", has(DataComponents.SUSPICIOUS_STEW_EFFECTS)),
            new ItemRule("edible_weapon", has(DataComponents.FOOD).and(isA(SwordItem.class, AxeItem.class, TridentItem.class, MaceItem.class))),
            new ItemRule("food_effects", ItemCategories::foodWithEffects),
            new ItemRule("fish", tagged(ItemTags.FISHES, RAW_FISH, TIDE_FISH)),
            new ItemRule("food", has(DataComponents.FOOD)),

            // Équipement.
            new ItemRule("wolf_armor", ItemCategories::wolfArmor),
            new ItemRule("horse_armor", isA(AnimalArmorItem.class)),
            new ItemRule("armor", isA(ArmorItem.class)),
            new ItemRule("sword", isA(SwordItem.class)),
            new ItemRule("pickaxe", isA(PickaxeItem.class)),
            new ItemRule("axe", isA(AxeItem.class)),
            new ItemRule("shovel", isA(ShovelItem.class)),
            new ItemRule("hoe", isA(HoeItem.class)),
            new ItemRule("ranged", isA(BowItem.class, CrossbowItem.class)),
            new ItemRule("arrow", isA(ArrowItem.class)),

            // Objets à usage particulier.
            new ItemRule("mob_bucket", isA(MobBucketItem.class)),
            new ItemRule("empty_bucket", ItemCategories::emptyBucket),
            new ItemRule("bucket", isA(BucketItem.class)),
            new ItemRule("enchanted_book", isA(EnchantedBookItem.class)),
            new ItemRule("smithing_template", isA(SmithingTemplateItem.class)),
            new ItemRule("banner_pattern", isA(BannerPatternItem.class)),
            new ItemRule("dye", isA(DyeItem.class)),

            // Matériaux, avant les blocs : les graines et la poudre de redstone sont des objets-blocs.
            new ItemRule("ingot", tagged(Tags.Items.INGOTS)),
            new ItemRule("nugget", tagged(Tags.Items.NUGGETS)),
            new ItemRule("gem", tagged(Tags.Items.GEMS)),
            new ItemRule("dust", tagged(Tags.Items.DUSTS)),
            new ItemRule("seeds", tagged(Tags.Items.SEEDS)),
            new ItemRule("raw_material", tagged(Tags.Items.RAW_MATERIALS)),

            // Blocs.
            new ItemRule("ore", tagged(Tags.Items.ORES)),
            new ItemRule("log", tagged(ItemTags.LOGS)),
            new ItemRule("planks", tagged(ItemTags.PLANKS)),
            new ItemRule("leaves", tagged(ItemTags.LEAVES)),
            new ItemRule("sapling", tagged(ItemTags.SAPLINGS)),
            new ItemRule("flower", tagged(ItemTags.FLOWERS)),
            new ItemRule("wool", tagged(ItemTags.WOOL)),
            new ItemRule("bed", tagged(ItemTags.BEDS)),
            new ItemRule("glass", tagged(Tags.Items.GLASS_BLOCKS, Tags.Items.GLASS_PANES)),
            new ItemRule("storage_block", tagged(Tags.Items.STORAGE_BLOCKS)),
            new ItemRule("door", tagged(ItemTags.DOORS, ItemTags.TRAPDOORS, ItemTags.FENCE_GATES)),
            new ItemRule("building_shape", tagged(ItemTags.STAIRS, ItemTags.SLABS, ItemTags.WALLS, ItemTags.FENCES)),
            new ItemRule("rail", tagged(ItemTags.RAILS)),
            new ItemRule("sign", tagged(ItemTags.SIGNS, ItemTags.HANGING_SIGNS)),
            new ItemRule("candle", tagged(ItemTags.CANDLES)),
            new ItemRule("storage", tagged(Tags.Items.CHESTS, Tags.Items.BARRELS, Tags.Items.SHULKER_BOXES)),
            new ItemRule("utility_block", ItemCategories::blockWithMachine),
            new ItemRule("block", isA(BlockItem.class)),

            // Tout le reste.
            new ItemRule("fuel", stack -> stack.getBurnTime(RecipeType.SMELTING) > 0),
            new ItemRule("tool", ItemStack::isDamageableItem),
            new ItemRule("material", stack -> true));

    /** La catégorie de l'objet (il y en a toujours une : "material" en dernier recours). */
    static String of(ItemStack stack) {
        return ItemRule.first(RULES, stack);
    }

    private static boolean foodWithEffects(ItemStack stack) {
        FoodProperties food = stack.get(DataComponents.FOOD);
        return food != null && !food.effects().isEmpty();
    }

    private static boolean wolfArmor(ItemStack stack) {
        return stack.getItem() instanceof AnimalArmorItem armor && armor.getBodyType() == AnimalArmorItem.BodyType.CANINE;
    }

    private static boolean emptyBucket(ItemStack stack) {
        return stack.getItem() instanceof BucketItem bucket && bucket.content == Fluids.EMPTY;
    }

    /** Un bloc qui garde un contenu ou un état (four, table d'enchantement, ruche...). */
    private static boolean blockWithMachine(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof EntityBlock;
    }

    private static TagKey<Item> itemTag(String namespace, String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(namespace, path));
    }
}
