package net.Kyap.KPItemsRarity.stats;

import net.Kyap.KPItemsRarity.compat.CuriosCompat;
import net.Kyap.KPItemsRarity.data.ItemDefinition;
import net.Kyap.KPItemsRarity.data.RarityData;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.neoforged.neoforge.common.ItemAbilities;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Où un objet compte : armure à sa place, bouclier en main gauche, accessoires dans les cases Curios, le reste en main droite. */
public final class GearSlots {
    private GearSlots() {}

    /** Emplacements comptés pour les stats et les panoplies (pas l'armure de cheval/loup). */
    public static final EquipmentSlot[] GEAR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };

    /** Emplacements d'accessoires (portés dans les cases Curios, jamais en main). */
    public static final Set<String> ACCESSORY_SLOTS = Set.of("amulet", "ring", "relic");

    /** Où l'objet donne ses stats : armure -> son emplacement, bouclier -> main gauche, le reste -> main droite. */
    public static EquipmentSlotGroup slotGroupFor(ItemStack stack) {
        if (stack.getItem() instanceof ArmorItem armor) {
            return EquipmentSlotGroup.bySlot(armor.getEquipmentSlot());
        }
        if (stack.getItem() instanceof ShieldItem || stack.canPerformAction(ItemAbilities.SHIELD_BLOCK)) {
            return EquipmentSlotGroup.OFFHAND;
        }
        return EquipmentSlotGroup.MAINHAND;
    }

    /** Amulette, anneau ou relique : ses stats ne comptent que dans une case d'accessoire. */
    public static boolean isAccessory(ItemStack stack) {
        ItemDefinition def = RarityData.itemDef(stack);
        return def != null && def.slot != null && ACCESSORY_SLOTS.contains(def.slot);
    }

    /** L'objet est-il porté au bon endroit pour compter ? */
    public static boolean isWornCorrectly(ItemStack stack, EquipmentSlot slot) {
        return !stack.isEmpty() && !isAccessory(stack) && slotGroupFor(stack).test(slot);
    }

    /** L'équipement qui compte (armure et mains), dans l'ordre des emplacements. */
    public static List<ItemStack> wornGear(LivingEntity entity) {
        List<ItemStack> worn = new ArrayList<>();
        for (EquipmentSlot slot : GEAR_SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (isWornCorrectly(stack, slot)) worn.add(stack);
        }
        return worn;
    }

    /** Les accessoires portés dans les cases Curios (amulette, anneaux, relique). */
    public static List<ItemStack> wornAccessories(LivingEntity entity) {
        List<ItemStack> worn = new ArrayList<>();
        for (ItemStack stack : CuriosCompat.equipped(entity)) if (isAccessory(stack)) worn.add(stack);
        return worn;
    }
}
