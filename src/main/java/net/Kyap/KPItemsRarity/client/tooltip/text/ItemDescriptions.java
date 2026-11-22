package net.Kyap.KPItemsRarity.client.tooltip.text;

import net.Kyap.KPItemsRarity.KPItemsRarity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * La description d'un objet qui n'est pas un équipement du mod (matériau, bloc, nourriture...), en bas de son
 * infobulle. Tout est dans les fichiers de langue, rien à toucher dans le code :
 *
 *   1. écrite à la main : "desc.<mod>.<objet>" (desc.minecraft.totem_of_undying), suivie de son lore s'il en a un ;
 *   2. sinon son lore ("lore.<mod>.<objet>", voir LoreText), précédé de la phrase automatique de sa catégorie
 *      quand celle-ci lit une valeur sur l'objet (effets, faim, combustion...) ;
 *   3. sinon la phrase automatique de sa catégorie : "desc.kpitemsrarity.auto.<catégorie>" (voir ItemCategories).
 *
 * Variables des phrases : voir DescriptionTemplate. Une phrase peut avoir des variantes séparées par "|" : chaque
 * objet en garde une, toujours la même (deux poissons n'ont pas forcément la même phrase).
 */
public final class ItemDescriptions {
    private ItemDescriptions() {}

    private static final String AUTOMATIC_PREFIX = "desc." + KPItemsRarity.MOD_ID + ".auto.";

    /**
     * @param coversVanillaLines la description redit déjà les lignes vanilla (effets d'une potion, musique d'un
     *                           disque) : ces lignes sont alors masquées
     * @param automatic          phrase de catégorie : elle s'efface devant le texte que le mod de l'objet écrit déjà
     */
    public record Description(List<Component> lines, boolean coversVanillaLines, boolean automatic) {}

    /** La description de l'objet, ou null s'il n'y a rien à dire. */
    @Nullable
    public static Description describe(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String writtenKey = "desc." + id.getNamespace() + "." + id.getPath();
        boolean written = Language.getInstance().has(writtenKey);
        Component lore = LoreText.item(stack, null);
        if (!written && lore == null) return automatic(stack);

        DescriptionTemplate.Filled explanation = written ? fill(writtenKey, stack) : automaticWithValues(stack);
        List<Component> lines = new ArrayList<>();
        if (explanation != null) lines.add(explanation.text());
        if (lore != null) lines.add(lore);
        return new Description(lines, explanation != null && explanation.coversVanillaLines(), false);
    }

    @Nullable
    private static Description automatic(ItemStack stack) {
        String key = AUTOMATIC_PREFIX + ItemCategories.of(stack);
        if (!Language.getInstance().has(key)) return null;
        DescriptionTemplate.Filled filled = fill(key, stack);
        return new Description(List.of(filled.text()), filled.coversVanillaLines(), true);
    }

    /** La phrase automatique, seulement si elle lit une valeur sur l'objet (elle a une variable). */
    @Nullable
    private static DescriptionTemplate.Filled automaticWithValues(ItemStack stack) {
        String text = Language.getInstance().getOrDefault(AUTOMATIC_PREFIX + ItemCategories.of(stack), "");
        String chosen = chooseVariant(stack, text);
        return chosen.contains("{") ? DescriptionTemplate.fill(chosen, stack) : null;
    }

    private static DescriptionTemplate.Filled fill(String key, ItemStack stack) {
        return DescriptionTemplate.fill(chooseVariant(stack, Language.getInstance().getOrDefault(key)), stack);
    }

    /** "phrase 1|phrase 2|phrase 3" : une variante par objet, toujours la même. */
    private static String chooseVariant(ItemStack stack, String text) {
        if (!text.contains("|")) return text;
        String[] variants = text.split("\\|");
        int hash = BuiltInRegistries.ITEM.getKey(stack.getItem()).hashCode();
        return variants[Math.floorMod(hash, variants.length)].trim();
    }
}
