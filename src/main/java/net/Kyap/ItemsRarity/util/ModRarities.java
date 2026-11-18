package net.Kyap.ItemsRarity.util;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Rarity;
import org.jetbrains.annotations.Nullable;

/**
 * Les raretés du mod, stockées sur l'objet via le data component {@code itemsrarity:rarity}.
 * Chaque rareté sait se convertir en {@link Rarity} vanilla (couleur du nom, tooltips Obscure).
 */
public final class ModRarities {
    private ModRarities() {}

    public enum ModRarity implements StringRepresentable {
        COMMON("common", 0, 0.6f, 0.6f, 0.6f, 1),
        UNCOMMON("uncommon", 1, 0.2f, 0.8f, 0.2f, 1),
        RARE("rare", 2, 0.2f, 0.4f, 1.0f, 2),
        EPIC("epic", 3, 0.8f, 0.2f, 1.0f, 5),
        LEGENDARY("legendary", 4, 1.0f, 0.6f, 0.0f, 7),
        MYTHIC("mythic", 5, 1.0f, 0.2f, 0.2f, 10);

        public static final Codec<ModRarity> CODEC = StringRepresentable.fromEnum(ModRarity::values);
        public static final StreamCodec<ByteBuf, ModRarity> STREAM_CODEC =
                ByteBufCodecs.idMapper(i -> values()[i], ModRarity::ordinal);

        private final String id;
        private final int level;
        private final float red, green, blue;
        private final int particleMultiplier;
        private Rarity vanilla; // résolu à la demande (l'enum vanilla étendu doit être chargé)

        ModRarity(String id, int level, float red, float green, float blue, int particleMultiplier) {
            this.id = id;
            this.level = level;
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.particleMultiplier = particleMultiplier;
        }

        public String getId() { return id; }
        public int getLevel() { return level; }
        public float red() { return red; }
        public float green() { return green; }
        public float blue() { return blue; }
        public int getParticleMultiplier() { return particleMultiplier; }

        public Rarity getRarity() {
            if (vanilla == null) {
                vanilla = switch (this) {
                    case COMMON -> Rarity.COMMON;
                    case UNCOMMON -> Rarity.UNCOMMON;
                    case RARE -> Rarity.RARE;
                    case EPIC -> Rarity.EPIC;
                    case LEGENDARY -> Rarity.valueOf("ITEMSRARITY_LEGENDARY");
                    case MYTHIC -> Rarity.valueOf("ITEMSRARITY_MYTHIC");
                };
            }
            return vanilla;
        }

        public boolean isAtLeast(ModRarity other) {
            return this.level >= other.level;
        }

        @Override
        public String getSerializedName() {
            return id;
        }

        @Nullable
        public static ModRarity fromId(@Nullable String id) {
            if (id == null) return null;
            for (ModRarity r : values()) {
                if (r.id.equalsIgnoreCase(id)) return r;
            }
            return null;
        }
    }
}
