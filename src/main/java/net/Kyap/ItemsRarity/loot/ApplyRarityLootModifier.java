package net.Kyap.ItemsRarity.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.Kyap.ItemsRarity.util.StatEngine;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

import java.util.List;

/**
 * Donne leurs jets aux équipements générés par certaines tables de loot.
 * Voir data/itemsrarity/loot_modifiers/chest_rarity.json :
 *  "table_prefixes": chemins de tables concernés ("chests/", "gameplay/fishing"...). Vide = toutes.
 */
public class ApplyRarityLootModifier extends LootModifier {
    public static final MapCodec<ApplyRarityLootModifier> CODEC = RecordCodecBuilder.mapCodec(inst -> codecStart(inst)
            .and(Codec.STRING.listOf().optionalFieldOf("table_prefixes", List.of()).forGetter(m -> m.tablePrefixes))
            .apply(inst, ApplyRarityLootModifier::new));

    private final List<String> tablePrefixes;

    public ApplyRarityLootModifier(LootItemCondition[] conditions, List<String> tablePrefixes) {
        super(conditions);
        this.tablePrefixes = tablePrefixes;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (!tablePrefixes.isEmpty()) {
            String path = context.getQueriedLootTableId().getPath();
            if (tablePrefixes.stream().noneMatch(path::startsWith)) return generatedLoot;
        }
        for (ItemStack stack : generatedLoot) {
            StatEngine.ensureRolled(stack, context.getRandom());
        }
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
