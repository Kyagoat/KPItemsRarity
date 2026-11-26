package net.Kyap.KPItemsRarity.passive;

import net.Kyap.KPItemsRarity.KPItemsRarity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Depuis quand chaque joueur court, côté serveur (Charge en dépend). Le seul suivi à chaque tick des passifs :
 * une lecture de booléen et, au plus, une écriture dans une petite table.
 */
@EventBusSubscriber(modid = KPItemsRarity.MOD_ID)
public final class SprintTracker {
    private SprintTracker() {}

    private static final Map<Player, Long> SPRINT_START = new WeakHashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        if (player.isSprinting()) SPRINT_START.putIfAbsent(player, player.level().getGameTime());
        else SPRINT_START.remove(player);
    }

    /** Secondes de course sans interruption (0 s'il ne court pas). */
    public static double seconds(Player player) {
        Long start = SPRINT_START.get(player);
        return start == null ? 0 : (player.level().getGameTime() - start) / 20.0;
    }

    /** Le coup est parti : la course recommence à zéro. */
    public static void reset(Player player) {
        SPRINT_START.remove(player);
    }
}
