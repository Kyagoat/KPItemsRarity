package net.Kyap.ItemsRarity.client;

import net.Kyap.ItemsRarity.ItemsRarity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.EntityBlock;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Petite description de N'IMPORTE QUEL objet, affichée dans l'en-tête de l'infobulle.
 *
 * 1. Description écrite à la main : clé de langue "desc.<mod>.<objet>" (ex. desc.minecraft.totem_of_undying).
 * 2. Sinon, son lore ("lore.<mod>.<objet>", voir LoreText).
 * 3. Sinon, une description automatique selon la catégorie de l'objet (desc.itemsrarity.auto.<catégorie>).
 *
 * Les textes peuvent contenir des variables, lues SUR L'OBJET et surlignées. Si un mod ou un datapack change
 * l'objet (effets d'une potion, nourriture...), la description suit toute seule :
 *   {effects}     effets donnés (potion, flèche, ragoût, nourriture, totem) avec leur durée
 *   {nutrition}   points de faim rendus        {saturation}  saturation
 *   {armor}       points d'armure              {durability}  durabilité maximale
 *   {mob}         créature d'un œuf            {song}        musique d'un disque
 *   {fluid}       contenu d'un seau            {fuel}        secondes de combustion dans un four
 *   {name}        nom de l'objet
 * Tout se modifie dans assets/itemsrarity/lang/*.json, sans toucher au code.
 */
public final class ItemDescriptions {
    private ItemDescriptions() {}

    /** Couleur du texte normal et des valeurs surlignées. */
    private static final int TEXT = RpgFrame.TEXT_DIM; // explications un peu grisées
    private static final int VALUE = RpgFrame.TEXT;

    /** Le totem de vanilla : effets codés en dur dans le jeu (LivingEntity.checkTotemDeathProtection). */
    private static List<MobEffectInstance> totemEffects() {
        return List.of(new MobEffectInstance(MobEffects.REGENERATION, 900, 1),
                new MobEffectInstance(MobEffects.ABSORPTION, 100, 1),
                new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
    }

    /**
     * @param replacesVanilla vrai quand la description reprend déjà ce que les lignes vanilla disent
     *                        (effets d'une potion, musique d'un disque) : ces lignes sont alors masquées.
     */
    public record Description(List<Component> parts, boolean replacesVanilla, boolean auto) {
        Description(Component text, boolean replacesVanilla, boolean auto) {
            this(List.of(text), replacesVanilla, auto);
        }
    }

    @Nullable
    public static Description describe(ItemStack stack) {
        Language lang = Language.getInstance();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String own = "desc." + id.getNamespace() + "." + id.getPath();
        boolean hasOwn = lang.has(own), hasLore = lang.has(LoreText.itemKey(stack));
        // Explication ET lore quand les deux existent (ex. totem : ce qu'il fait, puis sa légende).
        if (hasOwn || hasLore) {
            List<Component> parts = new ArrayList<>();
            boolean replaces = false;
            if (hasOwn) {
                Description d = build(stack, pick(stack, lang.getOrDefault(own)), false);
                parts.addAll(d.parts());
                replaces = d.replacesVanilla();
            } else {
                // Lore seul : on garde quand même la ligne utile automatique (effets, faim, durée de combustion...)
                String auto = autoCategory(stack);
                String key = "desc." + ItemsRarity.MOD_ID + ".auto." + auto;
                if (auto != null && INFORMATIVE.contains(auto) && lang.has(key)) {
                    Description d = build(stack, pick(stack, lang.getOrDefault(key)), true);
                    parts.addAll(d.parts());
                    replaces = d.replacesVanilla();
                }
            }
            if (hasLore) parts.add(LoreText.item(stack, null));
            return new Description(parts, replaces, false);
        }
        String auto = autoCategory(stack);
        if (auto == null) return null;
        String key = "desc." + ItemsRarity.MOD_ID + ".auto." + auto;
        return lang.has(key) ? build(stack, pick(stack, lang.getOrDefault(key)), true) : null;
    }

    /**
     * Plusieurs variantes séparées par "|" : chaque objet en garde une, toujours la même
     * (deux poissons différents n'ont pas forcément la même phrase).
     */
    private static String pick(ItemStack stack, String text) {
        if (!text.contains("|")) return text;
        String[] options = text.split("\\|");
        int h = BuiltInRegistries.ITEM.getKey(stack.getItem()).hashCode();
        return options[Math.floorMod(h, options.length)].trim();
    }

    /** Catégories automatiques qui donnent une info chiffrée : gardées même quand l'objet a son propre lore. */
    private static final java.util.Set<String> INFORMATIVE = java.util.Set.of("food_effects", "potion", "splash_potion",
            "lingering_potion", "tipped_arrow", "fuel", "music_disc", "bucket");

    private static final net.minecraft.tags.TagKey<Item> RAW_FISH = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("c", "foods/raw_fish"));
    private static final net.minecraft.tags.TagKey<Item> TIDE_FISH = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("tide", "fish"));

    // ---------------------------------------------------------------- Catégories automatiques

    @Nullable
    private static String autoCategory(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof SpawnEggItem) return "spawn_egg";
        if (stack.has(DataComponents.JUKEBOX_PLAYABLE)) return "music_disc";
        if (item instanceof TippedArrowItem) return "tipped_arrow";
        if (item instanceof LingeringPotionItem) return "lingering_potion";
        if (item instanceof SplashPotionItem) return "splash_potion";
        if (item instanceof PotionItem) return "potion";
        if (stack.has(DataComponents.SUSPICIOUS_STEW_EFFECTS)) return "food_effects";
        FoodProperties food = stack.get(DataComponents.FOOD);
        // Se mange ET sert d'arme (ex. l'espadon de Tide) : une phrase qui le dit, pas juste "poisson".
        if (food != null && (item instanceof SwordItem || item instanceof AxeItem || item instanceof TridentItem || item instanceof MaceItem)) return "edible_weapon";
        if (stack.is(ItemTags.FISHES) || stack.is(RAW_FISH) || stack.is(TIDE_FISH)) return food != null && !food.effects().isEmpty() ? "food_effects" : "fish";
        if (food != null) return food.effects().isEmpty() ? "food" : "food_effects";
        if (item instanceof AnimalArmorItem animal) return animal.getBodyType() == AnimalArmorItem.BodyType.CANINE ? "wolf_armor" : "horse_armor";
        if (item instanceof ArmorItem) return "armor";
        if (item instanceof SwordItem) return "sword";
        if (item instanceof PickaxeItem) return "pickaxe";
        if (item instanceof AxeItem) return "axe";
        if (item instanceof ShovelItem) return "shovel";
        if (item instanceof HoeItem) return "hoe";
        if (item instanceof BowItem || item instanceof CrossbowItem) return "ranged";
        if (item instanceof ArrowItem) return "arrow";
        if (item instanceof MobBucketItem) return "mob_bucket";
        if (item instanceof BucketItem bucket) return bucket.content == net.minecraft.world.level.material.Fluids.EMPTY ? null : "bucket";
        if (item instanceof EnchantedBookItem) return "enchanted_book";
        if (item instanceof SmithingTemplateItem) return "smithing_template";
        if (item instanceof BannerPatternItem) return "banner_pattern";
        if (item instanceof DyeItem) return "dye";
        if (item instanceof BlockItem block) return blockCategory(stack, block);
        if (stack.is(Tags.Items.INGOTS)) return "ingot";
        if (stack.is(Tags.Items.NUGGETS)) return "nugget";
        if (stack.is(Tags.Items.GEMS)) return "gem";
        if (stack.is(Tags.Items.DUSTS)) return "dust";
        if (stack.is(Tags.Items.SEEDS)) return "seeds";
        if (stack.is(Tags.Items.RAW_MATERIALS)) return "raw_material";
        if (stack.getBurnTime(RecipeType.SMELTING) > 0) return "fuel";
        if (stack.isDamageableItem()) return "tool";
        return "material";
    }

    private static String blockCategory(ItemStack stack, BlockItem item) {
        var block = item.getBlock().defaultBlockState();
        if (stack.is(Tags.Items.ORES)) return "ore";
        if (stack.is(ItemTags.LOGS)) return "log";
        if (stack.is(ItemTags.PLANKS)) return "planks";
        if (stack.is(ItemTags.LEAVES)) return "leaves";
        if (stack.is(ItemTags.SAPLINGS)) return "sapling";
        if (stack.is(ItemTags.FLOWERS)) return "flower";
        if (stack.is(ItemTags.WOOL)) return "wool";
        if (stack.is(ItemTags.BEDS)) return "bed";
        if (stack.is(Tags.Items.GLASS_BLOCKS) || stack.is(Tags.Items.GLASS_PANES)) return "glass";
        if (stack.is(Tags.Items.STORAGE_BLOCKS)) return "storage_block";
        if (stack.is(ItemTags.DOORS) || stack.is(ItemTags.TRAPDOORS) || block.is(BlockTags.FENCE_GATES)) return "door";
        if (stack.is(ItemTags.STAIRS) || stack.is(ItemTags.SLABS) || stack.is(ItemTags.WALLS) || stack.is(ItemTags.FENCES)) return "building_shape";
        if (stack.is(ItemTags.RAILS)) return "rail";
        if (stack.is(ItemTags.SIGNS) || stack.is(ItemTags.HANGING_SIGNS)) return "sign";
        if (stack.is(ItemTags.CANDLES)) return "candle";
        if (stack.is(Tags.Items.CHESTS) || stack.is(Tags.Items.BARRELS) || stack.is(Tags.Items.SHULKER_BOXES)) return "storage";
        if (item.getBlock() instanceof EntityBlock) return "utility_block";
        return "block";
    }

    // ---------------------------------------------------------------- Variables

    private static final Pattern VAR = Pattern.compile("\\{([a-z_]+)}");

    private static Description build(ItemStack stack, String template, boolean auto) {
        MutableComponent out = Component.empty();
        Matcher m = VAR.matcher(template);
        int last = 0;
        boolean replaces = false;
        while (m.find()) {
            if (m.start() > last) out.append(text(template.substring(last, m.start())));
            String var = m.group(1);
            if (var.equals("effects") || var.equals("song")) replaces = true;
            out.append(variable(stack, var));
            last = m.end();
        }
        if (last < template.length()) out.append(text(template.substring(last)));
        return new Description(out, replaces, auto);
    }

    private static Component text(String s) {
        return Component.literal(s).withStyle(st -> st.withColor(TEXT));
    }

    private static Component value(String s) {
        return Component.literal(s).withStyle(st -> st.withColor(VALUE));
    }

    private static Component variable(ItemStack stack, String var) {
        Item item = stack.getItem();
        switch (var) {
            case "name":
                return stack.getHoverName().copy().withStyle(st -> st.withColor(VALUE));
            case "effects":
                return effects(effectsOf(stack), item instanceof TippedArrowItem ? 0.125f : 1.0f);
            case "nutrition": {
                FoodProperties food = stack.get(DataComponents.FOOD);
                return value(food != null ? String.valueOf(food.nutrition()) : "0");
            }
            case "saturation": {
                FoodProperties food = stack.get(DataComponents.FOOD);
                return value(food != null ? fmt(food.saturation()) : "0");
            }
            case "armor":
                return value(item instanceof ArmorItem armor ? String.valueOf(armor.getDefense()) : "0");
            case "durability":
                return value(String.valueOf(stack.getMaxDamage()));
            case "mob":
                return item instanceof SpawnEggItem egg
                        ? egg.getType(stack).getDescription().copy().withStyle(st -> st.withColor(VALUE))
                        : value("?");
            case "song": {
                var level = Minecraft.getInstance().level;
                if (level != null) {
                    var song = JukeboxSong.fromStack(level.registryAccess(), stack);
                    if (song.isPresent()) return song.get().value().description().copy().withStyle(st -> st.withColor(VALUE));
                }
                return value("?");
            }
            case "fluid":
                return item instanceof BucketItem bucket
                        ? bucket.content.getFluidType().getDescription().copy().withStyle(st -> st.withColor(VALUE))
                        : value("?");
            case "fuel":
                return value(fmt(stack.getBurnTime(RecipeType.SMELTING) / 20.0));
            default:
                return value("{" + var + "}");
        }
    }

    /** Tous les effets que l'objet donne. */
    private static List<MobEffectInstance> effectsOf(ItemStack stack) {
        List<MobEffectInstance> list = new ArrayList<>();
        if (stack.is(Items.TOTEM_OF_UNDYING)) return totemEffects();
        PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
        if (potion != null) potion.getAllEffects().forEach(list::add);
        SuspiciousStewEffects stew = stack.get(DataComponents.SUSPICIOUS_STEW_EFFECTS);
        if (stew != null) stew.effects().forEach(e -> list.add(e.createEffectInstance()));
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food != null) food.effects().forEach(e -> list.add(e.effect()));
        return list;
    }

    /** "Régénération II (0:45), Absorption II (0:05) et Résistance au feu (0:40)". */
    private static Component effects(List<MobEffectInstance> effects, float durationFactor) {
        if (effects.isEmpty()) return Component.translatable("desc.itemsrarity.no_effect").withStyle(st -> st.withColor(VALUE));
        float tickRate = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.tickRateManager().tickrate() : 20f;
        MutableComponent out = Component.empty();
        for (int i = 0; i < effects.size(); i++) {
            if (i > 0) out.append(text(i == effects.size() - 1 ? " " + Language.getInstance().getOrDefault("desc.itemsrarity.and", "and") + " " : ", "));
            MobEffectInstance inst = effects.get(i);
            Holder<MobEffect> effect = inst.getEffect();
            int color = RpgFrame.mix(effect.value().getColor(), 0xFFFFFF, 0.35f);
            MutableComponent name = Component.translatable(effect.value().getDescriptionId());
            if (inst.getAmplifier() > 0) name.append(" ").append(Component.translatable("enchantment.level." + (inst.getAmplifier() + 1)));
            out.append(name.withStyle(st -> st.withColor(color)));
            if (!effect.value().isInstantenous() && inst.getDuration() > 20) {
                out.append(text(" ("));
                out.append(MobEffectUtil.formatDuration(inst, durationFactor, tickRate).copy().withStyle(st -> st.withColor(VALUE)));
                out.append(text(")"));
            }
        }
        return out;
    }

    private static String fmt(double v) {
        return Math.abs(v - Math.rint(v)) < 0.05 ? String.valueOf((long) Math.rint(v)) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }
}
