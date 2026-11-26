package net.Kyap.KPItemsRarity.passive;

import net.Kyap.KPItemsRarity.KPItemsRarity;
import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.passive.effect.ContextProspection;
import net.Kyap.KPItemsRarity.registry.ModAttributes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * La Prospection (stat « Prospection », passifs de contexte) : plus de butin sur les monstres tués, comme dans Dofus.
 *   - les objets du mod qui tombent au hasard passent par la condition kpitemsrarity:prospecting_chance
 *     (chance × (1 + Prospection)) ;
 *   - les autres drops empilables (os, poudre, cuir...) gagnent des exemplaires : +50 % de Prospection = une chance sur
 *     deux d'en avoir un de plus par pile. Les équipements portés par les monstres ne sont jamais copiés.
 */
@EventBusSubscriber(modid = KPItemsRarity.MOD_ID)
public final class Prospecting {
    private Prospecting() {}

    /** Prospection du joueur, en fraction (0,3 = +30 %) : sa stat et ses passifs qui comptent ici et maintenant. */
    public static double of(Player player) {
        var attribute = player.getAttribute(ModAttributes.PROSPECTING);
        double total = attribute != null ? attribute.getValue() : 0;
        for (PassiveDefinition p : ActivePassives.list(player)) {
            if (ContextProspection.ID.equals(p.effect)) total += ContextProspection.amount(player, p);
        }
        return Math.max(0, total);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player) || player.level().isClientSide() || event.getDrops().isEmpty()) return;
        double bonus = of(player);
        if (bonus <= 0) return;
        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem();
            if (!stack.isStackable() || stack.isDamageableItem() || ownItem(stack)) continue;
            int extra = (int) bonus + (player.getRandom().nextDouble() < bonus - (int) bonus ? 1 : 0);
            if (extra > 0) stack.setCount(Math.min(stack.getMaxStackSize(), stack.getCount() + extra));
        }
    }

    /** Les objets des mods KP ont déjà leur Prospection dans leur table de drop. */
    private static boolean ownItem(ItemStack stack) {
        String ns = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        return ns.equals("kpsets") || ns.equals(KPItemsRarity.MOD_ID);
    }
}
