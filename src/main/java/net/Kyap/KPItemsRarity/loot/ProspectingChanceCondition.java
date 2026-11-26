package net.Kyap.KPItemsRarity.loot;

import net.Kyap.KPItemsRarity.passive.Prospecting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;

/**
 * kpitemsrarity:prospecting_chance : chance de drop (+ looting par niveau de Butin), multipliée par la Prospection du
 * joueur qui a tué (+50 % de Prospection = 1,5 × la chance, comme dans Dofus).
 * { "condition": "kpitemsrarity:prospecting_chance", "chance": 0.02, "looting": 0.01 }
 */
public record ProspectingChanceCondition(float chance, float looting) implements LootItemCondition {
    public static final MapCodec<ProspectingChanceCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.fieldOf("chance").forGetter(ProspectingChanceCondition::chance),
            Codec.FLOAT.optionalFieldOf("looting", 0f).forGetter(ProspectingChanceCondition::looting)
    ).apply(instance, ProspectingChanceCondition::new));

    @Override
    public LootItemConditionType getType() {
        return ModLootModifiers.PROSPECTING_CHANCE.get();
    }

    @Override
    public boolean test(LootContext context) {
        double p = chance;
        Entity killer = context.getParamOrNull(LootContextParams.ATTACKING_ENTITY);
        if (killer instanceof Player player) {
            if (looting > 0) {
                var lookup = context.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                p += looting * EnchantmentHelper.getEnchantmentLevel(lookup.getOrThrow(Enchantments.LOOTING), player);
            }
            p *= 1 + Prospecting.of(player);
        }
        return context.getRandom().nextFloat() < p;
    }
}
