package net.Kyap.KPItemsRarity.rarity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rareté AFFICHÉE des objets qui n'ont pas de jets (matériaux, objets des autres mods...) :
 * couleur du nom et du cadre de l'infobulle. Rempli côté client par les fichiers
 *   assets/<mod>/kp_rarity/*.json  { "rarity": "epic", "items": ["minecraft:elytra", "#tide:legendary_fish"] }
 * (un resource pack peut donc tout changer). Un objet listé directement passe avant un tag.
 */
public final class RarityOverrides {
    private RarityOverrides() {}

    private static volatile Map<Item, ModRarity> byItem = Map.of();
    private static volatile List<Map.Entry<TagKey<Item>, ModRarity>> byTag = List.of();

    @Nullable
    public static ModRarity get(ItemStack stack) {
        Map<Item, ModRarity> items = byItem;
        if (items.isEmpty() && byTag.isEmpty()) return null;
        ModRarity direct = items.get(stack.getItem());
        if (direct != null) return direct;
        for (var entry : byTag) if (stack.is(entry.getKey())) return entry.getValue();
        return null;
    }

    /** Remplace toutes les surcharges (appelé au rechargement des ressources). */
    public static void set(Map<String, String> entries) {
        Map<Item, ModRarity> items = new HashMap<>();
        List<Map.Entry<TagKey<Item>, ModRarity>> tags = new ArrayList<>();
        entries.forEach((id, rarityId) -> {
            ModRarity rarity = ModRarity.fromId(rarityId);
            if (rarity == null) return;
            if (id.startsWith("#")) {
                ResourceLocation loc = ResourceLocation.tryParse(id.substring(1));
                if (loc != null) tags.add(Map.entry(TagKey.create(Registries.ITEM, loc), rarity));
            } else {
                ResourceLocation loc = ResourceLocation.tryParse(id);
                // Objet d'un mod absent : simplement ignoré
                if (loc != null && BuiltInRegistries.ITEM.containsKey(loc)) items.put(BuiltInRegistries.ITEM.get(loc), rarity);
            }
        });
        byItem = Map.copyOf(items);
        byTag = List.copyOf(tags);
    }
}
