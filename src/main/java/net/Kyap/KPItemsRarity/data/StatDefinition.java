package net.Kyap.KPItemsRarity.data;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

/**
 * Une caractéristique : data/<ns>/kp_stats/<id>.json
 * {
 *   "attribute": "minecraft:generic.attack_damage",   // attribut modifié
 *   "operation": "add_multiplied_base",               // add_value | add_multiplied_base | add_multiplied_total
 *   "scale": 0.01,                                    // valeur d'attribut par point (11 points -> +0.11)
 *   "format": "percent",                              // int | percent | tenths (affichage)
 *   "color": "#C8873A", "category": "combat", "order": 20,
 *   "rune_power": 1, "exo": false                     // utilisés par la forgemagie
 * }
 *   "weight": 1.0, "base": false, "offset": 0.0             // équilibrage / ligne de base (voir plus bas)
 * Nom affiché : clé de traduction stat.<ns>.<id>. Si l'attribut n'existe pas (mod absent), la stat est inactive.
 */
public class StatDefinition {
    public transient String id;
    public transient String namespace;
    /** Calculés une fois au chargement (voir resolve) : ils servent à chaque image et à chaque tick. */
    @Nullable private transient Holder<Attribute> resolvedAttribute;
    private transient AttributeModifier.Operation resolvedOperation;
    private transient int resolvedRgb;

    public String attribute;
    public String operation = "add_value";
    public double scale = 1.0;
    public String format = "int";
    public String color = "#FFFFFF";
    public String category = "utility";
    public int order = 100;
    public int rune_power = 1;
    public boolean exo = false;
    /** Ancienne stat devenue passif : plus de rune ni d'ajout en forgemagie, reste affichée sur les vieux objets. */
    public boolean legacy = false;
    /** Coût d'un point dans le budget d'équilibrage (voir tools/check_balance.py). */
    public double weight = 1.0;
    /**
     * Ligne "de base" (ex. Dégâts d'une arme) : remplace la valeur vanilla de l'objet au lieu de s'y ajouter,
     * s'affiche sans signe, hors budget, et ne monte pas avec la rareté.
     * La valeur posée sur l'attribut = points * scale + offset (Dégâts 6 -> modificateur 5, la main fait déjà 1).
     */
    public boolean base = false;
    public double offset = 0.0;
    /**
     * Fiche de personnage : "total" (valeur réelle, base comprise : 5 % de critique, 40 d'endurance...),
     * "bonus" (seulement ce qu'apportent équipement et effets) ou "" = automatique
     * (total pour une valeur ajoutée, bonus pour un pourcentage du base ou un attribut multiplicateur à 1).
     */
    public String display = "";

    /** Complète la stat après lecture de son fichier : son id, son attribut, son opération, sa couleur. */
    void resolve(ResourceLocation file) {
        namespace = file.getNamespace();
        id = file.getPath();
        ResourceLocation attributeId = attribute == null ? null : ResourceLocation.tryParse(attribute);
        resolvedAttribute = attributeId == null ? null : BuiltInRegistries.ATTRIBUTE.getHolder(attributeId).orElse(null);
        resolvedOperation = AttributeModifier.Operation.ADD_VALUE;
        for (AttributeModifier.Operation op : AttributeModifier.Operation.values()) {
            if (op.getSerializedName().equalsIgnoreCase(operation)) resolvedOperation = op;
        }
        resolvedRgb = parseRgb(color);
    }

    private static int parseRgb(String hex) {
        try {
            return Integer.parseInt(hex.replace("#", ""), 16) & 0xFFFFFF;
        } catch (RuntimeException e) {
            return 0xFFFFFF;
        }
    }

    /** L'attribut que la stat modifie ; vide si son mod est absent. */
    public Optional<Holder<Attribute>> attributeHolder() {
        return Optional.ofNullable(resolvedAttribute);
    }

    /** La stat agit (son attribut existe). Une stat d'un mod absent reste affichée, barrée. */
    public boolean isActive() {
        return resolvedAttribute != null;
    }

    public AttributeModifier.Operation parsedOperation() {
        return resolvedOperation;
    }

    public int rgb() {
        return resolvedRgb;
    }

    /** Fiche de personnage : montrer la valeur totale (base comprise) plutôt que le seul bonus ? */
    public boolean showsTotal() {
        if ("total".equalsIgnoreCase(display)) return true;
        if ("bonus".equalsIgnoreCase(display)) return false;
        if (resolvedOperation != AttributeModifier.Operation.ADD_VALUE || resolvedAttribute == null) return false;
        return Math.abs(resolvedAttribute.value().getDefaultValue() - 1.0) > 1e-6;
    }

    public MutableComponent name() {
        return Component.translatable("stat." + namespace + "." + id);
    }

    /** "+11 %", "-3", "+0.5" selon le format. */
    public String formatValue(int points, boolean withSign) {
        String sign = withSign && points >= 0 ? "+" : "";
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "percent" -> sign + points + " %";
            case "tenths" -> sign + String.format(Locale.ROOT, "%.1f", points / 10.0);
            default -> sign + points;
        };
    }

    /** "8 à 15" sans signe ni unité, pour la fourchette grisée. */
    public String formatRange(int min, int max) {
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "tenths" -> String.format(Locale.ROOT, "%.1f - %.1f", min / 10.0, max / 10.0);
            default -> min + " - " + max;
        };
    }
}
