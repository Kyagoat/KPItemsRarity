package net.Kyap.KPItemsRarity.client.tooltip.item;

import net.Kyap.KPItemsRarity.client.tooltip.card.Elem;
import net.Kyap.KPItemsRarity.client.tooltip.card.Palette;
import net.Kyap.KPItemsRarity.data.ItemDefinition;
import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.data.RarityData;
import net.Kyap.KPItemsRarity.data.SetDefinition;
import net.Kyap.KPItemsRarity.passive.ActivePassives;
import net.Kyap.KPItemsRarity.passive.PassiveCooldowns;
import net.Kyap.KPItemsRarity.passive.PassiveEffect;
import net.Kyap.KPItemsRarity.passive.PassiveEffects;
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

/**
 * Les conditions d'équipement ("✔ Armure totale ≤ 8") et les passifs de l'objet puis de sa panoplie, chacun dans
 * son encadré avec son état en direct : actif, en recharge, en attente (condition pas remplie), inactif.
 */
final class PassiveRows {
    private PassiveRows() {}

    /** Couleurs du nom d'un passif (actif, pas porté) et de son état (en recharge, en attente). */
    private static final int ACTIVE = 0xF2C14E, INACTIVE = 0x9A8A6A, COOLDOWN = 0xF0A040, WAITING = 0xE8D25A;

    /** "✔ Armure totale ≤ 8" en vert si le joueur remplit la condition, "✖ ..." en rouge sinon. */
    static void addConditions(List<Elem> body, @Nullable ItemDefinition def) {
        if (def == null || !def.hasConditions()) return;
        Player player = Minecraft.getInstance().player;
        body.add(Elem.Gap.SECTION);
        def.conditions.forEach((condition, limit) -> {
            boolean met = player == null || Conditions.met(player, condition, limit);
            Component line = Component.literal(met ? "✔ " : "✖ ")
                    .append(Conditions.text(condition, limit))
                    .withStyle(style -> style.withColor(met ? Palette.POSITIVE : Palette.NEGATIVE));
            body.add(new Elem.Line(line));
        });
    }

    /** Passifs de l'objet, puis ceux de sa panoplie (avec le nombre de pièces qu'il faut porter). */
    static void addPassives(List<Elem> body, @Nullable ItemDefinition def) {
        if (def == null) return;
        Player player = Minecraft.getInstance().player;
        Set<String> active = activeIds(player);
        List<Elem> boxes = new ArrayList<>();
        for (String id : def.passives) addBox(boxes, id, null, player, active);
        SetDefinition set = def.hasSet() ? RarityData.set(def.set) : null;
        if (set != null) set.passiveTiers().forEach((pieces, ids) -> ids.forEach(id -> addBox(boxes, id, pieces, player, active)));
        if (boxes.isEmpty()) return;
        body.add(Elem.Gap.SECTION);
        body.addAll(boxes);
    }

    private static Set<String> activeIds(@Nullable Player player) {
        Set<String> ids = new HashSet<>();
        if (player != null) ActivePassives.list(player).forEach(passive -> ids.add(passive.id));
        return ids;
    }

    /** @param pieces pièces de panoplie qu'il faut porter, ou null pour un passif de l'objet */
    private static void addBox(List<Elem> boxes, String id, @Nullable Integer pieces, @Nullable Player player, Set<String> active) {
        PassiveDefinition passive = RarityData.passive(id);
        if (passive == null) return;
        boolean on = active.contains(id);
        int color = on ? ACTIVE : INACTIVE;
        MutableComponent name = Component.literal("✧ ").append(passive.name()).withStyle(style -> style.withColor(color).withBold(true));
        if (pieces != null) name.append(Component.translatable("tooltip.kpitemsrarity.passive.pieces", pieces)
                .withStyle(style -> style.withColor(Palette.TEXT_FAINT).withBold(false)));
        Component formula = Screen.hasShiftDown() ? formula(passive) : null;
        boxes.add(new Elem.Passive(name, state(passive, on, player), passive.description(), formula, color, on));
    }

    /** Le voyant à droite du nom : inactif (pas porté), en recharge, en attente de sa condition, ou actif. */
    private static Component state(PassiveDefinition passive, boolean on, @Nullable Player player) {
        int secondsLeft = PassiveCooldowns.clientSecondsLeft(passive.id);
        if (!on) return Component.translatable("tooltip.kpitemsrarity.passive.inactive").withStyle(style -> style.withColor(Palette.TEXT_FAINT));
        if (secondsLeft > 0) return Component.translatable("tooltip.kpitemsrarity.passive.cooldown", secondsLeft).withStyle(style -> style.withColor(COOLDOWN));
        if (player != null && isWaiting(passive, player)) {
            return Component.translatable("tooltip.kpitemsrarity.passive.waiting").withStyle(style -> style.withColor(WAITING));
        }
        return Component.translatable("tooltip.kpitemsrarity.passive.active").withStyle(style -> style.withColor(Palette.POSITIVE));
    }

    private static boolean isWaiting(PassiveDefinition passive, Player player) {
        PassiveEffect effect = PassiveEffects.get(passive.effect);
        return effect != null && effect.isWaiting(player, passive);
    }

    /** Maj : les réglages bruts du passif, "life_steal · ratio 0.2 · cooldown 10 s" (et ses autres effets après un "+"). */
    private static Component formula(PassiveDefinition passive) {
        StringBuilder text = new StringBuilder();
        appendEffect(text, passive);
        for (PassiveDefinition part : passive.and) appendEffect(text.append(" + "), part);
        if (passive.cooldown > 0) text.append(" · cooldown ").append((int) passive.cooldown).append(" s");
        return Component.literal(text.toString());
    }

    private static void appendEffect(StringBuilder text, PassiveDefinition effect) {
        text.append(effect.effect);
        effect.params.entrySet().forEach(param -> text.append(" · ").append(param.getKey()).append(' ').append(param.getValue().getAsString()));
    }
}
