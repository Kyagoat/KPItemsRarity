package net.Kyap.KPItemsRarity.mixin;

import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Texte d'une ligne d'infobulle déjà prête à dessiner (pour la relire dans l'infobulle RPG). */
@Mixin(ClientTextTooltip.class)
public interface ClientTextTooltipAccessor {
    @Accessor("text")
    FormattedCharSequence kpitemsrarity$getText();
}
