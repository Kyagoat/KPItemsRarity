package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.CritTracker;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.WeakHashMap;

/** charged_strike (Mèche) : après "idle" secondes sans frapper, le coup de mêlée suivant est un critique. */
public final class ChargedStrike implements PassiveEffect {
    /** Dernier coup de mêlée lancé (temps du monde), par joueur. */
    private final Map<Player, Long> last = new WeakHashMap<>();

    @Override
    public String id() {
        return "charged_strike";
    }

    @Override
    public void onSwing(Swing swing, PassiveDefinition p) {
        Player attacker = swing.attacker();
        long now = attacker.level().getGameTime();
        Long before = last.put(attacker, now);
        if (before == null || now - before >= p.num("idle", 2) * 20) CritTracker.forceNext(attacker);
    }

    @Override
    public boolean isWaiting(Player wearer, PassiveDefinition p) {
        Long before = last.get(wearer);
        return before != null && wearer.level().getGameTime() - before < p.num("idle", 2) * 20;
    }
}
