package net.Kyap.ItemsRarity.compat;

import net.Kyap.ItemsRarity.ItemsRarity;
import net.Kyap.ItemsRarity.events.AttributeEvents;
import net.Kyap.ItemsRarity.util.StatEngine;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;

import java.lang.reflect.Method;

/**
 * Accessoires (amulette, anneaux, relique) portés dans les cases Curios : leurs jets deviennent
 * de vrais attributs, comme une pièce d'armure. Curios n'est PAS une dépendance de compilation :
 * on s'abonne à son événement par réflexion, seulement s'il est installé.
 */
public final class CuriosCompat {
    private CuriosCompat() {}

    private static final String EVENT = "top.theillusivec4.curios.api.event.CurioAttributeModifierEvent";
    private static final String CHANGE_EVENT = "top.theillusivec4.curios.api.event.CurioChangeEvent";
    private static Method getItemStack, getId, addModifier, getCuriosInventory, getEquippedCurios;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void init() {
        if (!ModList.get().isLoaded("curios")) return;
        try {
            Class type = Class.forName(EVENT);
            getItemStack = type.getMethod("getItemStack");
            getId = type.getMethod("getId");
            addModifier = type.getMethod("addModifier", Holder.class, AttributeModifier.class);
            NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, (Class<Event>) type, CuriosCompat::onCurioAttributes);
            // Parures : les accessoires portés comptent comme pièces de panoplie
            getCuriosInventory = Class.forName("top.theillusivec4.curios.api.CuriosApi")
                    .getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class);
            getEquippedCurios = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler")
                    .getMethod("getEquippedCurios");
            Class change = Class.forName(CHANGE_EVENT);
            NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, (Class<Event>) change, e ->
                    net.Kyap.ItemsRarity.events.SetBonusEvents.refresh(
                            ((net.neoforged.neoforge.event.entity.living.LivingEvent) e).getEntity()));
            ItemsRarity.LOGGER.info("Items Rarity : accessoires Curios activés");
        } catch (ReflectiveOperationException | LinkageError e) {
            ItemsRarity.LOGGER.warn("Items Rarity : Curios trouvé mais son API a changé, accessoires sans stats ({})", e.toString());
        }
    }

    /** Accessoires portés dans les cases Curios (vide si Curios est absent). */
    public static java.util.List<ItemStack> equipped(net.minecraft.world.entity.LivingEntity entity) {
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        if (getCuriosInventory == null) return out;
        try {
            var inv = (java.util.Optional<?>) getCuriosInventory.invoke(null, entity);
            if (inv.isEmpty()) return out;
            var handler = (net.neoforged.neoforge.items.IItemHandler) getEquippedCurios.invoke(inv.get());
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (!stack.isEmpty()) out.add(stack);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return out;
    }

    private static void onCurioAttributes(Event event) {
        try {
            ItemStack stack = (ItemStack) getItemStack.invoke(event);
            if (stack.isEmpty() || !StatEngine.isAccessory(stack)) return;
            ResourceLocation id = (ResourceLocation) getId.invoke(event);
            // Une clé par case : deux anneaux identiques ne se remplacent pas
            String slotKey = "curio_" + id.getPath().replaceAll("[^a-z0-9_./-]", "_");
            AttributeEvents.forEachStatModifier(stack, slotKey, (Holder<Attribute> attribute, AttributeModifier modifier) -> {
                try {
                    addModifier.invoke(event, attribute, modifier);
                } catch (ReflectiveOperationException ignored) {
                }
            });
        } catch (ReflectiveOperationException | ClassCastException e) {
            ItemsRarity.LOGGER.debug("Curios : modificateurs ignorés ({})", e.toString());
        }
    }
}
