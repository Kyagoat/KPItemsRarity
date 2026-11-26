package net.Kyap.KPItemsRarity.passive.effect;

import net.Kyap.KPItemsRarity.data.PassiveDefinition;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import org.jetbrains.annotations.Nullable;

/**
 * L'effet de potion d'un passif, décrit par ses réglages : "effect" (minecraft:slowness), "seconds", "amplifier"
 * (0 = niveau I).
 */
final class Effects {
    private Effects() {}

    /** L'effet à donner, ou null si son identifiant n'existe pas. */
    @Nullable
    static MobEffectInstance of(PassiveDefinition p) {
        ResourceLocation id = ResourceLocation.tryParse(p.str("effect", ""));
        Holder<MobEffect> effect = id == null ? null : BuiltInRegistries.MOB_EFFECT.getHolder(id).orElse(null);
        if (effect == null) return null;
        int ticks = (int) Math.round(p.num("seconds", 3) * 20);
        return new MobEffectInstance(effect, ticks, (int) p.num("amplifier", 0));
    }
}
