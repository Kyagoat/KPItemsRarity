package net.Kyap.KPItemsRarity.loot;

import net.Kyap.KPItemsRarity.KPItemsRarity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModLootModifiers {
    private ModLootModifiers() {}

    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, KPItemsRarity.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<ApplyRarityLootModifier>> APPLY_RARITY =
            LOOT_MODIFIERS.register("apply_rarity", () -> ApplyRarityLootModifier.CODEC);

    public static final DeferredRegister<LootItemConditionType> CONDITIONS =
            DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, KPItemsRarity.MOD_ID);

    /** Chance de drop multipliée par la Prospection du tueur (voir ProspectingChanceCondition). */
    public static final DeferredHolder<LootItemConditionType, LootItemConditionType> PROSPECTING_CHANCE =
            CONDITIONS.register("prospecting_chance", () -> new LootItemConditionType(ProspectingChanceCondition.CODEC));

    public static void register(IEventBus eventBus) {
        LOOT_MODIFIERS.register(eventBus);
        CONDITIONS.register(eventBus);
    }
}
