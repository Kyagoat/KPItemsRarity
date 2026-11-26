package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.PassiveArrows;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * volley : un tir sur every_nth part en éventail : extra flèches de plus, écartées de spread degrés (Salve).
 * Réglages : every_nth (3), extra (2, réparties des deux côtés), spread (10), crossbow_only (true par défaut).
 * Les flèches d'un même tir (Tir multiple) comptent pour un seul.
 */
public final class Volley implements PassiveEffect {
    private static final class Count {
        long lastShotTick = -1;
        int shots;
    }

    private final Map<Player, Count> counts = new WeakHashMap<>();

    @Override
    public String id() {
        return "volley";
    }

    @Override
    public void onShoot(Shot shot, PassiveDefinition p) {
        boolean crossbowOnly = !p.params.has("crossbow_only") || p.flag("crossbow_only");
        if (crossbowOnly && !shot.arrow().shotFromCrossbow()) return;
        Player shooter = shot.shooter();
        long now = shooter.level().getGameTime();
        Count count = counts.computeIfAbsent(shooter, k -> new Count());
        if (count.lastShotTick == now) return;
        count.lastShotTick = now;
        if (++count.shots < (int) p.num("every_nth", 3)) return;
        count.shots = 0;
        int extra = (int) p.num("extra", 2);
        float spread = (float) p.num("spread", 10);
        for (int i = 0; i < extra; i++) {
            int side = i % 2 == 0 ? 1 : -1;
            PassiveArrows.fireCopy(shooter, shot.arrow(), side * spread * (i / 2 + 1));
        }
    }
}
