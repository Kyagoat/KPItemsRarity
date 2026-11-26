package net.Kyap.KPItemsRarity.data;

import net.Kyap.KPItemsRarity.KPItemsRarity;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Les données des datapacks, et le seul endroit où les lire :
 *
 *   data/<mod>/kp_stats/<id>.json      les stats (id = nom du fichier : "strength")
 *   data/<mod>/kp_items/<nom>.json     les stats des objets
 *   data/<mod>/kp_sets/<nom>.json      les panoplies (id = "<mod>:<nom>")
 *   data/<mod>/kp_passives/<nom>.json  les passifs (id = "<mod>:<nom>")
 *
 * Le serveur les lit au démarrage et à chaque /reload, puis envoie les fichiers tels quels aux joueurs
 * (ConfigSyncPayload) : infobulles, fiche de personnage et forge marchent donc aussi sur un serveur dédié.
 * Ce qui se calcule à partir des fichiers (ordre des stats, objet -> définition, pièces d'une panoplie) est calculé
 * une fois au chargement.
 */
@EventBusSubscriber(modid = KPItemsRarity.MOD_ID)
public final class RarityData {
    private RarityData() {}

    private static final Gson GSON = new Gson();

    /** Les quatre sortes de fichiers, et leur dossier. */
    public enum Kind {
        STATS("kp_stats"), ITEMS("kp_items"), SETS("kp_sets"), PASSIVES("kp_passives");

        public final String directory;

        Kind(String directory) {
            this.directory = directory;
        }
    }

    private static final Sided<Map<String, StatDefinition>> STATS = new Sided<>(Map.of());
    /** Les stats dans l'ordre d'affichage ("order", le même partout : infobulles, fiche, forge). */
    private static final Sided<List<StatDefinition>> STAT_ORDER = new Sided<>(List.of());
    /** Objet -> sa définition (par objet et non par son nom : demandé à chaque tick, sans rien allouer). */
    private static final Sided<Map<Item, ItemDefinition>> ITEMS = new Sided<>(Map.of());
    /** Id de panoplie -> ids des objets qui la composent. */
    private static final Sided<Map<String, List<String>>> SET_PIECES = new Sided<>(Map.of());
    private static final Sided<Map<String, SetDefinition>> SETS = new Sided<>(Map.of());
    private static final Sided<Map<String, PassiveDefinition>> PASSIVES = new Sided<>(Map.of());

    /** Les fichiers lus par le serveur, gardés tels quels pour les envoyer aux joueurs. */
    private static final Map<Kind, Map<String, String>> FILES = new EnumMap<>(Kind.class);

    // ---------------------------------------------------------------- Lecture

    @Nullable
    public static StatDefinition stat(String id) {
        return STATS.get().get(id);
    }

    /** Toutes les stats, dans l'ordre d'affichage. */
    public static List<StatDefinition> statsSorted() {
        return STAT_ORDER.get();
    }

    @Nullable
    public static ItemDefinition itemDef(ItemStack stack) {
        return stack.isEmpty() ? null : ITEMS.get().get(stack.getItem());
    }

    @Nullable
    public static SetDefinition set(String id) {
        return SETS.get().get(id);
    }

    public static List<String> setPieces(String setId) {
        return SET_PIECES.get().getOrDefault(setId, List.of());
    }

    @Nullable
    public static PassiveDefinition passive(String id) {
        return PASSIVES.get().get(id);
    }

    /** Les fichiers du serveur, pour les envoyer aux joueurs. */
    public static synchronized Map<Kind, Map<String, String>> files() {
        return Map.copyOf(FILES);
    }

    // ---------------------------------------------------------------- Chargement

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        for (Kind kind : Kind.values()) event.addListener(new Loader(kind));
    }

    /** Côté client, à la réception des fichiers du serveur. */
    public static void applyClientSync(Map<String, String> stats, Map<String, String> items, Map<String, String> sets,
                                       Map<String, String> passives) {
        load(Kind.STATS, stats, Side.CLIENT);
        load(Kind.ITEMS, items, Side.CLIENT);
        load(Kind.SETS, sets, Side.CLIENT);
        load(Kind.PASSIVES, passives, Side.CLIENT);
    }

    private enum Side {SERVER, CLIENT}

    private static <T> void store(Sided<T> target, T value, Side side) {
        if (side == Side.SERVER) target.setServer(value);
        else target.setClient(value);
    }

    /** Lit une sorte de fichiers ("clé" = mod:nom du fichier -> JSON) et range ce qui en découle. */
    private static void load(Kind kind, Map<String, String> files, Side side) {
        switch (kind) {
            case STATS -> {
                Map<String, StatDefinition> stats = parseAll(files, StatDefinition.class, (stat, key) -> stat.resolve(ResourceLocation.parse(key)), stat -> stat.id);
                List<StatDefinition> order = new ArrayList<>(stats.values());
                order.sort(Comparator.comparingInt((StatDefinition stat) -> stat.order).thenComparing(stat -> stat.id));
                store(STATS, Map.copyOf(stats), side);
                store(STAT_ORDER, List.copyOf(order), side);
            }
            case ITEMS -> {
                Map<String, ItemDefinition> items = parseAll(files, ItemDefinition.class, ItemDefinition::resolve, item -> item.id);
                store(ITEMS, byItem(items.values()), side);
                store(SET_PIECES, setPieces(items.values()), side);
            }
            case SETS -> store(SETS, Map.copyOf(parseAll(files, SetDefinition.class, SetDefinition::resolve, set -> set.id)), side);
            case PASSIVES -> store(PASSIVES, Map.copyOf(parseAll(files, PassiveDefinition.class, PassiveDefinition::setId, passive -> passive.id)), side);
        }
    }

    /**
     * Lit chaque fichier ; un fichier illisible est signalé dans le journal et ignoré.
     * @param resolve complète la définition lue (son id, ce qui se calcule une fois)
     * @param idOf    la clé sous laquelle la ranger
     */
    private static <T> Map<String, T> parseAll(Map<String, String> files, Class<T> type, BiConsumer<T, String> resolve, Function<T, String> idOf) {
        Map<String, T> out = new LinkedHashMap<>();
        files.forEach((key, json) -> {
            try {
                T definition = GSON.fromJson(json, type);
                resolve.accept(definition, key);
                out.put(idOf.apply(definition), definition);
            } catch (Exception e) {
                KPItemsRarity.LOGGER.error("KP Items Rarity : impossible de lire {} ({})", key, type.getSimpleName(), e);
            }
        });
        return out;
    }

    /**
     * Objet -> définition. Un objet décrit deux fois (fiche générique + pièce de panoplie) : la pièce de panoplie
     * gagne. Un objet d'un mod absent est ignoré.
     */
    private static Map<Item, ItemDefinition> byItem(Iterable<ItemDefinition> definitions) {
        Map<Item, ItemDefinition> byItem = new HashMap<>();
        for (ItemDefinition def : definitions) {
            for (String id : def.items) {
                ResourceLocation key = ResourceLocation.tryParse(id);
                Item item = key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
                if (item != null) byItem.merge(item, def, (old, candidate) -> candidate.hasSet() || !old.hasSet() ? candidate : old);
            }
        }
        return Map.copyOf(byItem);
    }

    private static Map<String, List<String>> setPieces(Iterable<ItemDefinition> definitions) {
        Map<String, List<String>> pieces = new HashMap<>();
        for (ItemDefinition def : definitions) {
            if (def.hasSet()) pieces.computeIfAbsent(def.set, set -> new ArrayList<>()).addAll(def.items);
        }
        Map<String, List<String>> frozen = new HashMap<>();
        pieces.forEach((set, items) -> frozen.put(set, List.copyOf(items)));
        return Map.copyOf(frozen);
    }

    private static final class Loader extends SimpleJsonResourceReloadListener {
        private final Kind kind;

        Loader(Kind kind) {
            super(GSON, kind.directory);
            this.kind = kind;
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager, ProfilerFiller profiler) {
            Map<String, String> files = new HashMap<>();
            entries.forEach((id, json) -> files.put(id.toString(), json.toString()));
            synchronized (RarityData.class) {
                FILES.put(kind, Map.copyOf(files));
            }
            load(kind, files, Side.SERVER);
            KPItemsRarity.LOGGER.info("KP Items Rarity : {} fichiers {} chargés", files.size(), kind.directory);
        }
    }
}
