package net.Kyap.KPItemsRarity.passive;

import net.Kyap.KPItemsRarity.data.ItemDefinition;
import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.Kyap.KPItemsRarity.data.RarityData;
import net.Kyap.KPItemsRarity.data.SetDefinition;
import net.Kyap.KPItemsRarity.stats.Conditions;
import net.Kyap.KPItemsRarity.stats.EquippedStats;
import net.Kyap.KPItemsRarity.stats.GearSlots;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

/**
 * Les passifs actifs d'une créature à un instant : objets portés (armure, mains, accessoires Curios) et
 * panoplies au bon palier. Calculé une fois par événement, puis parcouru par effet.
 */
public final class ActivePassives {
    private static final ActivePassives NONE = new ActivePassives(List.of());

    private final List<PassiveDefinition> passives;

    private ActivePassives(List<PassiveDefinition> passives) {
        this.passives = passives;
    }

    /** Les passifs d'un joueur (les autres créatures n'en ont pas). */
    public static ActivePassives of(LivingEntity entity) {
        return entity instanceof Player ? new ActivePassives(list(entity)) : NONE;
    }

    /** Les passifs actifs, dans l'ordre des objets puis des panoplies (sans doublon). */
    public static List<PassiveDefinition> list(LivingEntity entity) {
        Set<String> ids = new LinkedHashSet<>();
        List<ItemStack> worn = new ArrayList<>(GearSlots.wornGear(entity));
        worn.addAll(GearSlots.wornAccessories(entity));
        for (ItemStack stack : worn) {
            ItemDefinition def = RarityData.itemDef(stack);
            if (def != null && def.passives != null && Conditions.met(entity, def)) ids.addAll(def.passives);
        }
        EquippedStats.countSetPieces(entity).forEach((setId, count) -> {
            SetDefinition set = RarityData.set(setId);
            if (set != null) ids.addAll(set.activePassives(count));
        });
        List<PassiveDefinition> out = new ArrayList<>();
        for (String id : ids) {
            PassiveDefinition p = RarityData.passive(id);
            if (p != null) out.add(p);
        }
        return out;
    }

    public boolean isEmpty() {
        return passives.isEmpty();
    }

    /** Chaque passif dans l'ordre où il est porté. */
    void forEachInOrder(BiConsumer<PassiveEffect, PassiveDefinition> action) {
        for (PassiveDefinition p : passives) {
            PassiveEffect effect = PassiveEffects.get(p.effect);
            if (effect != null) action.accept(effect, p);
        }
    }

    /** Effet par effet (ordre de PassiveEffects), puis dans l'ordre où les passifs sont portés. */
    void forEachByEffect(BiConsumer<PassiveEffect, PassiveDefinition> action) {
        anyByEffect((effect, p) -> {
            action.accept(effect, p);
            return false;
        });
    }

    /** Comme forEachByEffect, mais s'arrête au premier passif qui renvoie true. */
    boolean anyByEffect(BiPredicate<PassiveEffect, PassiveDefinition> action) {
        if (passives.isEmpty()) return false;
        for (PassiveEffect effect : PassiveEffects.inOrder()) {
            for (PassiveDefinition p : passives) {
                if (effect.id().equals(p.effect) && action.test(effect, p)) return true;
            }
        }
        return false;
    }
}
