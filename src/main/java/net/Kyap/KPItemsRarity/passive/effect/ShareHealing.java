package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;
import net.minecraft.world.entity.player.Player;

/** share_healing : ratio des soins reçus soigne aussi les joueurs dans "radius" blocs. */
public final class ShareHealing implements PassiveEffect {
    @Override
    public String id() {
        return "share_healing";
    }

    @Override
    public void onHeal(Heal heal, PassiveDefinition p) {
        if (heal.event().getAmount() <= 0) return;
        Player player = heal.player();
        float part = (float) (heal.event().getAmount() * p.num("ratio", 0.3));
        for (Player other : player.level().getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(p.num("radius", 6)),
                o -> o != player && o.isAlive())) {
            other.heal(part);
        }
    }
}
