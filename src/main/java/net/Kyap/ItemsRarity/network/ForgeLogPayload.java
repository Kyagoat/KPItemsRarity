package net.Kyap.ItemsRarity.network;

import net.Kyap.ItemsRarity.ItemsRarity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Serveur -> client : le résultat d'une action à la table de forgemagie (forger, briser), affiché dans
 * le journal de l'écran. Les dernières lignes sont gardées dans LOG (lu par l'écran).
 */
public record ForgeLogPayload(List<Component> lines) implements CustomPacketPayload {

    public static final Type<ForgeLogPayload> TYPE = new Type<>(ItemsRarity.id("forge_log"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ForgeLogPayload> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.TRUSTED_STREAM_CODEC.apply(ByteBufCodecs.list(16)), ForgeLogPayload::lines,
            ForgeLogPayload::new);

    /** Journal côté client, le plus récent en haut. */
    public static final List<Component> LOG = new ArrayList<>();
    private static final int KEEP = 14;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(ForgeLogPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            List<Component> fresh = new ArrayList<>(payload.lines());
            if (!LOG.isEmpty()) fresh.add(Component.empty());
            LOG.addAll(0, fresh);
            while (LOG.size() > KEEP) LOG.remove(LOG.size() - 1);
        });
    }
}
