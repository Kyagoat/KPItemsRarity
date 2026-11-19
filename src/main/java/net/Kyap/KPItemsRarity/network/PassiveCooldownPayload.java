package net.Kyap.KPItemsRarity.network;

import net.Kyap.KPItemsRarity.KPItemsRarity;
import net.Kyap.KPItemsRarity.passive.PassiveCooldowns;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Serveur -> joueur : un de ses passifs vient de se déclencher et recharge pendant "ticks" ticks (tooltip). */
public record PassiveCooldownPayload(String passive, int ticks) implements CustomPacketPayload {
    public static final Type<PassiveCooldownPayload> TYPE = new Type<>(KPItemsRarity.id("passive_cooldown"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PassiveCooldownPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PassiveCooldownPayload::passive,
            ByteBufCodecs.VAR_INT, PassiveCooldownPayload::ticks,
            PassiveCooldownPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(PassiveCooldownPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> PassiveCooldowns.startClient(p.passive(), p.ticks()));
    }
}
