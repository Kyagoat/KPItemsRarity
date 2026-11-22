package net.Kyap.KPItemsRarity.rarity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.function.UnaryOperator;

/**
 * Les raretés Légendaire et Mythique ajoutées à l'énumération vanilla {@code Rarity}, pour la couleur du nom des
 * objets (déclarées dans META-INF/enumextensions.json, qui appelle ces méthodes au chargement du jeu).
 * Paramètres du constructeur vanilla : 0 = numéro (-1 : attribué par NeoForge), 1 = nom, 2 = style du nom.
 *
 * IMPORTANT : cette classe ne doit jamais utiliser {@code Rarity}, sinon l'énumération se chargerait elle-même
 * pendant son extension.
 */
@SuppressWarnings("unused") // appelée par enumextensions.json
public final class RarityEnumParams {
    private RarityEnumParams() {}

    /** Noms des valeurs ajoutées, tels qu'écrits dans enumextensions.json. */
    static final String LEGENDARY = "KPITEMSRARITY_LEGENDARY", MYTHIC = "KPITEMSRARITY_MYTHIC";

    public static Object legendary(int index, Class<?> type) {
        return parameter(index, type, "kpitemsrarity:legendary", style -> style.withColor(ChatFormatting.GOLD));
    }

    public static Object mythic(int index, Class<?> type) {
        return parameter(index, type, "kpitemsrarity:mythic", style -> style.withColor(TextColor.fromRgb(0xFF3B3B)));
    }

    private static Object parameter(int index, Class<?> type, String name, UnaryOperator<Style> nameStyle) {
        return type.cast(switch (index) {
            case 0 -> -1;
            case 1 -> name;
            case 2 -> nameStyle;
            default -> throw new IllegalArgumentException("Paramètre inattendu : " + index);
        });
    }
}
