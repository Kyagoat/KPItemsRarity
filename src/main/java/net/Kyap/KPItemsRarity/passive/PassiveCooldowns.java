package net.Kyap.KPItemsRarity.passive;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.network.PassiveCooldownPayload;
import net.minecraft.Util;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Recharges des passifs ("cooldown" en secondes) : le compte côté serveur, et la copie du client pour le voyant du tooltip. */
public final class PassiveCooldowns {
    private PassiveCooldowns() {}

    /** Serveur : fin de recharge (temps du monde) par joueur et par passif. */
    private static final Map<UUID, Map<String, Long>> SERVER = new ConcurrentHashMap<>();
    /** Client : fin de recharge en millisecondes réelles, par passif du joueur local. */
    private static final Map<String, Long> CLIENT = new ConcurrentHashMap<>();

    /** Le passif est-il prêt ? S'il l'est et a une recharge, on la lance (et on prévient le joueur). */
    public static boolean ready(LivingEntity entity, PassiveDefinition p) {
        if (p.cooldown <= 0) return true;
        long now = entity.level().getGameTime();
        Map<String, Long> map = SERVER.computeIfAbsent(entity.getUUID(), k -> new HashMap<>());
        Long until = map.get(p.id);
        if (until != null && until > now) return false;
        int ticks = (int) Math.round(p.cooldown * 20);
        map.put(p.id, now + ticks);
        if (entity instanceof ServerPlayer sp) PacketDistributor.sendToPlayer(sp, new PassiveCooldownPayload(p.id, ticks));
        return true;
    }

    /** Client : le serveur annonce qu'un passif recharge pendant "ticks" ticks. */
    public static void startClient(String passive, int ticks) {
        CLIENT.put(passive, Util.getMillis() + ticks * 50L);
    }

    /** Client : secondes de recharge restantes (0 = prêt). */
    public static int clientSecondsLeft(String passive) {
        Long until = CLIENT.get(passive);
        if (until == null) return 0;
        long left = until - Util.getMillis();
        return left <= 0 ? 0 : (int) Math.ceil(left / 1000.0);
    }
}
