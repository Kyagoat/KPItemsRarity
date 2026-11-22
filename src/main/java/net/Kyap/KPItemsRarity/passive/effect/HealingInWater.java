package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;
import net.minecraft.world.entity.player.Player;

/** healing_in_water : soins reçus × multiplier dans l'eau. */
public final class HealingInWater implements PassiveEffect {
    @Override
    public String id() {
        return "healing_in_water";
    }

    @Override
    public void onHeal(Heal heal, PassiveDefinition p) {
        if (!isWaiting(heal.player(), p)) heal.event().setAmount((float) (heal.event().getAmount() * p.num("multiplier", 2)));
    }

    @Override
    public boolean isWaiting(Player wearer, PassiveDefinition p) {
        return !wearer.isInWater();
    }
}
