package net.Kyap.KPItemsRarity.client.panel;

import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Une ligne de l'infobulle d'une stat de la fiche : un texte, ou une source ("+3  [icône] Casque en bois"). */
sealed interface StatDetail permits StatDetail.Text, StatDetail.Source {
    record Text(Component text) implements StatDetail {}

    /** Valeur, petite icône de l'objet (une panoplie fait défiler ses pièces portées), puis son nom. */
    record Source(Component value, List<ItemStack> icons, Component name, boolean malus) implements StatDetail {
        private static final long CYCLE_MS = 1000;

        ItemStack icon() {
            if (icons.isEmpty()) return ItemStack.EMPTY;
            return icons.get((int) ((Util.getMillis() / CYCLE_MS) % icons.size()));
        }
    }
}
