package net.Kyap.KPItemsRarity.passive.effect;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/** Les familles de créatures des passifs : un tag de types d'entités ("minecraft:undead", "minecraft:illager"...). */
final class Families {
    private Families() {}

    static boolean is(Entity entity, String tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag);
        if (id == null) return false;
        TagKey<EntityType<?>> key = TagKey.create(Registries.ENTITY_TYPE, id);
        return entity.getType().is(key);
    }
}
