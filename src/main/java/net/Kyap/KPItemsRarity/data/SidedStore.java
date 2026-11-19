package net.Kyap.KPItemsRarity.data;

import net.neoforged.fml.util.thread.EffectiveSide;

import java.util.Map;

/**
 * Garde deux copies des données chargées depuis les datapacks :
 * - "serveur" : remplie au chargement / /reload par les reload listeners ;
 * - "client"  : remplie par le paquet de synchronisation (ConfigSyncPayload).
 * Sur un serveur dédié, le client n'a PAS les datapacks : sans cette copie,
 * les tooltips et l'interface de l'enclume afficheraient des chances vides.
 */
public final class SidedStore<T> {
    private volatile Map<String, T> server = Map.of();
    private volatile Map<String, T> client = Map.of();

    public void setServer(Map<String, T> values) { this.server = Map.copyOf(values); }
    public void setClient(Map<String, T> values) { this.client = Map.copyOf(values); }

    /** Données du côté logique courant (thread serveur ou thread client). */
    public Map<String, T> get() {
        return EffectiveSide.get().isClient() ? client : server;
    }

    public Map<String, T> server() { return server; }
}
