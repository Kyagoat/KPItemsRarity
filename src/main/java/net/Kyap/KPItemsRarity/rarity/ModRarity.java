package net.Kyap.KPItemsRarity.rarity;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Rarity;
import org.jetbrains.annotations.Nullable;

/**
 * Les six raretés du mod, de Commun à Mythique, dans l'ordre (comparer avec {@code ordinal()}).
 * Stockée sur l'objet (composant kpitemsrarity:rarity), et convertie en rareté vanilla pour la couleur du nom :
 * Légendaire et Mythique sont ajoutées à l'énumération vanilla (voir RarityEnumParams).
 */
public enum ModRarity implements StringRepresentable {
    COMMON("common", 0x999999, 1),
    UNCOMMON("uncommon", 0x33CC33, 1),
    RARE("rare", 0x3366FF, 2),
    EPIC("epic", 0xCC33FF, 5),
    LEGENDARY("legendary", 0xFF9900, 7),
    MYTHIC("mythic", 0xFF3333, 10);

    public static final Codec<ModRarity> CODEC = StringRepresentable.fromEnum(ModRarity::values);
    public static final StreamCodec<ByteBuf, ModRarity> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], ModRarity::ordinal);

    private final String id;
    private final int particleColor;
    private final int particleMultiplier;
    /** Résolue à la première demande : les raretés ajoutées à l'énumération vanilla n'existent qu'une fois le jeu chargé. */
    @Nullable private Rarity vanilla;

    ModRarity(String id, int particleColor, int particleMultiplier) {
        this.id = id;
        this.particleColor = particleColor;
        this.particleMultiplier = particleMultiplier;
    }

    public String id() {
        return id;
    }

    /** "Légendaire" (clé rarity.kpitemsrarity.<id>). */
    public Component displayName() {
        return Component.translatable("rarity.kpitemsrarity." + id);
    }

    /** Couleur des particules (forge, enclume), en 0xRRGGBB. */
    public int particleColor() {
        return particleColor;
    }

    /** Combien de particules de plus qu'un objet commun. */
    public int particleMultiplier() {
        return particleMultiplier;
    }

    public Rarity vanilla() {
        if (vanilla == null) {
            vanilla = switch (this) {
                case COMMON -> Rarity.COMMON;
                case UNCOMMON -> Rarity.UNCOMMON;
                case RARE -> Rarity.RARE;
                case EPIC -> Rarity.EPIC;
                case LEGENDARY -> Rarity.valueOf(RarityEnumParams.LEGENDARY);
                case MYTHIC -> Rarity.valueOf(RarityEnumParams.MYTHIC);
            };
        }
        return vanilla;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    /** La rareté écrite dans un fichier ("epic", "Legendary"...), ou null si elle n'existe pas. */
    @Nullable
    public static ModRarity fromId(@Nullable String id) {
        for (ModRarity rarity : values()) {
            if (rarity.id.equalsIgnoreCase(id)) return rarity;
        }
        return null;
    }

    /** Une rareté vanilla, ou ajoutée par un autre mod (reconnue à son nom : "..._LEGENDARY", "..._MYTHIC"). */
    public static ModRarity fromVanilla(Rarity rarity) {
        String name = rarity.name();
        if (name.contains("MYTHIC")) return MYTHIC;
        if (name.contains("LEGENDARY")) return LEGENDARY;
        return switch (rarity) {
            case COMMON -> COMMON;
            case UNCOMMON -> UNCOMMON;
            case RARE -> RARE;
            case EPIC -> EPIC;
            default -> RARE; // rareté inconnue d'un autre mod : au moins visible
        };
    }

    /** La plus haute des deux. */
    public static ModRarity max(ModRarity a, ModRarity b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
