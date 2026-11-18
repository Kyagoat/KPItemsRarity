package net.Kyap.ItemsRarity.network;

import net.Kyap.ItemsRarity.ItemsRarity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client -> serveur : "je partage cet objet dans le chat" (touche N par défaut).
 * En survie, le client n'envoie QUE l'emplacement : le serveur lit lui-même l'objet, on ne peut donc pas
 * partager un objet qu'on n'a pas. En créatif (où l'on peut de toute façon tout créer), le client envoie l'objet.
 *   containerId >= 0 : emplacement du menu ouvert ; containerId = -1 : objet en main ; -2 : objet envoyé (créatif).
 */
public record ShareItemPayload(int containerId, int slot, ItemStack creativeStack) implements CustomPacketPayload {

    public static final int HELD = -1;
    public static final int CREATIVE = -2;

    public static final Type<ShareItemPayload> TYPE = new Type<>(ItemsRarity.id("share_item"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShareItemPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ShareItemPayload::containerId,
            ByteBufCodecs.VAR_INT, ShareItemPayload::slot,
            ItemStack.OPTIONAL_STREAM_CODEC, ShareItemPayload::creativeStack,
            ShareItemPayload::new);

    private static final long COOLDOWN_TICKS = 60; // 3 secondes, anti-spam
    private static final Map<UUID, Long> LAST_SHARE = new HashMap<>();

    public static ShareItemPayload slot(int containerId, int slot) {
        return new ShareItemPayload(containerId, slot, ItemStack.EMPTY);
    }

    public static ShareItemPayload held() {
        return new ShareItemPayload(HELD, -1, ItemStack.EMPTY);
    }

    public static ShareItemPayload creative(ItemStack stack) {
        return new ShareItemPayload(CREATIVE, -1, stack.copyWithCount(1));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(ShareItemPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;

        long now = player.serverLevel().getGameTime();
        Long last = LAST_SHARE.get(player.getUUID());
        if (last != null && now - last < COOLDOWN_TICKS) {
            player.displayClientMessage(Component.translatable("chat.itemsrarity.share_cooldown"), true);
            return;
        }

        ItemStack stack = ItemStack.EMPTY;
        AbstractContainerMenu menu = player.containerMenu;
        if (payload.containerId() == CREATIVE) {
            if (player.isCreative()) stack = payload.creativeStack();
        } else if (payload.containerId() == HELD) {
            stack = player.getMainHandItem();
        } else if (menu.containerId == payload.containerId() && payload.slot() >= 0 && payload.slot() < menu.slots.size()) {
            stack = menu.getSlot(payload.slot()).getItem();
        }
        if (stack.isEmpty()) {
            ItemsRarity.LOGGER.debug("Partage refusé pour {} : objet introuvable ({} / {})",
                    player.getGameProfile().getName(), payload.containerId(), payload.slot());
            return;
        }

        LAST_SHARE.put(player.getUUID(), now);
        player.server.getPlayerList().broadcastSystemMessage(
                Component.translatable("chat.itemsrarity.share", player.getDisplayName(), stack.getDisplayName()), false);
    }
}
