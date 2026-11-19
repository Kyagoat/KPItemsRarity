package net.Kyap.KPItemsRarity.network;

import net.Kyap.KPItemsRarity.KPItemsRarity;
import net.Kyap.KPItemsRarity.data.RarityData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Envoie aux clients les JSON des stats, objets et panoplies (à la connexion et après /reload).
 * Les paquets réseau du mod (synchro + partage d'objet) sont enregistrés ici.
 */
public record ConfigSyncPayload(Map<String, String> stats, Map<String, String> items, Map<String, String> sets,
                                Map<String, String> passives)
        implements CustomPacketPayload {

    public static final Type<ConfigSyncPayload> TYPE = new Type<>(KPItemsRarity.id("config_sync"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Map<String, String>> STRING_MAP =
            ByteBufCodecs.<RegistryFriendlyByteBuf, String, String, Map<String, String>>map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8);

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigSyncPayload> STREAM_CODEC = StreamCodec.composite(
            STRING_MAP, ConfigSyncPayload::stats,
            STRING_MAP, ConfigSyncPayload::items,
            STRING_MAP, ConfigSyncPayload::sets,
            STRING_MAP, ConfigSyncPayload::passives,
            ConfigSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static ConfigSyncPayload fromServerData() {
        Map<RarityData.Kind, Map<String, String>> raw = RarityData.rawData();
        return new ConfigSyncPayload(
                raw.getOrDefault(RarityData.Kind.STATS, Map.of()),
                raw.getOrDefault(RarityData.Kind.ITEMS, Map.of()),
                raw.getOrDefault(RarityData.Kind.SETS, Map.of()),
                raw.getOrDefault(RarityData.Kind.PASSIVES, Map.of()));
    }

    private static void handle(ConfigSyncPayload payload, IPayloadContext context) {
        RarityData.applyClientSync(payload.stats(), payload.items(), payload.sets(), payload.passives());
    }

    @EventBusSubscriber(modid = KPItemsRarity.MOD_ID)
    public static final class Registration {
        @SubscribeEvent
        public static void register(RegisterPayloadHandlersEvent event) {
            var registrar = event.registrar("4");
            registrar.playToClient(TYPE, STREAM_CODEC, ConfigSyncPayload::handle);
            registrar.playToServer(ShareItemPayload.TYPE, ShareItemPayload.STREAM_CODEC, ShareItemPayload::handle);
            registrar.playToClient(PassiveCooldownPayload.TYPE, PassiveCooldownPayload.STREAM_CODEC, PassiveCooldownPayload::handle);
        }
    }

    @EventBusSubscriber(modid = KPItemsRarity.MOD_ID)
    public static final class Sync {
        @SubscribeEvent
        public static void onDatapackSync(OnDatapackSyncEvent event) {
            ConfigSyncPayload payload = fromServerData();
            event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, payload));
        }
    }
}
