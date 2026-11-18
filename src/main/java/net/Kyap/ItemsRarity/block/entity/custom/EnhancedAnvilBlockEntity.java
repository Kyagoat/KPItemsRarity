package net.Kyap.ItemsRarity.block.entity.custom;

import net.Kyap.ItemsRarity.block.entity.ModBlockEntities;
import net.Kyap.ItemsRarity.block.custom.EnhancedAnvilBlock;
import net.Kyap.ItemsRarity.component.ItemStats;
import net.Kyap.ItemsRarity.component.ModDataComponents;
import net.Kyap.ItemsRarity.component.RuneData;
import net.Kyap.ItemsRarity.data.RarityData;
import net.Kyap.ItemsRarity.data.StatDefinition;
import net.Kyap.ItemsRarity.forge.ForgeMagic;
import net.Kyap.ItemsRarity.forge.RuneSources;
import net.Kyap.ItemsRarity.network.ForgeLogPayload;
import net.Kyap.ItemsRarity.util.StatEngine;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.Kyap.ItemsRarity.screen.EnhancedAnvilBlockMenu;
import net.Kyap.ItemsRarity.util.UpgradeHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Table de forgemagie (l'ancienne "Enhanced Anvil") : une case pour l'objet à forger ou briser,
 * une case pour la rune / la pierre / la ressource. Les règles sont dans forge/ForgeMagic.
 */
public class EnhancedAnvilBlockEntity extends BlockEntity implements MenuProvider {
    /** Rune, pierre de rareté ou ressource à briser. */
    public static final int RUNE_SLOT = 0;
    /** Objet à stats. */
    public static final int GEAR_SLOT = 1;
    /** Ancienne case de matériau : vidée dans l'inventaire du joueur à l'ouverture. */
    public static final int LEGACY_SLOT = 2;
    /** Anciens noms, gardés pour le rendu des objets posés. */
    public static final int RESOURCE_SLOT = RUNE_SLOT, MOD_MATERIAL_SLOT = LEGACY_SLOT;

    public static final int STATUS_WAITING = 0;
    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_FAILED = 2;

    /** Chance de monter la rareté d'un cran avec chaque pierre. */
    private static final java.util.Map<String, Float> STONES = java.util.Map.of(
            "itemsrarity:flourite", 0.35f, "itemsrarity:sulfur", 0.60f, "itemsrarity:bismuth", 0.90f);

    protected final ContainerData data;
    private int enhancementStatus = STATUS_WAITING;

    private final ItemStackHandler itemHandler = new ItemStackHandler(3) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide()) {
                // Pour que le rendu des objets posés sur la table se mette à jour chez les autres joueurs
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == GEAR_SLOT ? 1 : super.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case GEAR_SLOT -> UpgradeHelper.isItemUpgradable(stack);
                case RUNE_SLOT -> true; // rune, pierre ou ressource : vérifié au moment d'agir
                default -> false;
            };
        }
    };

    public EnhancedAnvilBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENHANCED_ANVIL_BE.get(), pos, state);
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                return index == 0 ? enhancementStatus : 0;
            }

            @Override
            public void set(int index, int value) {
                if (index == 0) enhancementStatus = value;
            }

            @Override
            public int getCount() {
                return 3;
            }
        };
    }

    public ItemStackHandler getItemHandler() {
        return itemHandler;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.itemsrarity.enhanced_anvil_block");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        // Objets laissés dans l'ancienne case de matériau : rendus au joueur
        ItemStack legacy = itemHandler.getStackInSlot(LEGACY_SLOT);
        if (!legacy.isEmpty()) {
            itemHandler.setStackInSlot(LEGACY_SLOT, ItemStack.EMPTY);
            player.getInventory().placeItemBackInInventory(legacy);
        }
        return new EnhancedAnvilBlockMenu(containerId, inventory, this, this.data);
    }

    /** Position envoyée au client même quand un autre mod ouvre le menu sans la donner. */
    @Override
    public void writeClientSideData(AbstractContainerMenu menu, net.minecraft.network.RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(worldPosition);
    }

    // ---------- Forgemagie ----------

    /** Forger : applique la rune (ou la pierre) sur l'objet. Côté serveur. */
    public ForgeMagic.Outcome forge(ServerPlayer player) {
        ItemStack gear = itemHandler.getStackInSlot(GEAR_SLOT);
        ItemStack rune = itemHandler.getStackInSlot(RUNE_SLOT);
        if (gear.isEmpty()) return send(player, ForgeMagic.Result.invalidOf("forge.itemsrarity.no_item"));
        ForgeMagic.Result result;
        RuneData data = rune.get(ModDataComponents.RUNE.get());
        Float stone = STONES.get(BuiltInRegistries.ITEM.getKey(rune.getItem()).toString());
        ItemStack forged = gear.copy();
        if (data != null) {
            result = ForgeMagic.apply(forged, data, player.getRandom());
        } else if (stone != null) {
            result = ForgeMagic.applyStone(forged, stone, player.getRandom());
        } else {
            return send(player, ForgeMagic.Result.invalidOf("forge.itemsrarity.need_rune"));
        }
        if (result.outcome() != ForgeMagic.Outcome.INVALID) {
            rune.shrink(1);
            itemHandler.setStackInSlot(RUNE_SLOT, rune.isEmpty() ? ItemStack.EMPTY : rune);
            itemHandler.setStackInSlot(GEAR_SLOT, forged);
            boolean good = result.outcome() == ForgeMagic.Outcome.CRITICAL || result.outcome() == ForgeMagic.Outcome.NEUTRAL
                    || result.outcome() == ForgeMagic.Outcome.EXO;
            enhancementStatus = good ? STATUS_SUCCESS : STATUS_FAILED;
            if (level != null) EnhancedAnvilBlock.playUpgradeAnimation(level, worldPosition, this, good);
            if (result.outcome() == ForgeMagic.Outcome.EXO && player.getServer() != null && data != null) announceExo(player, forged, data);
        }
        return send(player, result);
    }

    /**
     * Forger avec une rune de l'INVENTAIRE (boutons de l'écran, pas besoin de poser la rune).
     * repeat : recommence jusqu'à ce que la ligne monte, au plus 10 runes.
     */
    public void forgeFromInventory(ServerPlayer player, String stat, int power, boolean repeat) {
        ItemStack gear = itemHandler.getStackInSlot(GEAR_SLOT);
        if (gear.isEmpty()) {
            send(player, ForgeMagic.Result.invalidOf("forge.itemsrarity.no_item"));
            return;
        }
        RuneData wanted = new RuneData(stat, power);
        java.util.List<Component> log = new java.util.ArrayList<>();
        ItemStack forged = gear.copy();
        boolean any = false, good = false;
        for (int attempt = 0; attempt < (repeat ? 10 : 1); attempt++) {
            int slot = findRune(player, wanted);
            if (slot < 0) {
                if (!any) log.add(Component.translatable("forge.itemsrarity.no_rune_in_inventory").withStyle(net.minecraft.ChatFormatting.GRAY));
                break;
            }
            ForgeMagic.Result result = ForgeMagic.apply(forged, wanted, player.getRandom());
            if (result.outcome() == ForgeMagic.Outcome.INVALID) {
                if (!any) log.addAll(result.log());
                break;
            }
            any = true;
            player.getInventory().getItem(slot).shrink(1);
            if (repeat && attempt > 0) log.add(Component.empty());
            log.addAll(result.log());
            good = result.outcome() == ForgeMagic.Outcome.CRITICAL || result.outcome() == ForgeMagic.Outcome.NEUTRAL;
            if (good) break;
        }
        if (any) {
            itemHandler.setStackInSlot(GEAR_SLOT, forged);
            enhancementStatus = good ? STATUS_SUCCESS : STATUS_FAILED;
            if (level != null) EnhancedAnvilBlock.playUpgradeAnimation(level, worldPosition, this, good);
            player.containerMenu.broadcastChanges();
        }
        // Le journal garde les 16 dernières lignes de cette action
        if (log.size() > 16) log = log.subList(log.size() - 16, log.size());
        PacketDistributor.sendToPlayer(player, new ForgeLogPayload(log));
    }

    private static int findRune(ServerPlayer player, RuneData wanted) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && wanted.equals(s.get(ModDataComponents.RUNE.get()))) return i;
        }
        return -1;
    }

    /** Briser : l'objet (selon ses jets) ou, sans objet, la pile de ressources. Les runes vont au joueur. */
    public void breakItems(ServerPlayer player) {
        ItemStack gear = itemHandler.getStackInSlot(GEAR_SLOT);
        ItemStack resource = itemHandler.getStackInSlot(RUNE_SLOT);
        java.util.List<ItemStack> runes;
        Component broken;
        if (!gear.isEmpty()) {
            StatEngine.ensureRolled(gear, player.getRandom());
            broken = gear.getHoverName();
            runes = ForgeMagic.breakItem(gear, player.getRandom());
            itemHandler.setStackInSlot(GEAR_SLOT, ItemStack.EMPTY);
        } else if (!resource.isEmpty() && !RuneSources.dropsFor(resource).isEmpty()) {
            broken = Component.literal(resource.getCount() + " × ").append(resource.getHoverName());
            runes = ForgeMagic.breakResource(resource, player.getRandom());
            itemHandler.setStackInSlot(RUNE_SLOT, ItemStack.EMPTY);
        } else {
            send(player, ForgeMagic.Result.invalidOf("forge.itemsrarity.nothing_to_break"));
            return;
        }
        java.util.List<Component> log = new java.util.ArrayList<>();
        log.add(Component.translatable("forge.itemsrarity.broken", broken).withStyle(net.minecraft.ChatFormatting.GOLD));
        if (runes.isEmpty()) log.add(Component.translatable("forge.itemsrarity.no_rune").withStyle(net.minecraft.ChatFormatting.GRAY));
        for (ItemStack r : runes) {
            log.add(Component.literal(r.getCount() + " × ").withStyle(net.minecraft.ChatFormatting.GRAY).append(r.getHoverName()));
            player.getInventory().placeItemBackInInventory(r);
        }
        if (level != null) level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.GRINDSTONE_USE,
                net.minecraft.sounds.SoundSource.BLOCKS, 1f, 0.9f);
        send(player, new ForgeMagic.Result(ForgeMagic.Outcome.CRITICAL, log));
    }

    private static ForgeMagic.Outcome send(ServerPlayer player, ForgeMagic.Result result) {
        PacketDistributor.sendToPlayer(player, new ForgeLogPayload(result.log()));
        return result.outcome();
    }

    /** Annonce anonyme à tout le serveur : un exo, c'est un événement. */
    private static void announceExo(ServerPlayer player, ItemStack item, RuneData rune) {
        StatDefinition stat = RarityData.stat(rune.stat());
        ItemStats stats = item.get(ModDataComponents.STATS);
        if (stat == null || stats == null) return;
        Component msg = Component.translatable("forge.itemsrarity.exo_announce",
                Component.literal(stat.formatValue(stats.exoValue(), true) + " ").append(stat.name()).withStyle(s -> s.withColor(0xFF5FB8)),
                item.getHoverName()).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE);
        player.getServer().getPlayerList().broadcastSystemMessage(msg, false);
    }

    // ---------- Sauvegarde ----------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", itemHandler.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        itemHandler.deserializeNBT(registries, tag.getCompound("inventory"));
    }

    // ---------- Synchro client (rendu des objets sur l'enclume) ----------

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
