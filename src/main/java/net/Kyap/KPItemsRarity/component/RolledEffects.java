package net.Kyap.KPItemsRarity.component;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Effets tirés sur un objet : id de l'effet -> valeur (ex. "damage" -> 0.12 = +12 %).
 * Remplace les anciens tags NBT "CustomEffects" + "XxxEffect".
 * Les modificateurs d'attributs NE sont PAS stockés sur l'objet : ils sont recalculés à partir
 * de ce composant (voir AttributeEvents), donc changer un JSON d'effet ne casse pas les vieux objets.
 */
public record RolledEffects(Map<String, Float> values) {
    public static final RolledEffects EMPTY = new RolledEffects(Map.of());

    public static final Codec<RolledEffects> CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT).xmap(RolledEffects::new, RolledEffects::values);

    public static final StreamCodec<ByteBuf, RolledEffects> STREAM_CODEC =
            ByteBufCodecs.<ByteBuf, String, Float, Map<String, Float>>map(LinkedHashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.FLOAT)
                    .map(RolledEffects::new, RolledEffects::values);

    public RolledEffects {
        values = Map.copyOf(values);
    }

    public float get(String effectId) {
        return values.getOrDefault(effectId, 0f);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }
}
