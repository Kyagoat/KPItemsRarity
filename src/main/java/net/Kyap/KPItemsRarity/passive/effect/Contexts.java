package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Les contextes des passifs ("contexts": "water,rain") : l'un d'eux suffit.
 * water, rain, night, nether, end, overworld, gliding (élytres), falling (en chute), underground (sous terre, sans ciel).
 */
final class Contexts {
    private Contexts() {}

    /** Le joueur est-il dans l'un des contextes de la liste (séparés par des virgules) ? */
    static boolean any(Player player, String list) {
        for (String context : list.split(",")) {
            if (is(player, context.trim())) return true;
        }
        return false;
    }

    /** La liste du passif : "contexts", ou "context". */
    static String of(PassiveDefinition p) {
        return p.str("contexts", p.str("context", ""));
    }

    private static boolean is(Player player, String context) {
        Level level = player.level();
        BlockPos pos = player.blockPosition();
        return switch (context) {
            case "water" -> player.isInWater();
            case "rain" -> level.isRainingAt(pos) || level.isRainingAt(BlockPos.containing(player.getX(), player.getBoundingBox().maxY, player.getZ()));
            case "night" -> level.isNight();
            case "nether" -> level.dimension() == Level.NETHER;
            case "end" -> level.dimension() == Level.END;
            case "overworld" -> level.dimension() == Level.OVERWORLD;
            case "gliding" -> player.isFallFlying();
            case "falling" -> !player.onGround() && player.fallDistance > 1.5f;
            case "underground" -> !level.canSeeSky(pos) && pos.getY() < level.getSeaLevel();
            default -> false;
        };
    }
}
