package net.Kyap.KPItemsRarity.client.tooltip;

import net.Kyap.KPItemsRarity.ClientConfig;
import net.Kyap.KPItemsRarity.KPItemsRarity;
import net.Kyap.KPItemsRarity.client.ShareKey;
import net.Kyap.KPItemsRarity.client.tooltip.TooltipModel.Elem;
import net.Kyap.KPItemsRarity.component.ItemStats;
import net.Kyap.KPItemsRarity.component.ModDataComponents;
import net.Kyap.KPItemsRarity.data.ItemDefinition;
import net.Kyap.KPItemsRarity.data.RarityData;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Infobulle RPG des objets :
 *
 *  ┌──────────────────────────────────────────┐
 *  │ ÉPÉE EN NETHERITE              ✦ Épique   │  nom à la couleur de la rareté
 *  │ Épée · Palier 4                  ╔════╗   │
 *  │ Dégâts · 1.6 att/s               ║ 🗡 ║   │  aperçu de l'objet en grand
 *  │ ⚔ 9  (7 - 9)                     ╚════╝   │
 *  ├────────────────────◆─────────────────────┤
 *  │ +11 % Force                     (7 - 11)  │
 *  │ +5 % Critique                    (3 - 5)  │
 *  │ Enchantements ────────────────────────    │
 *  │ ◆ Tranchant V                             │
 *  │ Panoplie ─────────────────────────────    │
 *  │ Panoplie en bois 2/6     Maintiens [Alt]  │
 *  │            Durabilité : 1800 / 2031       │
 *  └──────────────────────────────────────────┘
 * Le cadre prend la couleur de la rareté (et pulse en Légendaire / Mythique).
 *
 * Le contenu vient de {@link TooltipContent}, les mesures de {@link TooltipLayout} ; cette classe dessine.
 */
public final class RpgTooltip {
    private RpgTooltip() {}

    private static boolean warned;

    /**
     * Appelé par GuiGraphicsTooltipMixin pour TOUTE infobulle d'objet.
     * @param images composants non textuels de l'infobulle (cases d'appât de Tide, cartes...)
     * @return true si l'infobulle normale ne doit pas être dessinée.
     */
    public static boolean handle(GuiGraphics g, Font font, List<Component> lines, List<ClientTooltipComponent> images,
                                 ItemStack stack, int mouseX, int mouseY) {
        // Carte de panoplie figée (Alt maintenu) : aucune autre infobulle par-dessus.
        if (SetCard.isOpen()) return true;
        if (stack == null || stack.isEmpty()) return false;
        if (ShareKey.isSetDetailsDown() && SetCard.open(stack, mouseX, mouseY)) return true;
        if (!ClientConfig.rpgTooltips()) return false;
        ItemDefinition def = RarityData.itemDef(stack);
        ItemStats stats = stack.get(ModDataComponents.STATS);

        g.pose().pushPose();
        try {
            TooltipContent content = TooltipContent.of(stack, def, stats, lines, images, font);
            int icon = ClientConfig.iconSize(); // même taille pour tous les objets
            TooltipLayout layout = new TooltipLayout(content, font, icon, g.guiWidth(), g.guiHeight(), mouseX, mouseY);
            draw(g, font, content, layout, icon);
            return true;
        } catch (RuntimeException e) {
            if (!warned) {
                warned = true;
                KPItemsRarity.LOGGER.error("Infobulle RPG impossible pour {}, retour à l'infobulle classique", stack, e);
            }
            return false;
        } finally {
            g.pose().popPose();
        }
    }

    private static void draw(GuiGraphics g, Font font, TooltipContent content, TooltipLayout layout, int icon) {
        g.pose().translate(0, 0, 400);
        RpgFrame.draw(g, layout.x, layout.y, layout.width, layout.height, layout.headerH, content.accent,
                RpgFrame.pulses(content.rarity), RpgFrame.ornateLevel(content.rarity));
        drawHeader(g, font, content, layout);
        drawIcon(g, content, layout, icon);
        g.pose().pushPose();
        g.pose().translate(0, 0, 200); // au-dessus des objets dessinés
        if (layout.bodyH > 0) drawBody(g, font, content, layout);
        if (content.durability != null) drawDurability(g, font, content, layout);
        g.pose().popPose();
    }

    private static void drawHeader(GuiGraphics g, Font font, TooltipContent content, TooltipLayout layout) {
        int left = layout.left(), right = layout.right();
        int cy = layout.y + TooltipLayout.PAD;
        ScrollingName.draw(g, font, content.name, content.stack, left, cy, layout.inner - font.width(content.rarityLabel) - 6);
        g.drawString(font, content.rarityLabel, right - font.width(content.rarityLabel), cy - 2, 0xFFFFFF, true);
        cy += 11;
        g.drawString(font, content.subtitle, left, cy, RpgFrame.TEXT_DIM, true);
        cy += 10;
        if (content.base != null) content.base.draw(g, font, left, cy + 3);
    }

    /** Aperçu de l'objet sur un petit socle ; il lévite et se retourne toutes les 6 s comme une pièce qu'on fait tourner. */
    private static void drawIcon(GuiGraphics g, TooltipContent content, TooltipLayout layout, int icon) {
        int ix = layout.right() - icon, iy = layout.y + layout.iconTop;
        int accent = content.accent;
        g.fill(ix - 3, iy - 3, ix + icon + 3, iy + icon + 3, RpgFrame.argb(accent, 0x22));
        g.fill(ix - 2, iy + icon + 2, ix + icon + 2, iy + icon + 3, RpgFrame.argb(accent, 0x70));
        float flip = 1f, bob = 0f;
        if (ClientConfig.animatedIcon()) {
            long ms = Util.getMillis();
            bob = (float) Math.sin(ms / 650.0) * 1.0f;
            float t = (ms % 6000L) / 900f;
            if (t < 1f) {
                float u = t * t * (3f - 2f * t);
                flip = Math.max(0.06f, Math.abs((float) Math.cos(u * Math.PI * 2)));
            }
        }
        float scale = icon / 16f;
        g.pose().pushPose();
        g.pose().translate(ix + icon / 2f, iy + icon / 2f + bob, 0);
        g.pose().scale(scale * flip, scale, 1.0f);
        g.renderFakeItem(content.stack, -8, -8);
        g.pose().popPose();
    }

    /** Le corps, découpé à sa zone visible quand il défile, puis le texte du bas. */
    private static void drawBody(GuiGraphics g, Font font, TooltipContent content, TooltipLayout layout) {
        TooltipScroll.Viewport viewport = layout.viewport;
        int bodyTop = layout.bodyTop();
        if (viewport.scrolling()) g.enableScissor(layout.x + 1, bodyTop - 1, layout.x + layout.width - 1, bodyTop + viewport.view() + 1);
        TooltipModel.Area area = layout.bodyArea();
        int cy = bodyTop - viewport.offset();
        for (Elem e : content.body) {
            e.draw(g, font, area, cy);
            cy += e.height(font, area.wrapWidth());
        }
        if (!layout.footerLines.isEmpty()) {
            if (layout.bodyH > layout.footerLines.size() * 10) {
                g.fill(layout.left(), cy + 1, layout.right(), cy + 2, RpgFrame.argb(RpgFrame.TEXT_FAINT, 0x60));
                cy += 6;
            }
            for (FormattedCharSequence line : layout.footerLines) {
                g.drawString(font, line, layout.left(), cy, RpgFrame.TEXT_DIM, true);
                cy += 10;
            }
        }
        if (viewport.scrolling()) {
            g.disableScissor();
            RpgFrame.scrollChrome(g, font, layout.x, layout.width, layout.right(), bodyTop, viewport.view(), layout.bodyH,
                    viewport.offset(), viewport.max(), content.accent);
        }
    }

    private static void drawDurability(GuiGraphics g, Font font, TooltipContent content, TooltipLayout layout) {
        int fy = layout.y + layout.height - layout.footerH - 2;
        g.fill(layout.left(), fy, layout.right(), fy + 1, RpgFrame.argb(content.accent, 0x40));
        g.drawString(font, content.durability, layout.x + (layout.width - font.width(content.durability)) / 2, fy + 3, RpgFrame.DURABILITY, true);
    }
}
