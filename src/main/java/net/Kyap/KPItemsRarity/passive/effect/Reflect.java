package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;
import net.Kyap.KPItemsRarity.passive.PassiveStats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** reflect : renvoie per_point × points de "stat" (l'Armure par défaut) des dégâts de mêlée reçus. */
public final class Reflect implements PassiveEffect {
    @Override
    public String id() {
        return "reflect";
    }

    @Override
    public void onTaken(Taken hit, PassiveDefinition p) {
        Player player = hit.victim();
        DamageSource source = hit.source();
        if (!(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker
                || attacker == player || source.is(DamageTypes.THORNS)) return;
        double pts = p.str("stat", "armor").equals("armor") ? player.getArmorValue() : PassiveStats.points(player, p.str("stat", ""));
        float back = (float) (hit.damage() * p.num("per_point", 0.01) * pts);
        if (back > 0) attacker.hurt(player.damageSources().thorns(player), back);
    }
}
