package net.Kyap.KPItemsRarity.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;

/** Better Combat (optionnel, appelé par réflexion) : la portée d'attaque réelle de l'arme en main. */
public final class BetterCombatCompat {
    private BetterCombatCompat() {}

    private static Method rangeForItem;
    private static boolean checked;

    /** Portée d'attaque de l'arme en main selon Better Combat, ou -1 s'il n'est pas installé. */
    public static double attackRange(Player player) {
        if (!checked) {
            checked = true;
            try {
                rangeForItem = Class.forName("net.bettercombat.logic.PlayerAttackHelper")
                        .getMethod("getRangeForItem", Player.class, ItemStack.class);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                rangeForItem = null;
            }
        }
        if (rangeForItem == null) return -1;
        try {
            return (double) rangeForItem.invoke(null, player, player.getMainHandItem());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }
}
