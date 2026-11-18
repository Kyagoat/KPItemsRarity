package net.Kyap.ItemsRarity.screen;

import net.Kyap.ItemsRarity.block.ModBlocks;
import net.Kyap.ItemsRarity.block.entity.custom.EnhancedAnvilBlockEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;

public class EnhancedAnvilBlockMenu extends AbstractContainerMenu {
    public final EnhancedAnvilBlockEntity blockEntity;
    private final Level level;
    private final ContainerData data;

    // Constructeur côté client (données envoyées par player.openMenu(anvil, pos))
    public EnhancedAnvilBlockMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, clientTable(inventory, extraData), new SimpleContainerData(3));
    }

    /**
     * La table côté client. Normalement sa position arrive avec l'ouverture ; certains mods (Cinematic
     * Interactions) ouvrent le menu sans elle : on prend alors la table visée, sinon une table "fantôme"
     * (les objets des cases sont de toute façon synchronisés par le serveur).
     */
    private static BlockEntity clientTable(Inventory inventory, RegistryFriendlyByteBuf extraData) {
        var level = inventory.player.level();
        if (extraData != null && extraData.readableBytes() > 0) {
            BlockEntity be = level.getBlockEntity(extraData.readBlockPos());
            if (be instanceof EnhancedAnvilBlockEntity) return be;
        }
        if (level.isClientSide()) {
            BlockEntity looked = net.Kyap.ItemsRarity.client.ClientLook.lookedAtBlockEntity();
            if (looked instanceof EnhancedAnvilBlockEntity) return looked;
        }
        return new EnhancedAnvilBlockEntity(net.minecraft.core.BlockPos.ZERO, ModBlocks.ENHANCED_ANVIL_BLOCK.get().defaultBlockState());
    }

    public EnhancedAnvilBlockMenu(int containerId, Inventory inventory, BlockEntity entity, ContainerData data) {
        super(ModMenuTypes.ENHANCED_ANVIL_MENU.get(), containerId);
        checkContainerDataCount(data, 3);
        this.blockEntity = (EnhancedAnvilBlockEntity) entity;
        this.level = inventory.player.level();
        this.data = data;

        // Les cases de la table D'ABORD (0 = rune, 1 = objet), comme les menus vanilla :
        // Cinematic Interactions lit les cases de poste en tête de menu.
        var handler = blockEntity.getItemHandler();
        this.addSlot(new SlotItemHandler(handler, EnhancedAnvilBlockEntity.RUNE_SLOT, 27, 47));
        this.addSlot(new SlotItemHandler(handler, EnhancedAnvilBlockEntity.GEAR_SLOT, 80, 15));

        addPlayerHotbar(inventory);
        addPlayerInventory(inventory);

        addDataSlots(data);
    }

    public int getStatus() { return data.get(0); }

    public ItemStack getRuneStack() { return slots.get(TE_INVENTORY_FIRST_SLOT_INDEX).getItem(); }
    public ItemStack getGearStack() { return slots.get(TE_INVENTORY_FIRST_SLOT_INDEX + 1).getItem(); }

    /** Boutons de l'écran (envoyés par vanilla) : 0 = Forger, 1 = Briser. */
    public static final int BUTTON_FORGE = 0, BUTTON_BREAK = 1;

    /**
     * Rune prise directement dans l'inventaire : id = LINE_BUTTONS + ligne * 3 + taille (0 rune, 1 Pa, 2 Ra),
     * + REPEAT pour "jusqu'à ce que ça passe" (Maj). La ligne suit l'ordre des jets de l'objet.
     */
    public static final int LINE_BUTTONS = 100, REPEAT = 10000;
    public static final int[] SIZES = {net.Kyap.ItemsRarity.component.RuneData.BASE, net.Kyap.ItemsRarity.component.RuneData.PA,
            net.Kyap.ItemsRarity.component.RuneData.RA};

    public static int lineButton(int line, int size, boolean repeat) {
        return LINE_BUTTONS + line * 3 + size + (repeat ? REPEAT : 0);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return false;
        if (id == BUTTON_FORGE) blockEntity.forge(serverPlayer);
        else if (id == BUTTON_BREAK) blockEntity.breakItems(serverPlayer);
        else if (id >= LINE_BUTTONS) {
            boolean repeat = id >= REPEAT;
            int rest = (repeat ? id - REPEAT : id) - LINE_BUTTONS;
            var stats = getGearStack().get(net.Kyap.ItemsRarity.component.ModDataComponents.STATS.get());
            if (stats == null) return false;
            var ids = new java.util.ArrayList<>(stats.lines().keySet());
            int line = rest / 3, size = rest % 3;
            if (line < 0 || line >= ids.size()) return false;
            blockEntity.forgeFromInventory(serverPlayer, ids.get(line), SIZES[size], repeat);
        } else return false;
        broadcastChanges();
        return true;
    }

    private static final int HOTBAR_SLOT_COUNT = 9;
    private static final int PLAYER_INVENTORY_SLOT_COUNT = 27;
    private static final int VANILLA_SLOT_COUNT = HOTBAR_SLOT_COUNT + PLAYER_INVENTORY_SLOT_COUNT;
    public static final int TE_INVENTORY_FIRST_SLOT_INDEX = 0;
    private static final int TE_INVENTORY_SLOT_COUNT = 2;
    private static final int VANILLA_FIRST_SLOT_INDEX = TE_INVENTORY_FIRST_SLOT_INDEX + TE_INVENTORY_SLOT_COUNT;

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot sourceSlot = slots.get(index);
        if (!sourceSlot.hasItem()) return ItemStack.EMPTY;
        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack copy = sourceStack.copy();

        if (index < TE_INVENTORY_FIRST_SLOT_INDEX + TE_INVENTORY_SLOT_COUNT) {
            // Table -> inventaire du joueur
            if (!moveItemStackTo(sourceStack, VANILLA_FIRST_SLOT_INDEX, VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index < VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT) {
            // Inventaire joueur -> table : un objet à stats va dans sa case, le reste (runes...) dans l'autre
            if (!moveItemStackTo(sourceStack, TE_INVENTORY_FIRST_SLOT_INDEX + 1, TE_INVENTORY_FIRST_SLOT_INDEX + 2, false)
                    && !moveItemStackTo(sourceStack, TE_INVENTORY_FIRST_SLOT_INDEX, TE_INVENTORY_FIRST_SLOT_INDEX + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (sourceStack.isEmpty()) {
            sourceSlot.set(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }
        sourceSlot.onTake(player, sourceStack);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(level, blockEntity.getBlockPos()), player, ModBlocks.ENHANCED_ANVIL_BLOCK.get());
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory inventory) {
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(inventory, i, 8 + i * 18, 142));
        }
    }
}
