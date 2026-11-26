package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.passive.PassiveEffect;

/**
 * taunt (Provocation) : un monstre qui frappe un autre joueur à "radius" blocs du porteur se retourne contre lui.
 * Le travail se fait dans Passives (au coup reçu par l'allié) ; cette classe ne fait que déclarer l'effet.
 */
public final class Taunt implements PassiveEffect {
    public static final String ID = "taunt";

    @Override
    public String id() {
        return ID;
    }
}
