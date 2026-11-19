package net.Kyap.KPItemsRarity.client.tooltip;

import net.Kyap.KPItemsRarity.data.ItemDefinition;
import net.Kyap.KPItemsRarity.data.SetDefinition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Petits textes d'ambiance (lore) des infobulles. TOUT est dans les fichiers de langue, pour les modifier
 * sans toucher au code : assets/kpitemsrarity/lang/fr_fr.json (et en_us.json).
 *
 *   Objet     : "lore.<mod>.<objet>"        ex. "lore.minecraft.iron_sword"
 *   Panoplie  : "lore.set.<mod>.<panoplie>" ex. "lore.set.kpitemsrarity.iron_guard"
 *
 * On peut aussi forcer un texte (ou une autre clé) avec le champ "lore" du JSON de l'objet (kp_items)
 * ou de la panoplie (kp_sets). Pas de clé = pas de lore, rien ne s'affiche.
 */
public final class LoreText {
    private LoreText() {}

    public static String itemKey(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return "lore." + id.getNamespace() + "." + id.getPath();
    }

    public static String setKey(String setId) {
        ResourceLocation id = ResourceLocation.tryParse(setId);
        return id == null ? "lore.set." + setId : "lore.set." + id.getNamespace() + "." + id.getPath();
    }

    @Nullable
    public static Component item(ItemStack stack, @Nullable ItemDefinition def) {
        return resolve(def != null ? def.lore : null, itemKey(stack));
    }

    @Nullable
    public static Component set(SetDefinition set) {
        return resolve(set.lore, setKey(set.id));
    }

    @Nullable
    private static Component resolve(@Nullable String override, String defaultKey) {
        Language lang = Language.getInstance();
        Component text = null;
        if (override != null && !override.isBlank()) {
            text = lang.has(override) ? Component.translatable(override) : Component.literal(override);
        } else if (lang.has(defaultKey)) {
            text = Component.translatable(defaultKey);
        }
        return text == null ? null : text.copy().withStyle(s -> s.withItalic(true).withColor(RpgFrame.TEXT_DIM));
    }
}
