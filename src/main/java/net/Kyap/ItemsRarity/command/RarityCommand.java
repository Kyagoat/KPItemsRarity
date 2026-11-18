package net.Kyap.ItemsRarity.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.Kyap.ItemsRarity.ItemsRarity;
import net.Kyap.ItemsRarity.component.ItemStats;
import net.Kyap.ItemsRarity.component.ModDataComponents;
import net.Kyap.ItemsRarity.data.ItemDefinition;
import net.Kyap.ItemsRarity.data.RarityData;
import net.Kyap.ItemsRarity.data.StatDefinition;
import net.Kyap.ItemsRarity.util.ModRarities.ModRarity;
import net.Kyap.ItemsRarity.util.StatEngine;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Commandes de test (niveau OP 2), sur l'objet en main :
 *  /rarity roll                 nouveaux jets dans les fourchettes
 *  /rarity max                  jets parfaits
 *  /rarity upgrade              monte la rareté d'un cran (lignes au max, fourchettes +10 %)
 *  /rarity set <stat> <valeur>  force une ligne (même hors fourchette, pour tester l'overmax)
 *  /rarity exo <stat> <valeur>  ajoute un exo (valeur 0 = retire l'exo)
 *  /rarity remove               retire jets et rareté (ils reviendront au prochain scan)
 *  /rarity info                 détail des jets
 */
@EventBusSubscriber(modid = ItemsRarity.MOD_ID)
public final class RarityCommand {
    private RarityCommand() {}

    private static final SimpleCommandExceptionType NO_STATS =
            new SimpleCommandExceptionType(Component.translatable("commands.itemsrarity.no_stats"));
    private static final SimpleCommandExceptionType UNKNOWN_STAT =
            new SimpleCommandExceptionType(Component.translatable("commands.itemsrarity.unknown_stat"));

    private static final SuggestionProvider<CommandSourceStack> STATS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(RarityData.statsSorted().stream().filter(StatDefinition::isActive).map(s -> s.id), builder);

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("rarity")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("roll").executes(RarityCommand::roll))
                .then(Commands.literal("max").executes(RarityCommand::max))
                .then(Commands.literal("upgrade").executes(RarityCommand::upgrade))
                .then(Commands.literal("set")
                        .then(Commands.argument("stat", StringArgumentType.word()).suggests(STATS)
                                .then(Commands.argument("value", IntegerArgumentType.integer(-1000, 1000))
                                        .executes(RarityCommand::setLine))))
                .then(Commands.literal("exo")
                        .then(Commands.argument("stat", StringArgumentType.word()).suggests(STATS)
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 1000))
                                        .executes(RarityCommand::exo))))
                .then(Commands.literal("remove").executes(RarityCommand::remove))
                .then(Commands.literal("info").executes(RarityCommand::info)));
    }

    private static ItemStack held(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (RarityData.itemDef(stack) == null) throw NO_STATS.create();
        return stack;
    }

    private static int roll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ItemStack stack = held(ctx);
        StatEngine.roll(stack, RarityData.itemDef(stack), ctx.getSource().getLevel().getRandom());
        return info(ctx);
    }

    private static int max(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ItemStack stack = held(ctx);
        StatEngine.maxOut(stack, RarityData.itemDef(stack));
        return info(ctx);
    }

    private static int upgrade(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ItemStack stack = held(ctx);
        if (!StatEngine.upgradeRarity(stack, ctx.getSource().getLevel().getRandom())) {
            ctx.getSource().sendFailure(Component.translatable("commands.itemsrarity.max_rarity"));
            return 0;
        }
        return info(ctx);
    }

    private static int setLine(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ItemStack stack = held(ctx);
        String stat = StringArgumentType.getString(ctx, "stat");
        if (RarityData.stat(stat) == null) throw UNKNOWN_STAT.create();
        StatEngine.ensureRolled(stack, ctx.getSource().getLevel().getRandom());
        ItemStats stats = stack.get(ModDataComponents.STATS);
        stack.set(ModDataComponents.STATS, stats.withLine(stat, IntegerArgumentType.getInteger(ctx, "value")));
        return info(ctx);
    }

    private static int exo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ItemStack stack = held(ctx);
        String stat = StringArgumentType.getString(ctx, "stat");
        if (RarityData.stat(stat) == null) throw UNKNOWN_STAT.create();
        StatEngine.ensureRolled(stack, ctx.getSource().getLevel().getRandom());
        int value = IntegerArgumentType.getInteger(ctx, "value");
        ItemStats stats = stack.get(ModDataComponents.STATS);
        stack.set(ModDataComponents.STATS, value == 0 ? stats.withExo("", 0) : stats.withExo(stat, value));
        return info(ctx);
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ItemStack stack = held(ctx);
        stack.remove(ModDataComponents.RARITY);
        stack.remove(ModDataComponents.STATS);
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.itemsrarity.removed"), false);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ItemStack stack = held(ctx);
        ItemDefinition def = RarityData.itemDef(stack);
        ItemStats stats = stack.get(ModDataComponents.STATS);
        ModRarity rarity = StatEngine.rarityOf(stack);

        MutableComponent text = Component.empty()
                .append(stack.getHoverName()).append(" · ")
                .append(Component.translatable("rarity.itemsrarity." + rarity.getId()).withStyle(rarity.getRarity().getStyleModifier()))
                .append(" · ").append(Component.translatable("tooltip.itemsrarity.tier", def.tier));
        if (stats == null) {
            text.append("\n  ").append(Component.translatable("commands.itemsrarity.not_rolled"));
        } else {
            stats.lines().forEach((statId, value) -> {
                StatDefinition stat = RarityData.stat(statId);
                int[] range = StatEngine.range(def, statId, stats.upgrades());
                text.append("\n  ").append(stat != null ? stat.formatValue(value, !stat.base) + " " : value + " ")
                        .append(stat != null ? stat.name() : Component.literal(statId));
                if (range != null) text.append(" (" + range[0] + " - " + range[1] + ")");
            });
            if (stats.hasExo()) {
                StatDefinition stat = RarityData.stat(stats.exoStat());
                text.append("\n  [exo] ").append(stat != null ? stat.formatValue(stats.exoValue(), true) + " " : "")
                        .append(stat != null ? stat.name() : Component.literal(stats.exoStat()));
            }
            text.append("\n  ").append(Component.translatable("commands.itemsrarity.quality",
                    Math.round(StatEngine.quality(stack) * 100)));
        }
        ctx.getSource().sendSuccess(() -> text, false);
        return 1;
    }
}
