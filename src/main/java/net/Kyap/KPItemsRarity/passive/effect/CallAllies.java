package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.Allies;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * call_allies : les créatures d'une famille, à "radius" blocs, attaquent ce qui frappe le porteur (Pacte de l'or :
 * les Piglins). Réglages : tag ("kpitemsrarity:piglins"), radius (16).
 */
public final class CallAllies implements PassiveEffect {
    @Override
    public String id() {
        return "call_allies";
    }

    @Override
    public boolean onDefend(Defense hit, PassiveDefinition p) {
        Player victim = hit.victim();
        if (!(hit.source().getEntity() instanceof LivingEntity attacker) || attacker == victim || !attacker.isAlive()) return false;
        String tag = p.str("tag", "");
        if (Families.is(attacker, tag)) return false; // un allié qui frappe par erreur ne se fait pas lyncher
        double radius = Math.min(32, p.num("radius", 16));
        for (Mob ally : victim.level().getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(radius),
                m -> m.isAlive() && Families.is(m, tag) && !Allies.targets(m, attacker))) {
            Allies.setTarget(ally, attacker);
        }
        return false;
    }
}
