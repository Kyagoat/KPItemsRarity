package net.Kyap.ItemsRarity.events;

import net.Kyap.ItemsRarity.Config;
import net.Kyap.ItemsRarity.ItemsRarity;
import net.Kyap.ItemsRarity.util.StatEngine;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Donne leurs jets aux équipements qui n'en ont pas encore :
 *  - craft, drop de mob, coffres/pêche (loot/ApplyRarityLootModifier)
 *  - tout le reste via un scan léger de l'inventaire (échanges, table de forgeron, commandes...).
 * La rareté vient de la définition de l'objet (kp_items), elle n'est plus tirée au hasard.
 * Côté serveur uniquement : le client reçoit le résultat via les data components.
 */
@EventBusSubscriber(modid = ItemsRarity.MOD_ID)
public final class RarityAssignmentEvents {
    private RarityAssignmentEvents() {}

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        StatEngine.ensureRolled(event.getCrafting(), player.getRandom());
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        for (ItemEntity itemEntity : event.getDrops()) {
            StatEngine.ensureRolled(itemEntity.getItem(), event.getEntity().getRandom());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        int interval = Config.INVENTORY_SCAN_INTERVAL.get();
        if (interval <= 0 || player.tickCount % interval != 0) return;

        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            StatEngine.ensureRolled(inventory.getItem(i), player.getRandom());
        }
    }
}
