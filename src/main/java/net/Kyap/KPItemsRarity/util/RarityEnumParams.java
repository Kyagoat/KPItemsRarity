package net.Kyap.KPItemsRarity.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.function.UnaryOperator;

/**
 * Paramètres des raretés ajoutées à l'enum vanilla {@code Rarity} (voir META-INF/enumextensions.json).
 * IMPORTANT : cette classe ne doit jamais référencer {@code Rarity}, sinon chargement circulaire de l'enum.
 * Paramètres : 0 = id (-1, attribué automatiquement), 1 = nom préfixé par le modid, 2 = style du nom.
 */
@SuppressWarnings("unused") // Utilisé par enumextensions.json
public final class RarityEnumParams {
    private RarityEnumParams() {}

    public static Object legendary(int idx, Class<?> type) {
        return type.cast(switch (idx) {
            case 0 -> -1;
            case 1 -> "kpitemsrarity:legendary";
            case 2 -> (UnaryOperator<Style>) style -> style.withColor(ChatFormatting.GOLD);
            default -> throw new IllegalArgumentException("Unexpected parameter index: " + idx);
        });
    }

    public static Object mythic(int idx, Class<?> type) {
        return type.cast(switch (idx) {
            case 0 -> -1;
            case 1 -> "kpitemsrarity:mythic";
            case 2 -> (UnaryOperator<Style>) style -> style.withColor(TextColor.fromRgb(0xFF3B3B));
            default -> throw new IllegalArgumentException("Unexpected parameter index: " + idx);
        });
    }
}
