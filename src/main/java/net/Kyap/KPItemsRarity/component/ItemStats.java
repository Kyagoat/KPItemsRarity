package net.Kyap.KPItemsRarity.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Les jets d'un objet, façon Dofus : id de stat -> valeur en points (ex. "strength" -> 11 = +11 % Force).
 * La fourchette théorique (min - max) n'est PAS stockée : elle vient de la définition de l'objet
 * (data/<ns>/kp_items), donc on peut rééquilibrer sans toucher aux objets existants.
 *
 * exoStat / exoValue : la ligne bonus obtenue en forgemagie (une seule par objet, "" si aucune).
 * upgrades : nombre de crans de rareté gagnés à la forge (0 à 2), qui décale les fourchettes vers le haut.
 * well : le "puits" de forgemagie, en points de poids : ce que les échecs ont fait perdre à l'objet,
 *        et qui sert à réussir les runes suivantes (voir forge/ForgeMagic).
 */
public record ItemStats(Map<String, Integer> lines, String exoStat, int exoValue, int upgrades, int well) {

    public static final Codec<ItemStats> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("lines").forGetter(ItemStats::lines),
            Codec.STRING.optionalFieldOf("exo_stat", "").forGetter(ItemStats::exoStat),
            Codec.INT.optionalFieldOf("exo_value", 0).forGetter(ItemStats::exoValue),
            Codec.INT.optionalFieldOf("upgrades", 0).forGetter(ItemStats::upgrades),
            Codec.INT.optionalFieldOf("well", 0).forGetter(ItemStats::well)
    ).apply(inst, ItemStats::new));

    public static final StreamCodec<ByteBuf, ItemStats> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.<ByteBuf, String, Integer, Map<String, Integer>>map(LinkedHashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT),
            ItemStats::lines,
            ByteBufCodecs.STRING_UTF8, ItemStats::exoStat,
            ByteBufCodecs.VAR_INT, ItemStats::exoValue,
            ByteBufCodecs.VAR_INT, ItemStats::upgrades,
            ByteBufCodecs.VAR_INT, ItemStats::well,
            ItemStats::new);

    public ItemStats {
        lines = Collections.unmodifiableMap(new LinkedHashMap<>(lines)); // garde l'ordre des lignes
        exoStat = exoStat == null ? "" : exoStat;
    }

    public ItemStats(Map<String, Integer> lines) {
        this(lines, "", 0, 0, 0);
    }

    public ItemStats(Map<String, Integer> lines, String exoStat, int exoValue) {
        this(lines, exoStat, exoValue, 0, 0);
    }

    public ItemStats(Map<String, Integer> lines, String exoStat, int exoValue, int upgrades) {
        this(lines, exoStat, exoValue, upgrades, 0);
    }

    public boolean hasExo() {
        return !exoStat.isEmpty() && exoValue != 0;
    }

    public int get(String stat) {
        return lines.getOrDefault(stat, 0);
    }

    /** Les points de chaque stat, ligne exotique comprise (elle s'ajoute à la ligne de la même stat, s'il y en a une). */
    public Map<String, Integer> pointsWithExo() {
        if (!hasExo()) return lines;
        Map<String, Integer> points = new LinkedHashMap<>(lines);
        points.merge(exoStat, exoValue, Integer::sum);
        return points;
    }

    public ItemStats withLine(String stat, int value) {
        Map<String, Integer> copy = new LinkedHashMap<>(lines);
        copy.put(stat, value);
        return new ItemStats(copy, exoStat, exoValue, upgrades, well);
    }

    public ItemStats withExo(String stat, int value) {
        return new ItemStats(lines, stat, value, upgrades, well);
    }
}
