package net.Kyap.KPItemsRarity.data;

import net.neoforged.fml.util.thread.EffectiveSide;

/**
 * Une donnée gardée en deux exemplaires : celui du serveur (lu dans les datapacks) et celui du client (reçu du
 * serveur, voir ConfigSyncPayload). Sur un serveur dédié le client n'a pas les datapacks ; en solo, le serveur
 * intégré et le client tournent côte à côte et chacun lit le sien.
 */
final class Sided<T> {
    private volatile T server;
    private volatile T client;

    Sided(T empty) {
        this.server = empty;
        this.client = empty;
    }

    void setServer(T value) {
        this.server = value;
    }

    void setClient(T value) {
        this.client = value;
    }

    /** L'exemplaire du côté qui demande (thread du serveur ou thread du client). */
    T get() {
        return EffectiveSide.get().isClient() ? client : server;
    }
}
