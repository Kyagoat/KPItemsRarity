package net.Kyap.KPItemsRarity.client.tooltip.item;

import net.Kyap.KPItemsRarity.ClientConfig;
import net.Kyap.KPItemsRarity.client.tooltip.card.Card;
import net.Kyap.KPItemsRarity.client.tooltip.card.RpgFrame;
import net.Kyap.KPItemsRarity.client.tooltip.text.ItemTypes;
import net.Kyap.KPItemsRarity.client.tooltip.text.Texts;
import net.Kyap.KPItemsRarity.component.ItemStats;
import net.Kyap.KPItemsRarity.data.ItemDefinition;
import net.Kyap.KPItemsRarity.data.RarityData;
import net.Kyap.KPItemsRarity.data.RollRange;
import net.Kyap.KPItemsRarity.data.StatDefinition;
import net.Kyap.KPItemsRarity.stats.ItemRolls;
import net.Kyap.KPItemsRarity.util.ModRarities.ModRarity;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.jetbrains.annotations.Nullable;

/**
 * L'en-tête de l'infobulle d'un objet :
 *
 *   ÉPÉE EN NETHERITE           ✦ Épique     nom (défile s'il est trop long), rareté
 *   Épée · Palier 4               ╔════╗     type, palier
 *   ⚔ 9  (7 - 9)                  ║ 🗡 ║     valeur de base en grand (dégâts ou armure)
 *   1.6 att/s                     ╚════╝     aperçu de l'objet qui lévite et se retourne
 */
final class ItemHeader implements Card.Header {
    private static final int PAD = 7, NAME_H = 11, SUBTITLE_H = 10, ICON_TOP = PAD + 12;

    private final ItemStack stack;
    private final Component name;
    private final Component rarityLabel;
    private final Component subtitle;
    @Nullable private final BaseValue base;
    private final int accent;
    /** Taille de l'aperçu de l'objet (la même pour tous les objets). */
    private final int icon = ClientConfig.iconSize();

    ItemHeader(ItemStack stack, @Nullable ItemDefinition def, @Nullable ItemStats stats, ModRarity rarity, int accent) {
        this.stack = stack;
        this.accent = accent;
        this.name = stack.getHoverName().copy().withStyle(Style.EMPTY.withBold(true)
                .withColor(TextColor.fromRgb(RpgFrame.mix(accent, 0xFFFFFF, 0.25f))));
        this.rarityLabel = rarityLabel(rarity, accent, stats != null ? stats.upgrades() : 0);
        MutableComponent sub = ItemTypes.typeName(stack).copy();
        if (def != null) sub.append(" · ").append(Component.translatable("tooltip.kpitemsrarity.tier", def.tier));
        this.subtitle = sub;
        this.base = BaseValue.of(stack, def, stats);
    }

    /** "✦✦ Épique" : la rareté, avec une étoile par cran gagné à la forge. */
    private static Component rarityLabel(ModRarity rarity, int accent, int upgrades) {
        MutableComponent label = Component.empty();
        if (upgrades > 0) label.append(Component.literal("✦".repeat(upgrades) + " ").withStyle(s -> s.withColor(RpgFrame.OVERMAX)));
        label.append(Component.translatable("rarity.kpitemsrarity." + rarity.getId()).withStyle(s -> s.withColor(accent)));
        return label;
    }

    @Override
    public int width(Font font) {
        int nameLine = Math.min(font.width(name), ScrollingName.MAX_WIDTH) + 12 + font.width(rarityLabel);
        int textBesideIcon = Math.max(font.width(subtitle), base != null ? base.width(font) : 0) + icon + 10;
        return Math.max(nameLine, textBesideIcon);
    }

    /** Assez haut pour le texte (nom, type, valeur de base) et pour l'aperçu, le plus haut des deux. */
    @Override
    public int height() {
        int textBottom = PAD + NAME_H + SUBTITLE_H + (base != null ? base.height() + 3 : 0) - 3;
        int iconBottom = ICON_TOP + icon + 3;
        return Math.max(textBottom, iconBottom) + PAD - 2;
    }

    @Override
    public void draw(GuiGraphics g, Font font, int left, int right, int top) {
        int y = top + PAD;
        ScrollingName.draw(g, font, name, stack, left, y, right - left - font.width(rarityLabel) - 6);
        g.drawString(font, rarityLabel, right - font.width(rarityLabel), y - 2, 0xFFFFFF, true);
        y += NAME_H;
        g.drawString(font, subtitle, left, y, RpgFrame.TEXT_DIM, true);
        y += SUBTITLE_H;
        if (base != null) base.draw(g, font, left, y + 3);
        drawIcon(g, right - icon, top + ICON_TOP);
    }

    /** Aperçu de l'objet sur un petit socle ; il lévite et se retourne toutes les 6 s comme une pièce qu'on fait tourner. */
    private void drawIcon(GuiGraphics g, int x, int y) {
        g.fill(x - 3, y - 3, x + icon + 3, y + icon + 3, RpgFrame.argb(accent, 0x22));
        g.fill(x - 2, y + icon + 2, x + icon + 2, y + icon + 3, RpgFrame.argb(accent, 0x70));
        float flip = 1f, bob = 0f;
        if (ClientConfig.animatedIcon()) {
            long ms = Util.getMillis();
            bob = (float) Math.sin(ms / 650.0);
            float t = (ms % 6000L) / 900f;
            if (t < 1f) {
                float u = t * t * (3f - 2f * t);
                flip = Math.max(0.06f, Math.abs((float) Math.cos(u * Math.PI * 2)));
            }
        }
        float scale = icon / 16f;
        g.pose().pushPose();
        g.pose().translate(x + icon / 2f, y + icon / 2f + bob, 0);
        g.pose().scale(scale * flip, scale, 1.0f);
        g.renderFakeItem(stack, -8, -8);
        g.pose().popPose();
    }

    // ---------------------------------------------------------------- Valeur de base

    /** "🛡 5" ou "⚔ 6 (5 - 7)" en grand, avec la vitesse d'attaque dessous ({@code label}, vide s'il n'y en a pas). */
    record BaseValue(Component label, String value, String range, boolean armor) {

        /** La valeur de base de l'objet (armure ou dégâts), ou null s'il n'en a pas. */
        @Nullable
        static BaseValue of(ItemStack stack, @Nullable ItemDefinition def, @Nullable ItemStats stats) {
            ItemAttributeModifiers mods = stack.getAttributeModifiers();
            if (stack.getItem() instanceof ArmorItem) {
                double armor = sum(mods, Attributes.ARMOR);
                if (armor > 0) return new BaseValue(Component.empty(), Texts.number(armor), "", true); // l'icône d'armure suffit
            }
            BaseValue damage = modDamage(def, stats, attackSpeed(mods));
            if (damage != null) return damage;
            double vanilla = sum(mods, Attributes.ATTACK_DAMAGE);
            return vanilla > 0 ? new BaseValue(attackSpeed(mods), Texts.number(1.0 + vanilla), "", false) : null;
        }

        /** La ligne de base "Dégâts" du mod : le jet et sa fourchette, ou la fourchette seule d'un objet pas encore tiré. */
        @Nullable
        private static BaseValue modDamage(@Nullable ItemDefinition def, @Nullable ItemStats stats, Component speed) {
            if (def == null) return null;
            for (String statId : def.stats.keySet()) {
                StatDefinition sd = RarityData.stat(statId);
                if (sd == null || !sd.base) continue;
                if (stats != null) {
                    if (!stats.lines().containsKey(statId)) continue;
                    RollRange r = ItemRolls.range(def, statId, stats.upgrades());
                    return new BaseValue(speed, String.valueOf(stats.get(statId)), r != null ? "(" + r.format(sd) + ")" : "", false);
                }
                RollRange r = def.range(statId);
                if (r != null) return new BaseValue(speed, r.format(sd), "", false);
            }
            return null;
        }

        /** L'icône ⚔ suffit pour "Dégâts" : on n'affiche que la vitesse d'attaque (vide si l'objet n'en change pas). */
        private static Component attackSpeed(ItemAttributeModifiers mods) {
            double speed = sum(mods, Attributes.ATTACK_SPEED);
            return speed != 0 ? Component.translatable("tooltip.kpitemsrarity.rpg.speed", Texts.number(4.0 + speed)) : Component.empty();
        }

        /** Somme des bonus fixes de cet attribut sur l'objet. */
        static double sum(ItemAttributeModifiers mods, Holder<Attribute> attribute) {
            double total = 0;
            for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
                if (e.attribute().equals(attribute) && e.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
                    total += e.modifier().amount();
                }
            }
            return total;
        }

        int width(Font font) {
            return Math.max(12 + (int) (font.width(value) * 1.5f) + 4 + (range.isEmpty() ? 0 : font.width(range)), 12 + font.width(label));
        }

        /** La valeur, plus la vitesse d'attaque en dessous s'il y en a une. */
        int height() {
            return label.getString().isEmpty() ? 14 : 23;
        }

        void draw(GuiGraphics g, Font font, int x, int y) {
            if (armor) g.blitSprite(ResourceLocation.withDefaultNamespace("hud/armor_full"), x, y + 2, 9, 9);
            else g.drawString(font, "⚔", x, y + 2, RpgFrame.TEXT_SOFT, true);
            int vx = x + 12;
            g.pose().pushPose();
            g.pose().translate(vx, y, 0);
            g.pose().scale(1.5f, 1.5f, 1.0f);
            g.drawString(font, value, 0, 0, RpgFrame.TEXT, true);
            g.pose().popPose();
            if (!range.isEmpty()) g.drawString(font, range, vx + (int) (font.width(value) * 1.5f) + 4, y + 4, RpgFrame.TEXT_FAINT, true);
            if (!label.getString().isEmpty()) g.drawString(font, label, vx, y + 14, RpgFrame.TEXT_DIM, true);
        }
    }

    // ---------------------------------------------------------------- Nom qui défile

    /**
     * Nom trop long pour la place : "…" d'abord, puis après une seconde de survol il défile d'un bout à l'autre
     * (aller-retour, avec une pause à chaque extrémité).
     */
    static final class ScrollingName {
        private ScrollingName() {}

        /** Largeur maximale que le nom peut imposer à l'infobulle. */
        static final int MAX_WIDTH = 110;
        private static final long WAIT_MS = 1000, PAUSE_MS = 900;
        private static final float SPEED = 0.03f; // pixels par milliseconde

        private static ItemStack stack = ItemStack.EMPTY;
        private static long since;

        static void draw(GuiGraphics g, Font font, Component name, ItemStack hovered, int x, int y, int maxW) {
            int full = font.width(name);
            long now = Util.getMillis();
            if (!ItemStack.isSameItemSameComponents(hovered, stack)) {
                stack = hovered.copy();
                since = now;
            }
            long elapsed = now - since - WAIT_MS;
            if (full <= maxW || elapsed < 0) {
                g.drawString(font, RpgFrame.fit(font, name, maxW), x, y, 0xFFFFFF, true);
                return;
            }
            g.enableScissor(x, y - 1, x + maxW, y + 10);
            g.drawString(font, name, x - Math.round(shift(full - maxW, elapsed)), y, 0xFFFFFF, true);
            g.disableScissor();
        }

        /** Décalage du texte : aller, pause au bout, retour, pause au début. */
        private static float shift(int overflow, long elapsed) {
            long travel = (long) (overflow / SPEED);
            long t = elapsed % (2 * (travel + PAUSE_MS));
            if (t < travel) return t * SPEED;
            if (t < travel + PAUSE_MS) return overflow;
            if (t < 2 * travel + PAUSE_MS) return overflow - (t - travel - PAUSE_MS) * SPEED;
            return 0;
        }
    }
}
