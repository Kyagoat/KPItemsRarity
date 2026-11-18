package net.Kyap.ItemsRarity.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.Kyap.ItemsRarity.ItemsRarity;
import net.Kyap.ItemsRarity.network.ShareItemPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Touches du mod (modifiables dans Options > Commandes > Items Rarity) :
 *  - Partager l'objet (N) : dans un inventaire, l'objet sous la souris ; en jeu, l'objet en main.
 *    (B était pris par Atmospherics, qui ouvre son menu et avalait la touche.)
 *  - Détails de panoplie (Alt gauche, maintenu) : carte de la panoplie de l'objet survolé.
 */
@EventBusSubscriber(modid = ItemsRarity.MOD_ID, value = Dist.CLIENT)
public final class ShareKey {
    private ShareKey() {}

    public static final String CATEGORY = "key.categories.itemsrarity";

    public static final KeyMapping SHARE = new KeyMapping("key.itemsrarity.share",
            KeyConflictContext.UNIVERSAL, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY);

    public static final KeyMapping SET_DETAILS = new KeyMapping("key.itemsrarity.set_details",
            KeyConflictContext.GUI, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY);

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(SHARE);
        event.register(SET_DETAILS);
    }

    /** Vrai tant que la touche "Détails de panoplie" est enfoncée (lue directement au clavier, marche dans les écrans). */
    public static boolean isSetDetailsDown() {
        InputConstants.Key key = SET_DETAILS.getKey();
        if (key.getType() != InputConstants.Type.KEYSYM || key.getValue() == InputConstants.UNKNOWN.getValue()) return false;
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), key.getValue());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onScreenKey(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        if (screen.getFocused() instanceof EditBox) return; // renommage à l'enclume, recherche du créatif...
        if (!SHARE.isActiveAndMatches(InputConstants.getKey(event.getKeyCode(), event.getScanCode()))) return;

        Slot slot = screen.getSlotUnderMouse();
        if (slot == null || !slot.hasItem()) {
            hint();
            event.setCanceled(true);
            return;
        }
        if (screen instanceof CreativeModeInventoryScreen) {
            // Les emplacements du créatif n'existent que côté client : on envoie l'objet lui-même.
            PacketDistributor.sendToServer(ShareItemPayload.creative(slot.getItem()));
        } else {
            PacketDistributor.sendToServer(ShareItemPayload.slot(screen.getMenu().containerId, slot.index));
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (SHARE.consumeClick()) {
            if (mc.player == null || mc.screen != null) continue;
            if (mc.player.getMainHandItem().isEmpty()) {
                hint();
                continue;
            }
            PacketDistributor.sendToServer(ShareItemPayload.held());
        }
    }

    private static void hint() {
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.translatable("chat.itemsrarity.share_hint"), true);
    }
}
