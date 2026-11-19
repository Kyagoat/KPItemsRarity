package net.Kyap.KPItemsRarity.registry;

import net.Kyap.KPItemsRarity.KPItemsRarity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Attributs de combat du mod (plus besoin d'Apothic Attributes).
 * Leurs effets sont appliqués dans events/CombatEvents.
 */
@EventBusSubscriber(modid = KPItemsRarity.MOD_ID)
public final class ModAttributes {
    private ModAttributes() {}

    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, KPItemsRarity.MOD_ID);

    /** Chance de coup critique (0.05 = 5 % de base). */
    public static final DeferredHolder<Attribute, Attribute> CRIT_CHANCE = register("crit_chance", 0.05, 0.0, 1.0);
    /** Multiplicateur des dégâts d'un coup critique (1.5 = 150 % de base). */
    public static final DeferredHolder<Attribute, Attribute> CRIT_DAMAGE = register("crit_damage", 1.5, 1.0, 10.0);
    /** Part des dégâts infligés rendue en vie. */
    public static final DeferredHolder<Attribute, Attribute> LIFE_STEAL = register("life_steal", 0.0, 0.0, 1.0);
    /** Chance d'esquiver complètement une attaque (plafonnée à 60 %). */
    public static final DeferredHolder<Attribute, Attribute> DODGE_CHANCE = register("dodge_chance", 0.0, 0.0, 0.6);
    /** Points d'armure de la cible ignorés. */
    public static final DeferredHolder<Attribute, Attribute> ARMOR_PIERCE = register("armor_pierce", 0.0, 0.0, 1024.0);
    /** Multiplicateur des dégâts des flèches tirées. */
    public static final DeferredHolder<Attribute, Attribute> ARROW_DAMAGE = register("arrow_damage", 1.0, 0.0, 10.0);
    /** Vitesse pour bander un arc / charger une arbalète. */
    public static final DeferredHolder<Attribute, Attribute> DRAW_SPEED = register("draw_speed", 1.0, 0.1, 4.0);
    /** Multiplicateur d'expérience gagnée (Sagesse). */
    public static final DeferredHolder<Attribute, Attribute> EXPERIENCE_GAINED = register("experience_gained", 1.0, 0.0, 10.0);

    /** Multiplicateur des soins reçus (régénération, potions, vol de vie...). */
    public static final DeferredHolder<Attribute, Attribute> HEALING_RECEIVED = register("healing_received", 1.0, 0.0, 10.0);
    /** Part des dégâts de mêlée subis renvoyée à l'attaquant. */
    public static final DeferredHolder<Attribute, Attribute> THORNS = register("thorns", 0.0, 0.0, 1.0);

    private static DeferredHolder<Attribute, Attribute> register(String name, double base, double min, double max) {
        return ATTRIBUTES.register(name, () -> new RangedAttribute("attribute.kpitemsrarity." + name, base, min, max).setSyncable(true));
    }

    public static void register(IEventBus bus) {
        ATTRIBUTES.register(bus);
    }

    @SubscribeEvent
    public static void addToPlayer(EntityAttributeModificationEvent event) {
        for (var holder : ATTRIBUTES.getEntries()) {
            event.add(EntityType.PLAYER, holder);
        }
    }
}
