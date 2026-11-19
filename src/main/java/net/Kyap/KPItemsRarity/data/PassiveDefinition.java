package net.Kyap.KPItemsRarity.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * Un passif, à la manière des objets légendaires de Dofus : data/<ns>/kp_passives/<nom>.json
 * {
 *   "effect": "life_steal",                   // type d'effet (voir passive/PassiveEffects)
 *   "params": { "ratio": 0.2, "below_health": 0.3, "low_ratio": 1.5 },
 *   "cooldown": 0                             // secondes ; 0 = pas de recharge
 * }
 * Porté par un objet (kp_items : "passives": ["<ns>:<nom>"]) ou par une panoplie
 * (kp_sets : "passives": { "5": ["<ns>:<nom>"] }).
 * Nom : passive.<ns>.<nom> ; description : passive.<ns>.<nom>.desc
 */
public class PassiveDefinition {
    public transient String id;

    public String effect = "";
    public JsonObject params = new JsonObject();
    public double cooldown = 0;

    public double num(String key, double fallback) {
        JsonElement e = params != null ? params.get(key) : null;
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : fallback;
    }

    public String str(String key, String fallback) {
        JsonElement e = params != null ? params.get(key) : null;
        return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
    }

    public boolean flag(String key) {
        JsonElement e = params != null ? params.get(key) : null;
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
    }

    private String key() {
        ResourceLocation loc = ResourceLocation.tryParse(id);
        return loc != null ? "passive." + loc.getNamespace() + "." + loc.getPath() : "passive." + id;
    }

    public MutableComponent name() {
        return Component.translatable(key());
    }

    public MutableComponent description() {
        return Component.translatable(key() + ".desc");
    }
}
