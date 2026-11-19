package net.Kyap.KPItemsRarity.client.tooltip;

import net.Kyap.KPItemsRarity.component.ItemStats;
import net.Kyap.KPItemsRarity.data.ItemDefinition;
import net.Kyap.KPItemsRarity.data.RarityData;
import net.Kyap.KPItemsRarity.data.RollRange;
import net.Kyap.KPItemsRarity.data.StatDefinition;
import net.Kyap.KPItemsRarity.stats.ItemRolls;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.jetbrains.annotations.Nullable;

/** "🛡 5" ou "⚔ 6 (5 - 7)  1.6 att/s" sur une seule ligne, sous le nom de l'objet. */
record BaseValue(Component label, String value, String range, boolean armor) {

    /** La valeur de base de l'objet (armure ou dégâts), ou null s'il n'en a pas. */
    @Nullable
    static BaseValue of(ItemStack stack, @Nullable ItemDefinition def, @Nullable ItemStats stats) {
        ItemAttributeModifiers mods = stack.getAttributeModifiers();
        if (stack.getItem() instanceof ArmorItem) {
            double armor = VanillaRows.sum(mods, Attributes.ARMOR, AttributeModifier.Operation.ADD_VALUE);
            if (armor > 0) return new BaseValue(Component.empty(), VanillaRows.fmt(armor), "", true); // l'icône d'armure suffit
        }
        BaseValue damage = modDamage(def, stats, attackSpeed(mods));
        if (damage != null) return damage;
        double vanilla = VanillaRows.sum(mods, Attributes.ATTACK_DAMAGE, AttributeModifier.Operation.ADD_VALUE);
        return vanilla > 0 ? new BaseValue(attackSpeed(mods), VanillaRows.fmt(1.0 + vanilla), "", false) : null;
    }

    /**
     * La ligne de base "Dégâts" du mod : le jet et sa fourchette, ou seulement la fourchette pour un objet
     * pas encore tiré. Null si l'objet n'en a pas.
     */
    @Nullable
    private static BaseValue modDamage(@Nullable ItemDefinition def, @Nullable ItemStats stats, Component speed) {
        if (def == null) return null;
        for (String statId : def.stats.keySet()) {
            StatDefinition sd = RarityData.stat(statId);
            if (sd == null || !sd.base) continue;
            if (stats != null) {
                if (!stats.lines().containsKey(statId)) continue;
                RollRange r = ItemRolls.range(def, statId, stats.upgrades());
                String range = r != null ? "(" + r.format(sd) + ")" : "";
                return new BaseValue(speed, String.valueOf(stats.get(statId)), range, false);
            }
            RollRange r = def.range(statId);
            if (r != null) return new BaseValue(speed, r.format(sd), "", false);
        }
        return null;
    }

    /** L'icône ⚔ suffit pour "Dégâts" : on n'affiche que la vitesse d'attaque à côté (vide si l'objet n'en change pas). */
    private static Component attackSpeed(ItemAttributeModifiers mods) {
        double speed = VanillaRows.sum(mods, Attributes.ATTACK_SPEED, AttributeModifier.Operation.ADD_VALUE);
        return speed != 0 ? Component.translatable("tooltip.kpitemsrarity.rpg.speed", VanillaRows.fmt(4.0 + speed)) : Component.empty();
    }

    int width(Font font) {
        return Math.max(12 + (int) (font.width(value) * 1.5f) + 4 + (range.isEmpty() ? 0 : font.width(range)), 12 + font.width(label));
    }

    /** Hauteur : la valeur, plus la vitesse d'attaque en dessous s'il y en a une. */
    int height() {
        return label.getString().isEmpty() ? 14 : 23;
    }

    void draw(GuiGraphics g, Font font, int x, int y) {
        if (armor) {
            g.blitSprite(ResourceLocation.withDefaultNamespace("hud/armor_full"), x, y + 2, 9, 9);
        } else {
            g.drawString(font, "⚔", x, y + 2, RpgFrame.TEXT_SOFT, true);
        }
        int vx = x + 12;
        g.pose().pushPose();
        g.pose().translate(vx, y, 0);
        g.pose().scale(1.5f, 1.5f, 1.0f);
        g.drawString(font, value, 0, 0, RpgFrame.TEXT, true);
        g.pose().popPose();
        int tx = vx + (int) (font.width(value) * 1.5f) + 4;
        if (!range.isEmpty()) g.drawString(font, range, tx, y + 4, RpgFrame.TEXT_FAINT, true);
        if (!label.getString().isEmpty()) g.drawString(font, label, vx, y + 14, RpgFrame.TEXT_DIM, true);
    }
}
