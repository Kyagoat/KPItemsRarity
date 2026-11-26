package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;
import net.Kyap.KPItemsRarity.passive.PassiveStats;

/**
 * stat_conversion : "amount" points de la stat "to" par tranche de "per" points de la stat "from", tant que le passif
 * est porté (Bras du Vide : 5 points de Portée, soit 0,5 bloc, par tranche de 10 % d'Agilité). Recalculé avec les
 * bonus de panoplie, au changement d'équipement.
 */
public final class StatConversion implements PassiveEffect {
    @Override
    public String id() {
        return "stat_conversion";
    }

    @Override
    public void onRefresh(Refresh refresh, PassiveDefinition p) {
        double per = Math.max(1e-6, p.num("per", 10));
        double steps = Math.floor(PassiveStats.points(refresh.player(), p.str("from", "agility")) / per);
        if (steps > 0) refresh.addStat().accept(p.str("to", "range"), steps * p.num("amount", 1));
    }
}
