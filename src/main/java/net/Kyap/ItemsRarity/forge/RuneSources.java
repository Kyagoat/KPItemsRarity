package net.Kyap.ItemsRarity.forge;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.Kyap.ItemsRarity.ItemsRarity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Brisage de RESSOURCES : quelles runes rend un matériau cassé à la table de forgemagie.
 *   data/<mod>/kp_runes/*.json
 *   { "items": ["kpsets:dragon_scale", "#c:gems/diamond"],
 *     "runes": [ { "stat": "strength", "power": 3, "chance": 0.5, "count": 1 } ] }
 * Chaque objet de la pile est cassé séparément : chaque rune a "chance" de tomber, "count" fois.
 * (Les objets à stats, eux, se brisent selon leurs jets : voir ForgeMagic.breakItem.)
 */
@EventBusSubscriber(modid = ItemsRarity.MOD_ID)
public final class RuneSources extends SimpleJsonResourceReloadListener {

    public record Drop(String stat, int power, float chance, int count) {}

    private static volatile Map<Item, List<Drop>> byItem = Map.of();
    private static volatile List<Map.Entry<TagKey<Item>, List<Drop>>> byTag = List.of();

    private RuneSources() {
        super(new Gson(), "kp_runes");
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new RuneSources());
    }

    /** Runes que rend cet objet une fois brisé, ou une liste vide si ce n'est pas une ressource à runes. */
    public static List<Drop> dropsFor(ItemStack stack) {
        List<Drop> direct = byItem.get(stack.getItem());
        if (direct != null) return direct;
        for (var entry : byTag) if (stack.is(entry.getKey())) return entry.getValue();
        return List.of();
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<Item, List<Drop>> items = new HashMap<>();
        List<Map.Entry<TagKey<Item>, List<Drop>>> tags = new ArrayList<>();
        files.forEach((file, json) -> {
            try {
                JsonObject obj = json.getAsJsonObject();
                List<Drop> drops = new ArrayList<>();
                for (JsonElement e : GsonHelper.getAsJsonArray(obj, "runes")) {
                    JsonObject r = e.getAsJsonObject();
                    drops.add(new Drop(GsonHelper.getAsString(r, "stat"), GsonHelper.getAsInt(r, "power", 1),
                            GsonHelper.getAsFloat(r, "chance", 1f), GsonHelper.getAsInt(r, "count", 1)));
                }
                for (JsonElement e : GsonHelper.getAsJsonArray(obj, "items")) {
                    String id = e.getAsString();
                    if (id.startsWith("#")) {
                        ResourceLocation tag = ResourceLocation.tryParse(id.substring(1));
                        if (tag != null) tags.add(Map.entry(TagKey.create(Registries.ITEM, tag), List.copyOf(drops)));
                    } else {
                        ResourceLocation loc = ResourceLocation.tryParse(id);
                        if (loc != null && BuiltInRegistries.ITEM.containsKey(loc)) items.put(BuiltInRegistries.ITEM.get(loc), List.copyOf(drops));
                    }
                }
            } catch (RuntimeException ex) {
                ItemsRarity.LOGGER.warn("kp_runes {} illisible : {}", file, ex.getMessage());
            }
        });
        byItem = Map.copyOf(items);
        byTag = List.copyOf(tags);
        ItemsRarity.LOGGER.info("Forgemagie : {} ressources à runes chargées", items.size() + tags.size());
    }
}
