package net.Kyap.KPItemsRarity.client.tooltip.text;

import net.Kyap.KPItemsRarity.client.tooltip.card.Palette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.LingeringPotionItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.TippedArrowItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Map.entry;

/**
 * Remplit le texte d'une description. Les variables {xxx} sont lues SUR L'OBJET et surlignées : si un mod ou un
 * datapack change l'objet (effets d'une potion, nourriture...), la phrase suit toute seule.
 *
 *   {name}        nom de l'objet                  {mob}         créature qui sort de l'œuf
 *   {effects}     effets donnés, avec leur durée  {song}        musique du disque
 *   {nutrition}   points de faim rendus           {fluid}       contenu du seau
 *   {saturation}  saturation                      {fuel}        secondes de combustion dans un four
 *   {armor}       points d'armure                 {durability}  durabilité maximale
 *   {effect:<effet>,<niveau>,<secondes>}          un effet écrit à la main, quand le jeu le code en dur au lieu de
 *                                                 le mettre sur l'objet (le totem : {effect:regeneration,2,45})
 *
 * {effects} et {song} redisent ce que les lignes vanilla de l'objet disent déjà : ces lignes sont alors masquées.
 * Une variable inconnue reste écrite telle quelle, pour repérer la faute de frappe en jeu.
 */
final class DescriptionTemplate {
    private DescriptionTemplate() {}

    private static final int TEXT = Palette.TEXT_DIM;
    private static final int VALUE = Palette.TEXT;
    private static final Pattern VARIABLE = Pattern.compile("\\{([a-z_]+)(?::([^}]*))?}");
    private static final Component UNKNOWN = value("?");
    private static final FoodProperties NO_FOOD = new FoodProperties.Builder().build();
    /** Durée réelle des effets d'une flèche ou d'un nuage, par rapport à la potion (comme les lignes vanilla). */
    private static final float TIPPED_ARROW_DURATION = 0.125f, LINGERING_DURATION = 0.25f;

    /** Le texte rempli, et s'il redit les lignes vanilla de l'objet ({effects}, {song}). */
    record Filled(Component text, boolean coversVanillaLines) {}

    /** Une variable : sa valeur pour l'objet, avec l'argument écrit après ":" (vide s'il n'y en a pas). */
    private record Variable(boolean coversVanillaLines, BiFunction<ItemStack, String, Component> value) {}

    private static final Map<String, Variable> VARIABLES = Map.ofEntries(
            entry("name", read((stack, arg) -> highlighted(stack.getHoverName()))),
            entry("effects", new Variable(true, (stack, arg) -> effectList(effectsOf(stack), durationFactor(stack)))),
            entry("effect", read((stack, arg) -> writtenEffect(arg))),
            entry("nutrition", read((stack, arg) -> value(String.valueOf(food(stack).nutrition())))),
            entry("saturation", read((stack, arg) -> value(Texts.number(food(stack).saturation())))),
            entry("armor", read((stack, arg) -> value(String.valueOf(stack.getItem() instanceof ArmorItem armor ? armor.getDefense() : 0)))),
            entry("durability", read((stack, arg) -> value(String.valueOf(stack.getMaxDamage())))),
            entry("mob", read((stack, arg) -> stack.getItem() instanceof SpawnEggItem egg ? highlighted(egg.getType(stack).getDescription()) : UNKNOWN)),
            entry("song", new Variable(true, (stack, arg) -> song(stack))),
            entry("fluid", read((stack, arg) -> stack.getItem() instanceof BucketItem bucket ? highlighted(bucket.content.getFluidType().getDescription()) : UNKNOWN)),
            entry("fuel", read((stack, arg) -> value(Texts.number(stack.getBurnTime(RecipeType.SMELTING) / 20.0)))));

    private static Variable read(BiFunction<ItemStack, String, Component> value) {
        return new Variable(false, value);
    }

    static Filled fill(String template, ItemStack stack) {
        MutableComponent text = Component.empty();
        boolean coversVanillaLines = false;
        Matcher matcher = VARIABLE.matcher(template);
        int end = 0;
        while (matcher.find()) {
            text.append(plain(template.substring(end, matcher.start())));
            Variable variable = VARIABLES.get(matcher.group(1));
            if (variable == null) {
                text.append(value(matcher.group()));
            } else {
                String argument = matcher.group(2) == null ? "" : matcher.group(2);
                text.append(variable.value().apply(stack, argument));
                coversVanillaLines |= variable.coversVanillaLines();
            }
            end = matcher.end();
        }
        text.append(plain(template.substring(end)));
        return new Filled(text, coversVanillaLines);
    }

    // ---------------------------------------------------------------- Valeurs lues sur l'objet

    private static FoodProperties food(ItemStack stack) {
        FoodProperties food = stack.get(DataComponents.FOOD);
        return food != null ? food : NO_FOOD;
    }

    private static Component song(ItemStack stack) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return UNKNOWN;
        return JukeboxSong.fromStack(level.registryAccess(), stack).map(song -> highlighted(song.value().description())).orElse(UNKNOWN);
    }

    /** Tous les effets que l'objet donne : potion (et flèche), ragoût suspect, nourriture. */
    private static List<MobEffectInstance> effectsOf(ItemStack stack) {
        List<MobEffectInstance> effects = new ArrayList<>();
        PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
        if (potion != null) potion.getAllEffects().forEach(effects::add);
        SuspiciousStewEffects stew = stack.get(DataComponents.SUSPICIOUS_STEW_EFFECTS);
        if (stew != null) stew.effects().forEach(effect -> effects.add(effect.createEffectInstance()));
        food(stack).effects().forEach(effect -> effects.add(effect.effect()));
        return effects;
    }

    private static float durationFactor(ItemStack stack) {
        if (stack.getItem() instanceof TippedArrowItem) return TIPPED_ARROW_DURATION;
        if (stack.getItem() instanceof LingeringPotionItem) return LINGERING_DURATION;
        return 1f;
    }

    /** "{effect:regeneration,2,45}" : Régénération II pendant 45 secondes. */
    private static Component writtenEffect(String argument) {
        String[] parts = argument.split(",");
        ResourceLocation id = ResourceLocation.tryParse(parts[0].trim());
        Optional<Holder.Reference<MobEffect>> effect = id == null ? Optional.empty() : BuiltInRegistries.MOB_EFFECT.getHolder(id);
        int level = parts.length > 1 ? parseOr(parts[1], 0) : 1;
        int seconds = parts.length > 2 ? parseOr(parts[2], -1) : 0;
        if (effect.isEmpty() || level < 1 || seconds < 0) return value("{effect:" + argument + "}");
        return effectList(List.of(new MobEffectInstance(effect.get(), seconds * 20, level - 1)), 1f);
    }

    private static int parseOr(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ---------------------------------------------------------------- Mise en forme

    /** "Régénération II (0:45), Absorption II (0:05) et Résistance au feu (0:40)", chaque effet de sa couleur. */
    private static Component effectList(List<MobEffectInstance> effects, float durationFactor) {
        if (effects.isEmpty()) return Component.translatable("desc.kpitemsrarity.no_effect").withStyle(style -> style.withColor(VALUE));
        ClientLevel level = Minecraft.getInstance().level;
        float tickRate = level != null ? level.tickRateManager().tickrate() : 20f;
        MutableComponent out = Component.empty();
        for (int i = 0; i < effects.size(); i++) {
            if (i > 0) out.append(separator(i == effects.size() - 1));
            out.append(effect(effects.get(i), durationFactor, tickRate));
        }
        return out;
    }

    /** ", " entre deux effets, " et " avant le dernier. */
    private static Component separator(boolean beforeLast) {
        return beforeLast ? plain(" ").append(Component.translatable("desc.kpitemsrarity.and")).append(" ") : plain(", ");
    }

    /** "Régénération II (0:45)" : pas de durée pour un effet instantané ou d'une seconde. */
    private static Component effect(MobEffectInstance instance, float durationFactor, float tickRate) {
        MobEffect effect = instance.getEffect().value();
        int color = Palette.mix(effect.getColor(), 0xFFFFFF, 0.35f);
        MutableComponent text = Component.translatable(effect.getDescriptionId());
        if (instance.getAmplifier() > 0) text.append(" ").append(Component.translatable("enchantment.level." + (instance.getAmplifier() + 1)));
        text.withStyle(style -> style.withColor(color));
        if (!effect.isInstantenous() && instance.getDuration() > 20) {
            text.append(plain(" (")).append(MobEffectUtil.formatDuration(instance, durationFactor, tickRate).copy().withStyle(style -> style.withColor(VALUE))).append(plain(")"));
        }
        return text;
    }

    private static MutableComponent plain(String text) {
        return Component.literal(text).withStyle(style -> style.withColor(TEXT));
    }

    private static Component value(String text) {
        return Component.literal(text).withStyle(style -> style.withColor(VALUE));
    }

    private static Component highlighted(Component text) {
        return text.copy().withStyle(style -> style.withColor(VALUE));
    }
}
