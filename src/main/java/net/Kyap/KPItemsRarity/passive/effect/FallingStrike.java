package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;
import net.minecraft.world.entity.player.Player;

/** falling_strike : un coup de mêlée porté en tombant fait + bonus de dégâts (Piqué). */
public final class FallingStrike implements PassiveEffect {
    @Override
    public String id() {
        return "falling_strike";
    }

    @Override
    public void onAttack(Attack hit, PassiveDefinition p) {
        if (hit.melee() && !isWaiting(hit.attacker(), p)) hit.multiply(1 + p.num("bonus", 0.3));
    }

    /** N'agit qu'en l'air, en train de tomber (ni dans l'eau, ni sur une monture). */
    @Override
    public boolean isWaiting(Player wearer, PassiveDefinition p) {
        return wearer.onGround() || wearer.fallDistance <= 0 || wearer.isInWater() || wearer.isPassenger();
    }
}
