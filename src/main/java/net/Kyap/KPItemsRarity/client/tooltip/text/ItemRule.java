package net.Kyap.KPItemsRarity.client.tooltip.text;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * "Si l'objet est ceci, c'est un {@code key}". Une liste de règles se lit de haut en bas et la première qui
 * correspond gagne : on met donc les plus précises en premier (ItemCategories, ItemTypes).
 */
record ItemRule(String key, Predicate<ItemStack> matches) {

    /** La clé de la première règle qui correspond, ou null si aucune. */
    @Nullable
    static String first(List<ItemRule> rules, ItemStack stack) {
        for (ItemRule rule : rules) {
            if (rule.matches.test(stack)) return rule.key;
        }
        return null;
    }

    /** L'objet est d'une de ces classes (ou d'une sous-classe : l'épée d'un autre mod reste une épée). */
    @SafeVarargs
    static Predicate<ItemStack> isA(Class<? extends Item>... types) {
        return stack -> {
            for (Class<? extends Item> type : types) {
                if (type.isInstance(stack.getItem())) return true;
            }
            return false;
        };
    }

    /** L'objet porte un de ces tags. */
    @SafeVarargs
    static Predicate<ItemStack> tagged(TagKey<Item>... tags) {
        return stack -> {
            for (TagKey<Item> tag : tags) {
                if (stack.is(tag)) return true;
            }
            return false;
        };
    }

    /** L'objet a ce composant (nourriture, disque, effets de ragoût...). */
    static Predicate<ItemStack> has(DataComponentType<?> component) {
        return stack -> stack.has(component);
    }
}
