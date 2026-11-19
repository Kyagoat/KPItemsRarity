package net.Kyap.KPItemsRarity.mixin;

import net.Kyap.KPItemsRarity.component.ModDataComponents;
import net.Kyap.KPItemsRarity.util.ModRarities.ModRarity;
import net.Kyap.KPItemsRarity.util.RarityOverrides;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * La rareté du mod remplace la rareté vanilla (couleur du nom, styles Obscure Tooltips).
 * On n'utilise pas le composant vanilla minecraft:rarity car le jeu "monte" la rareté des objets
 * enchantés (commun -> rare), ce qui fausserait l'affichage.
 * Appelé très souvent (à chaque rendu de tooltip) : pas de log ici !
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Inject(method = "getRarity", at = @At("HEAD"), cancellable = true)
    private void kpitemsrarity$getRarity(CallbackInfoReturnable<Rarity> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        ModRarity rarity = stack.get(ModDataComponents.RARITY);
        // Sans jets : rareté choisie par les fichiers kp_rarity (matériaux, objets des autres mods)
        if (rarity == null) rarity = RarityOverrides.get(stack);
        if (rarity != null) {
            cir.setReturnValue(rarity.getRarity());
        }
    }
}
