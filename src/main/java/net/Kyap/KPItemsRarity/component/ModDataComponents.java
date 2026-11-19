package net.Kyap.KPItemsRarity.component;

import net.Kyap.KPItemsRarity.KPItemsRarity;
import net.Kyap.KPItemsRarity.util.ModRarities.ModRarity;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Données stockées sur les ItemStack. Persistantes et synchronisées au client. */
public final class ModDataComponents {
    private ModDataComponents() {}

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, KPItemsRarity.MOD_ID);

    /** Rareté de l'objet (fixée par sa définition). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ModRarity>> RARITY =
            COMPONENTS.registerComponentType("rarity", builder -> builder
                    .persistent(ModRarity.CODEC)
                    .networkSynchronized(ModRarity.STREAM_CODEC));

    /** Jets de l'objet. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemStats>> STATS =
            COMPONENTS.registerComponentType("stats", builder -> builder
                    .persistent(ItemStats.CODEC)
                    .networkSynchronized(ItemStats.STREAM_CODEC));

    /**
     * Ancien système d'effets aléatoires. Gardé UNIQUEMENT pour que les objets déjà créés se chargent
     * sans erreur ; il est retiré des objets dès qu'ils reçoivent leurs jets.
     */
    @Deprecated
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RolledEffects>> LEGACY_EFFECTS =
            COMPONENTS.registerComponentType("effects", builder -> builder
                    .persistent(RolledEffects.CODEC)
                    .networkSynchronized(RolledEffects.STREAM_CODEC));

    public static void register(IEventBus eventBus) {
        COMPONENTS.register(eventBus);
    }
}
