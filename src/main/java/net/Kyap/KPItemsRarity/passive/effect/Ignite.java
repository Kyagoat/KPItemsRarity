package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;
import net.minecraft.world.entity.LivingEntity;

/**
 * ignite : met le feu. when = "attack" (défaut) : tes coups de mêlée enflamment la cible ; when = "defend" : qui te
 * frappe au corps à corps prend feu. Réglage : seconds (3).
 */
public final class Ignite implements PassiveEffect {
    @Override
    public String id() {
        return "ignite";
    }

    @Override
    public void onAttack(Attack hit, PassiveDefinition p) {
        if (hit.melee() && p.str("when", "attack").equals("attack")) hit.target().igniteForSeconds((float) p.num("seconds", 3));
    }

    @Override
    public void onTaken(Taken hit, PassiveDefinition p) {
        if (!p.str("when", "attack").equals("defend")) return;
        if (hit.source().getEntity() instanceof LivingEntity attacker && hit.source().getDirectEntity() == attacker && attacker != hit.victim()) {
            attacker.igniteForSeconds((float) p.num("seconds", 3));
        }
    }
}
