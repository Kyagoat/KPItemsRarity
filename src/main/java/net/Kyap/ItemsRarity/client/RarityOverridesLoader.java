package net.Kyap.ItemsRarity.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.Kyap.ItemsRarity.ItemsRarity;
import net.Kyap.ItemsRarity.util.RarityOverrides;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;

/** Charge assets/<mod>/kp_rarity/*.json (voir RarityOverrides). */
@EventBusSubscriber(modid = ItemsRarity.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class RarityOverridesLoader extends SimplePreparableReloadListener<Map<String, String>> {

    private static final FileToIdConverter FILES = FileToIdConverter.json("kp_rarity");

    @SubscribeEvent
    public static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new RarityOverridesLoader());
    }

    @Override
    protected Map<String, String> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<String, String> out = new LinkedHashMap<>();
        FILES.listMatchingResourceStacks(manager).forEach((file, stack) -> stack.forEach(resource -> {
            try (Reader reader = resource.openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                String rarity = json.get("rarity").getAsString();
                for (JsonElement e : json.getAsJsonArray("items")) out.put(e.getAsString(), rarity);
            } catch (Exception e) {
                ItemsRarity.LOGGER.warn("Fichier de rareté illisible : {} ({})", file, e.getMessage());
            }
        }));
        return out;
    }

    @Override
    protected void apply(Map<String, String> entries, ResourceManager manager, ProfilerFiller profiler) {
        RarityOverrides.set(entries);
        ItemsRarity.LOGGER.info("Items Rarity : {} raretés d'objets chargées", entries.size());
    }
}
