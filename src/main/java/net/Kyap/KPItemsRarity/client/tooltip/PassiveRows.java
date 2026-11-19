package net.Kyap.KPItemsRarity.client.tooltip;

import net.Kyap.KPItemsRarity.client.tooltip.TooltipModel.Elem;
import net.Kyap.KPItemsRarity.client.tooltip.TooltipModel.GapElem;
import net.Kyap.KPItemsRarity.client.tooltip.TooltipModel.PassiveElem;
import net.Kyap.KPItemsRarity.client.tooltip.TooltipModel.Row;
import net.Kyap.KPItemsRarity.client.tooltip.TooltipModel.RowElem;
import net.Kyap.KPItemsRarity.data.ItemDefinition;
import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.data.RarityData;
import net.Kyap.KPItemsRarity.data.SetDefinition;
import net.Kyap.KPItemsRarity.passive.ActivePassives;
import net.Kyap.KPItemsRarity.passive.PassiveCooldowns;
import net.Kyap.KPItemsRarity.stats.Conditions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Les conditions d'équipement et les passifs (de l'objet puis de sa panoplie), avec leur état en direct. */
final class PassiveRows {
    private PassiveRows() {}

    /** "Condition : Armure totale ≤ 8", en vert si le joueur la remplit, en rouge sinon. */
    static void addConditions(List<Elem> body, @Nullable ItemDefinition def) {
        if (def == null || !def.hasConditions()) return;
        Player player = Minecraft.getInstance().player;
        body.add(new GapElem(3));
        def.conditions.forEach((key, limit) -> {
            boolean ok = player == null || Conditions.met(player, key, limit);
            String shown = limit == Math.rint(limit) ? String.valueOf(limit.longValue()) : String.valueOf(limit);
            Component line = Component.literal(ok ? "✔ " : "✖ ").append(Component.translatable("tooltip.kpitemsrarity.condition." + key, shown))
                    .withStyle(s -> s.withColor(ok ? RpgFrame.POSITIVE : RpgFrame.NEGATIVE));
            body.add(new RowElem(new Row(line, Component.empty(), 0)));
        });
    }

    /** Passifs de l'objet, puis ceux de sa panoplie (avec le nombre de pièces requis). */
    static void addPassives(List<Elem> body, @Nullable ItemDefinition def) {
        if (def == null) return;
        Player player = Minecraft.getInstance().player;
        Set<String> active = new HashSet<>();
        if (player != null) for (var p : ActivePassives.list(player)) active.add(p.id);

        List<PassiveElem> elems = new ArrayList<>();
        if (def.passives != null) for (String id : def.passives) addPassive(elems, id, player, active, null);
        SetDefinition set = def.hasSet() ? RarityData.set(def.set) : null;
        if (set != null && set.passives != null) {
            set.passives.forEach((pieces, ids) -> {
                for (String id : ids) addPassive(elems, id, player, active, pieces);
            });
        }
        if (elems.isEmpty()) return;
        body.add(new GapElem(3));
        body.addAll(elems);
    }

    private static void addPassive(List<PassiveElem> elems, String id, @Nullable Player player, Set<String> active, @Nullable String pieces) {
        PassiveDefinition p = RarityData.passive(id);
        if (p != null) elems.add(passiveElem(p, player, active.contains(id), pieces));
    }

    static PassiveElem passiveElem(PassiveDefinition p, @Nullable Player player,
                                           boolean on, @Nullable String pieces) {
        int color = on ? 0xF2C14E : 0x9A8A6A;
        MutableComponent name = Component.literal("✧ ").append(p.name()).withStyle(s -> s.withColor(color).withBold(true));
        if (pieces != null) name.append(Component.translatable("tooltip.kpitemsrarity.passive.pieces", pieces)
                .withStyle(s -> s.withColor(RpgFrame.TEXT_FAINT).withBold(false)));
        Component state;
        int left = PassiveCooldowns.clientSecondsLeft(p.id);
        if (!on) {
            state = Component.translatable("tooltip.kpitemsrarity.passive.inactive").withStyle(s -> s.withColor(RpgFrame.TEXT_FAINT));
        } else if (left > 0) {
            state = Component.translatable("tooltip.kpitemsrarity.passive.cooldown", left).withStyle(s -> s.withColor(0xF0A040));
        } else if (player != null && waiting(p, player)) {
            state = Component.translatable("tooltip.kpitemsrarity.passive.waiting").withStyle(s -> s.withColor(0xE8D25A));
        } else {
            state = Component.translatable("tooltip.kpitemsrarity.passive.active").withStyle(s -> s.withColor(RpgFrame.POSITIVE));
        }
        Component formula = null;
        if (Screen.hasShiftDown()) {
            StringBuilder sb = new StringBuilder(p.effect);
            p.params.entrySet().forEach(e -> sb.append(" · ").append(e.getKey()).append(' ').append(e.getValue().getAsString()));
            if (p.cooldown > 0) sb.append(" · cooldown ").append((int) p.cooldown).append(" s");
            formula = Component.literal(sb.toString());
        }
        return new PassiveElem(name, state, p.description(), formula, color, on);
    }

    /** Condition du passif pas remplie en ce moment (vie trop haute, hors de l'eau, sous la pluie...). */
    static boolean waiting(PassiveDefinition p, Player player) {
        return switch (p.effect) {
            case "crit_explosion" -> player.getHealth() > p.num("max_health", 2.0);
            case "life_steal" -> p.num("below_health", 0) > 0 && player.getHealth() > player.getMaxHealth() * p.num("below_health", 0)
                    && p.num("ratio", 0) <= 0;
            case "strength_in_water", "healing_in_water" -> !player.isInWater();
            case "dodge" -> p.flag("no_rain") && player.isInWaterOrRain();
            default -> false;
        };
    }
}
