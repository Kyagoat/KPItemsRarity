package net.Kyap.KPItemsRarity.data;

import net.Kyap.KPItemsRarity.KPItemsRarity;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Accès unique aux données des datapacks :
 *  - data/<ns>/kp_stats/*.json -> caractéristiques (id = nom du fichier, ex. "strength")
 *  - data/<ns>/kp_items/*.json -> lignes de stats des objets
 *  - data/<ns>/kp_sets/*.json  -> panoplies (id = "<ns>:<nom>")
 * Les JSON bruts sont envoyés tels quels aux clients (ConfigSyncPayload) : tooltips, panneau
 * de stats et panoplies marchent donc aussi sur un serveur dédié.
 */
@EventBusSubscriber(modid = KPItemsRarity.MOD_ID)
public final class RarityData {
    private RarityData() {}

    public static final Gson GSON = new GsonBuilder().create();

    public enum Kind {
        STATS("kp_stats"), ITEMS("kp_items"), SETS("kp_sets"), PASSIVES("kp_passives");

        public final String directory;

        Kind(String directory) {
            this.directory = directory;
        }
    }

    private static final SidedStore<StatDefinition> STATS = new SidedStore<>();
    private static final SidedStore<ItemDefinition> ITEMS = new SidedStore<>();
    private static final SidedStore<SetDefinition> SETS = new SidedStore<>();
    private static final SidedStore<PassiveDefinition> PASSIVES = new SidedStore<>();
    /** id d'objet ("minecraft:iron_sword") -> définition. */
    private static final SidedStore<ItemDefinition> ITEM_INDEX = new SidedStore<>();
    /** id de panoplie -> ids des objets qui la composent. */
    private static final SidedStore<List<String>> SET_PIECES = new SidedStore<>();

    private static final Map<Kind, Map<String, String>> RAW = new HashMap<>();

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        for (Kind kind : Kind.values()) event.addListener(new Loader(kind));
    }

    // ---------------------------------------------------------------- Accès

    @Nullable
    public static StatDefinition stat(String id) {
        return STATS.get().get(id);
    }

    /** Toutes les stats, triées par "order". */
    public static List<StatDefinition> statsSorted() {
        List<StatDefinition> list = new ArrayList<>(STATS.get().values());
        list.sort(Comparator.comparingInt((StatDefinition s) -> s.order).thenComparing(s -> s.id));
        return list;
    }

    @Nullable
    public static ItemDefinition itemDef(ItemStack stack) {
        if (stack.isEmpty()) return null;
        return ITEM_INDEX.get().get(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    @Nullable
    public static SetDefinition set(String id) {
        return SETS.get().get(id);
    }

    @Nullable
    public static PassiveDefinition passive(String id) {
        return PASSIVES.get().get(id);
    }

    public static List<String> setPieces(String setId) {
        return SET_PIECES.get().getOrDefault(setId, List.of());
    }

    public static Map<Kind, Map<String, String>> rawData() {
        Map<Kind, Map<String, String>> copy = new HashMap<>();
        RAW.forEach((k, v) -> copy.put(k, Map.copyOf(v)));
        return copy;
    }

    // ---------------------------------------------------------------- Chargement

    /** Côté client, à la réception du paquet de synchronisation. */
    public static void applyClientSync(Map<String, String> stats, Map<String, String> items, Map<String, String> sets,
                                       Map<String, String> passives) {
        PASSIVES.setClient(parsePassives(passives));
        STATS.setClient(parseStats(stats));
        Map<String, ItemDefinition> parsedItems = parseItems(items);
        ITEMS.setClient(parsedItems);
        ITEM_INDEX.setClient(buildIndex(parsedItems));
        SET_PIECES.setClient(buildPieces(parsedItems));
        SETS.setClient(parseSets(sets));
    }

    private static void applyServer(Kind kind, Map<String, String> raw) {
        RAW.put(kind, Map.copyOf(raw));
        switch (kind) {
            case STATS -> STATS.setServer(parseStats(raw));
            case ITEMS -> {
                Map<String, ItemDefinition> parsed = parseItems(raw);
                ITEMS.setServer(parsed);
                ITEM_INDEX.setServer(buildIndex(parsed));
                SET_PIECES.setServer(buildPieces(parsed));
            }
            case SETS -> SETS.setServer(parseSets(raw));
            case PASSIVES -> PASSIVES.setServer(parsePassives(raw));
        }
        KPItemsRarity.LOGGER.info("KP Items Rarity : {} fichiers {} chargés", raw.size(), kind.directory);
    }

    private static Map<String, StatDefinition> parseStats(Map<String, String> raw) {
        Map<String, StatDefinition> out = new LinkedHashMap<>();
        raw.forEach((key, json) -> {
            StatDefinition def = parse(key, json, StatDefinition.class);
            if (def == null) return;
            ResourceLocation loc = ResourceLocation.tryParse(key);
            def.namespace = loc != null ? loc.getNamespace() : KPItemsRarity.MOD_ID;
            def.id = loc != null ? loc.getPath() : key;
            out.put(def.id, def);
        });
        return out;
    }

    private static Map<String, ItemDefinition> parseItems(Map<String, String> raw) {
        Map<String, ItemDefinition> out = new LinkedHashMap<>();
        raw.forEach((key, json) -> {
            ItemDefinition def = parse(key, json, ItemDefinition.class);
            if (def == null) return;
            def.id = key;
            out.put(key, def);
        });
        return out;
    }

    private static Map<String, SetDefinition> parseSets(Map<String, String> raw) {
        Map<String, SetDefinition> out = new LinkedHashMap<>();
        raw.forEach((key, json) -> {
            SetDefinition def = parse(key, json, SetDefinition.class);
            if (def == null) return;
            def.id = key;
            out.put(key, def);
        });
        return out;
    }

    private static Map<String, PassiveDefinition> parsePassives(Map<String, String> raw) {
        Map<String, PassiveDefinition> out = new LinkedHashMap<>();
        raw.forEach((key, json) -> {
            PassiveDefinition def = parse(key, json, PassiveDefinition.class);
            if (def == null) return;
            def.id = key;
            out.put(key, def);
        });
        return out;
    }

    private static Map<String, ItemDefinition> buildIndex(Map<String, ItemDefinition> defs) {
        Map<String, ItemDefinition> index = new HashMap<>();
        defs.values().forEach(def -> {
            // Un objet décrit deux fois (fiche générique + pièce de panoplie d'un autre mod) : la pièce de panoplie gagne.
            if (def.items != null) def.items.forEach(item -> index.merge(item, def, (old, neu) -> neu.hasSet() || !old.hasSet() ? neu : old));
        });
        return index;
    }

    private static Map<String, List<String>> buildPieces(Map<String, ItemDefinition> defs) {
        Map<String, List<String>> pieces = new HashMap<>();
        defs.values().forEach(def -> {
            if (def.hasSet() && def.items != null) {
                pieces.computeIfAbsent(def.set, k -> new ArrayList<>()).addAll(def.items);
            }
        });
        Map<String, List<String>> frozen = new HashMap<>();
        pieces.forEach((k, v) -> frozen.put(k, List.copyOf(v)));
        return frozen;
    }

    @Nullable
    private static <T> T parse(String key, String json, Class<T> type) {
        try {
            return GSON.fromJson(json, type);
        } catch (Exception e) {
            KPItemsRarity.LOGGER.error("KP Items Rarity : impossible de lire {} ({})", key, type.getSimpleName(), e);
            return null;
        }
    }

    private static final class Loader extends SimpleJsonResourceReloadListener {
        private final Kind kind;

        Loader(Kind kind) {
            super(GSON, kind.directory);
            this.kind = kind;
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager, ProfilerFiller profiler) {
            Map<String, String> raw = new HashMap<>();
            entries.forEach((loc, json) -> raw.put(loc.toString(), json.toString()));
            applyServer(kind, raw);
        }
    }
}
