package net.Kyap.ItemsRarity.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Une rune de forgemagie : la stat qu'elle monte et sa taille (1 = rune, 3 = rune Pa, 10 = rune Ra).
 * Une seule sorte d'objet "rune" pour toutes les stats : la stat est dans ce composant (data-driven).
 */
public record RuneData(String stat, int power) {
    public static final int BASE = 1, PA = 3, RA = 10;

    public static final Codec<RuneData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("stat").forGetter(RuneData::stat),
            Codec.INT.optionalFieldOf("power", BASE).forGetter(RuneData::power)
    ).apply(inst, RuneData::new));

    public static final StreamCodec<ByteBuf, RuneData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RuneData::stat,
            ByteBufCodecs.VAR_INT, RuneData::power,
            RuneData::new);

    /** Clé de taille pour le nom : "", "pa" ou "ra". */
    public String sizeKey() {
        return power >= RA ? "ra" : power >= PA ? "pa" : "base";
    }
}
