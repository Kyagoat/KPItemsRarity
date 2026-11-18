package net.Kyap.ItemsRarity.client;

import net.Kyap.ItemsRarity.ClientConfig;
import net.Kyap.ItemsRarity.ItemsRarity;
import net.Kyap.ItemsRarity.component.ItemStats;
import net.Kyap.ItemsRarity.component.ModDataComponents;
import net.Kyap.ItemsRarity.data.ItemDefinition;
import net.Kyap.ItemsRarity.data.RarityData;
import net.Kyap.ItemsRarity.data.SetDefinition;
import net.Kyap.ItemsRarity.data.StatDefinition;
import net.Kyap.ItemsRarity.events.AttributeEvents;
import net.Kyap.ItemsRarity.util.ModRarities.ModRarity;
import net.Kyap.ItemsRarity.util.StatEngine;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.*;

/**
 * Infobulle RPG des équipements :
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
 *  │ Seigneur du Nether 2/4   Maintiens [Alt]  │
 *  │            Durabilité : 1800 / 2031       │
 *  └──────────────────────────────────────────┘
 * Le cadre prend la couleur de la rareté (et pulse en Légendaire / Mythique).
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = ItemsRarity.MOD_ID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class RpgTooltip {
    private RpgTooltip() {}

    // Défilement des infobulles trop hautes pour l'écran (molette sur l'objet survolé)
    private static ItemStack scrollStack = ItemStack.EMPTY;
    private static int scrollOffset, scrollMax;
    private static long lastTallRender;

    @net.neoforged.bus.api.SubscribeEvent
    public static void onScroll(net.neoforged.neoforge.client.event.ScreenEvent.MouseScrolled.Pre event) {
        if (scrollMax <= 0 || net.minecraft.Util.getMillis() - lastTallRender > 150) return;
        int next = Math.max(0, Math.min(scrollMax, scrollOffset - (int) Math.signum(event.getScrollDeltaY()) * 22));
        // Arrivé en bas (ou en haut), la molette n'est plus retenue : elle repart à l'écran (liste du créatif...).
        if (next == scrollOffset) return;
        scrollOffset = next;
        event.setCanceled(true);
    }

    private static final int PAD = 7;
    private static final int MIN_WIDTH = 120;
    private static final int MAX_WIDTH = 190;
    private static final int LINE = 11;
    /** Place gardée à droite pour la barre de défilement. */
    private static final int GUTTER = 7;
    private static boolean warned;

    /**
     * Appelé par GuiGraphicsTooltipMixin pour TOUTE infobulle d'objet.
     * @param images composants non textuels de l'infobulle (cases d'appât de Tide, cartes...)
     * @return true si l'infobulle normale ne doit pas être dessinée.
     */
    public static boolean handle(GuiGraphics g, Font font, List<Component> lines,
                                 List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> images,
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
            render(g, font, lines, images, stack, def, stats, mouseX, mouseY);
            return true;
        } catch (RuntimeException e) {
            if (!warned) {
                warned = true;
                ItemsRarity.LOGGER.error("Infobulle RPG impossible pour {}, retour à l'infobulle classique", stack, e);
            }
            return false;
        } finally {
            g.pose().popPose();
        }
    }

    // ---------------------------------------------------------------- Modèle

    /** Une ligne du corps : texte à gauche, texte grisé aligné à droite (fourchette...). */
    private record Row(Component left, Component right, int indent) {}

    private sealed interface Elem permits RowElem, TitleElem, TextElem, GapElem, ImageElem, PassiveElem {}

    /**
     * Encadré d'un passif : nom (couleur de rareté) et voyant d'état sur la première ligne,
     * puis sa description, retournée à la ligne selon la largeur de l'infobulle.
     */
    private record PassiveElem(Component name, Component state, Component desc, @org.jetbrains.annotations.Nullable Component formula,
                               int color, boolean on) implements Elem {}

    /** Largeur utile pour retourner le texte des passifs (fixée avant le calcul des hauteurs). */
    private static int wrapWidth = 170;

    private static List<FormattedCharSequence> passiveLines(PassiveElem p, Font font) {
        List<FormattedCharSequence> out = new ArrayList<>(font.split(p.desc(), Math.max(60, wrapWidth - 10)));
        if (p.formula() != null) out.addAll(font.split(p.formula(), Math.max(60, wrapWidth - 10)));
        return out;
    }
    /** Image d'un autre mod (emplacements d'appât de Tide, contenu d'une sacoche...), dessinée dans le corps. */
    private record ImageElem(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent component) implements Elem {}

    private static int heightOf(Elem e, Font font) {
        if (e instanceof GapElem gap) return gap.height();
        if (e instanceof TitleElem) return 12;
        if (e instanceof ImageElem img) return img.component().getHeight() + 3;
        if (e instanceof PassiveElem p) return 4 + 11 + passiveLines(p, font).size() * 10 + 4 + 3;
        return LINE;
    }
    private record RowElem(Row row) implements Elem {}
    private record TitleElem(Component title, int color) implements Elem {}
    private record TextElem(Component text) implements Elem {}
    private record GapElem(int height) implements Elem {}

    private static void render(GuiGraphics g, Font font, List<Component> vanilla,
                               List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> images, ItemStack stack,
                               ItemDefinition def, ItemStats stats, int mouseX, int mouseY) {
        // Équipement du mod, ou n'importe quel autre objet (matériau, bloc, nourriture...)
        boolean gear = def != null || stats != null;
        ModRarity rarity = gear ? StatEngine.rarityOf(stack) : fromVanilla(stack.getRarity());
        int accent = RpgFrame.rarityColor(rarity);
        int upgrades = stats != null ? stats.upgrades() : 0;
        int icon = ClientConfig.iconSize(); // même taille pour tous les objets

        // ---- En-tête
        Component name = stack.getHoverName().copy().withStyle(Style.EMPTY.withBold(true)
                .withColor(TextColor.fromRgb(RpgFrame.mix(accent, 0xFFFFFF, 0.25f))));
        MutableComponent rarityLabel = Component.empty();
        if (upgrades > 0) rarityLabel.append(Component.literal("✦".repeat(upgrades) + " ").withStyle(s -> s.withColor(TooltipHandler.OVERMAX_COLOR)));
        rarityLabel.append(Component.translatable("rarity.itemsrarity." + rarity.getId()).withStyle(s -> s.withColor(accent)));
        MutableComponent subtitle = typeName(stack).copy();
        if (def != null) subtitle.append(" · ").append(Component.translatable("tooltip.itemsrarity.tier", def.tier));

        BaseValue base = baseValue(stack, def, stats);

        // ---- Corps
        HINT.set(Component.translatable("tooltip.itemsrarity.rpg.help_hint").withStyle(st -> st.withColor(RpgFrame.TEXT_FAINT)));
        List<Elem> body = new ArrayList<>();
        if (stats != null) {
            Set<String> shown = new HashSet<>();
            if (def != null) {
                for (String statId : def.stats.keySet()) {
                    shown.add(statId);
                    if (!stats.lines().containsKey(statId)) continue;
                    StatDefinition sd = RarityData.stat(statId);
                    if (sd != null && sd.base) continue; // affichée en grand dans l'en-tête
                    body.add(new RowElem(statRow(statId, stats.get(statId), StatEngine.range(def, statId, upgrades))));
                    addStatHelp(body, font, sd);
                }
            }
            stats.lines().forEach((statId, value) -> {
                if (!shown.contains(statId)) body.add(new RowElem(statRow(statId, value, null)));
            });
        } else if (def != null) {
            for (String statId : def.stats.keySet()) {
                StatDefinition sd = RarityData.stat(statId);
                int[] r = def.range(statId);
                if (sd == null || sd.base || r == null) continue;
                body.add(new RowElem(possibleRow(sd, r)));
            }
        }
        // Indice discret : Maj affiche ce que fait chaque stat
        if (stats != null && !net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            // Sur la stat la plus courte : c'est là qu'il a le plus de chances de tenir sans élargir
            int best = -1, bestW = Integer.MAX_VALUE;
            for (int i = 0; i < body.size(); i++) {
                if (body.get(i) instanceof RowElem r && r.row().right().getString().isEmpty() && font.width(r.row().left()) < bestW) {
                    best = i;
                    bestW = font.width(r.row().left());
                }
            }
            if (best >= 0 && body.get(best) instanceof RowElem r) body.set(best, new RowElem(new Row(r.row().left(), HINT.get(), r.row().indent())));
        }
        for (Row row : vanillaAttributeRows(stack)) body.add(new RowElem(row));
        foodRows(stack, body);
        int afterStats = body.size();

        if (stats != null && stats.hasExo()) {
            body.add(new GapElem(3));
            body.add(new TitleElem(Component.translatable("tooltip.itemsrarity.rpg.exo"), RpgFrame.EXO));
            StatDefinition sd = RarityData.stat(stats.exoStat());
            Component exo = Component.literal("✦ ").withStyle(s -> s.withColor(RpgFrame.EXO))
                    .append(Component.literal((sd != null ? sd.formatValue(stats.exoValue(), true) : "+" + stats.exoValue()) + " ")
                            .append(sd != null ? sd.name() : Component.literal(stats.exoStat()))
                            .withStyle(s -> s.withColor(RpgFrame.EXO)));
            body.add(new RowElem(new Row(exo, Component.empty(), 0)));
        }

        // Puits de forgemagie : ce que les échecs ont mis de côté pour les prochaines runes
        if (stats != null && stats.well() > 0) {
            body.add(new RowElem(new Row(Component.translatable("forge.itemsrarity.well", stats.well())
                    .withStyle(st -> st.withColor(0x6FD8E8)), Component.empty(), 0)));
        }

        Set<String> enchantTexts = new HashSet<>();
        ItemEnchantments enchants = stack.getTagEnchantments();
        if (!enchants.isEmpty()) {
            body.add(new GapElem(3));
            body.add(new TitleElem(Component.translatable("tooltip.itemsrarity.rpg.enchantments"), RpgFrame.MAGIC));
            for (var entry : enchants.entrySet()) {
                Holder<Enchantment> holder = entry.getKey();
                Component full = Enchantment.getFullname(holder, entry.getIntValue());
                enchantTexts.add(full.getString());
                boolean curse = holder.is(EnchantmentTags.CURSE);
                int color = curse ? RpgFrame.NEGATIVE : RpgFrame.mix(RpgFrame.MAGIC, 0xFFFFFF, 0.3f);
                Component line = Component.literal("◆ ").withStyle(s -> s.withColor(RpgFrame.MAGIC))
                        .append(Component.literal(full.getString()).withStyle(s -> s.withColor(color)));
                body.add(new RowElem(new Row(line, Component.empty(), 0)));
            }
        }

        if (def != null && def.hasSet()) {
            SetDefinition set = RarityData.set(def.set);
            if (set != null) {
                Player player = Minecraft.getInstance().player;
                int worn = player != null ? StatEngine.countSetPieces(player).getOrDefault(def.set, 0) : 0;
                int total = SetCard.pieceCount(def.set);
                body.add(new GapElem(3));
                int setTier = SetCard.tierOfSet(def.set);
                Component left = set.name().withStyle(s -> s.withColor(RpgFrame.tierColor(setTier)))
                        .append(Component.literal(" ")).append(RpgFrame.tierPips(setTier))
                        .append(Component.literal("  " + worn + "/" + total).withStyle(s -> s.withColor(worn > 0 ? RpgFrame.POSITIVE : RpgFrame.TEXT_DIM)));
                Component right = Component.translatable("tooltip.itemsrarity.rpg.set_hint", shortKey(ShareKey.SET_DETAILS.getTranslatedKeyMessage()))
                        .withStyle(s -> s.withColor(RpgFrame.TEXT_FAINT));
                body.add(new RowElem(new Row(left, right, 0)));
                // Pièce qui partage sa case avec une autre de la panoplie : une seule des deux compte
                List<String> others = SetCard.alternatives(def.set, StatEngine.itemId(stack));
                if (!others.isEmpty()) {
                    Component choice = Component.translatable("tooltip.itemsrarity.rpg.set_choice", SetCard.names(others))
                            .withStyle(s -> s.withColor(RpgFrame.TEXT_DIM));
                    body.add(new RowElem(new Row(choice, Component.empty(), 0)));
                }
            }
        }

        addConditions(body, def);
        addPassives(body, def);

        // Lignes des autres mods, description (lore), infos avancées (F3+H)...
        List<Component> extras = new ArrayList<>(extraLines(vanilla, stack, enchantTexts));
        // Objet simple : sa description (écrite ou automatique, voir ItemDescriptions) va dans l'en-tête.
        ItemDescriptions.Description description = gear ? null : ItemDescriptions.describe(stack);
        // Lignes vanilla et des autres mods (portée d'attaque, contenu...) : avec les stats, aux mêmes couleurs.
        // Si la description dit déjà la même chose (effets d'une potion...), on ne les répète pas.
        // Les lignes qui ressemblent à des stats vont avec les stats ; les textes (description d'un autre mod...)
        // vont en bas, à la place de la légende. Le nom du mod (ajouté par JEI & co) est retiré.
        List<Elem> modRows = new ArrayList<>();
        List<Component> modTexts = new ArrayList<>();
        String modName = modName(stack);
        for (Component c : extras) {
            String str = c.getString();
            if (str.isBlank() || str.equals(modName)) continue;
            if (STAT_LINE.matcher(str).matches()) modRows.add(new RowElem(new Row(asStatLine(c), Component.empty(), 0)));
            else modTexts.add(c);
        }
        modTexts = joinBrokenLines(modTexts);
        if (description != null && description.replacesVanilla()) {
            modRows.clear();
            modTexts.clear();
        }
        // Image du mod (Tide : emplacements d'appât...) juste après les stats
        for (var image : images) modRows.add(new ImageElem(image));
        body.addAll(afterStats, modRows);
        extras.clear();
        extras.addAll(modTexts);
        // Une seule légende : si l'objet a déjà sa propre description (son mod), pas de description automatique.
        if (description != null && description.auto() && !modTexts.isEmpty()) description = null;

        if (stats != null && Screen.hasShiftDown()) {
            body.add(new GapElem(3));
            body.add(new RowElem(new Row(Component.translatable("tooltip.itemsrarity.rpg.quality").withStyle(s -> s.withColor(RpgFrame.TEXT_DIM)),
                    Component.literal(Math.round(StatEngine.quality(stack) * 100) + " %").withStyle(s -> s.withColor(RpgFrame.TEXT_SOFT)), 0)));
        }

        Component durability = null;
        if (stack.isDamageableItem()) {
            int max = stack.getMaxDamage();
            durability = Component.translatable("tooltip.itemsrarity.rpg.durability", max - stack.getDamageValue(), max);
        }

        // ---- Mesures
        int baseW = base != null ? base.width(font) : 0;
        // Un nom très long n'élargit pas l'infobulle : il est coupé avec "…" puis défile au survol prolongé
        int headerTextW = Math.max(Math.min(font.width(name), NAME_MAX_W) + 12 + font.width(rarityLabel), Math.max(font.width(subtitle), baseW) + icon + 10);
        int contentW = headerTextW;
        for (Elem e : body) {
            if (e instanceof RowElem r) {
                int w = r.row().indent() + font.width(r.row().left());
                int rw = isHint(r.row().right()) ? 0 : font.width(r.row().right());
                contentW = Math.max(contentW, rw > 0 ? w + 12 + rw : w);
            } else if (e instanceof TitleElem t) {
                contentW = Math.max(contentW, font.width(t.title()) + 20);
            } else if (e instanceof ImageElem img) {
                contentW = Math.max(contentW, img.component().getWidth(font));
            }
        }
        if (durability != null) contentW = Math.max(contentW, font.width(durability));

        // Texte d'en-tête (lore + description d'un objet simple) : il coule à gauche de l'aperçu,
        // puis reprend toute la largeur une fois passé sous l'image.
        List<Component> paras = new ArrayList<>();
        // Équipement : le lore va tout en bas, après les stats. Objet simple : sa description dans l'en-tête.
        if (gear) {
            Component lore = LoreText.item(stack, def);
            if (lore != null) extras.add(lore);
        } else if (description != null) {
            extras.addAll(description.parts()); // même place que le lore d'un équipement : tout en bas
        }
        // Largeur ADAPTATIVE : au moins la largeur de base, puis on élargit (jusqu'au maximum) tant que
        // l'infobulle est trop haute pour sa largeur. Un long texte donne une infobulle large et basse
        // plutôt qu'une colonne étroite et haute.
        int rowsH = 0;
        for (Elem e : body) rowsH += heightOf(e, font);
        int maxW = Math.max(ClientConfig.tooltipWidth(), ClientConfig.tooltipMaxWidth());
        int width = Math.min(maxW, Math.max(ClientConfig.tooltipWidth(), contentW + 2 * PAD));
        for (int w = width; w <= maxW; w += 8) {
            width = w;
            int lines = wrap(font, extras, w - 2 * PAD - GUTTER).size();
            int estimate = 59 + 10 + rowsH + (lines > 0 ? 6 + lines * 10 : 0) + (durability != null ? 13 : 0);
            if (estimate <= w * 0.8f) break;
        }
        int inner = width - 2 * PAD;
        wrapWidth = inner - GUTTER;
        List<FormattedCharSequence> extraWrapped = wrap(font, extras, inner - GUTTER);

        int iconTop = PAD + 12;                         // relatif au haut de l'infobulle
        int iconBottom = iconTop + icon + 3;
        // En-tête de hauteur fixe (nom, type, ligne de dégâts/armure sur 2 lignes, aperçu) pour tous les objets
        // En-tête à la hauteur de son contenu : nom, type, ligne de dégâts/armure s'il y en a une, aperçu
        int textTop = PAD + 11 + 10 + (base != null ? base.height() + 3 : 0);
        List<FormattedCharSequence> headerLines = flow(font, paras, textTop, iconBottom, inner - icon - 8, inner);
        int textBottom = headerLines.isEmpty() ? textTop - (paras.isEmpty() ? 0 : 4) : textTop + headerLines.size() * 10;
        int headerH = Math.max(textTop - 3, iconBottom) + PAD - 2;

        int bodyH = 0;
        for (Elem e : body) bodyH += heightOf(e, font);
        if (!extraWrapped.isEmpty()) bodyH += (bodyH > 0 ? 6 : 0) + extraWrapped.size() * 10;
        int footerH = durability != null ? 13 : 0;

        // Trop haute pour l'écran : seul le corps défile (molette), l'en-tête et la durabilité restent.
        int screenW = g.guiWidth(), screenH = g.guiHeight();
        int fixedH = headerH + 6 + 4 + footerH + 4;
        int bodyView = bodyH;
        int maxView = Math.max(30, Math.min(ClientConfig.maxBodyHeight(), screenH - 8 - fixedH));
        if (bodyH > maxView) {
            bodyView = maxView;
            if (!ItemStack.isSameItemSameComponents(stack, scrollStack)) {
                scrollStack = stack.copy();
                scrollOffset = 0;
            }
            scrollMax = Math.max(0, bodyH - bodyView);
            scrollOffset = Math.min(scrollOffset, scrollMax);
            lastTallRender = net.minecraft.Util.getMillis();
        } else {
            if (ItemStack.isSameItemSameComponents(stack, scrollStack)) scrollMax = 0;
        }
        boolean scrolling = bodyView < bodyH;
        int offset = scrolling ? scrollOffset : 0;
        int height = headerH + (bodyH > 0 ? 6 + bodyView + 4 : 0) + footerH + 4;

        // ---- Position (comme vanilla : à droite de la souris, retournée si ça dépasse)
        int x = mouseX + 12, y = mouseY - 12;
        if (x + width + 4 > screenW) x = Math.max(4, mouseX - 16 - width);
        if (y + height + 4 > screenH) y = screenH - height - 4;
        y = Math.max(4, y);

        g.pose().translate(0, 0, 400);
        RpgFrame.draw(g, x, y, width, height, headerH, accent, RpgFrame.pulses(rarity), RpgFrame.ornateLevel(rarity));

        // ---- En-tête
        int left = x + PAD;
        int right = x + width - PAD;
        int cy = y + PAD;
        drawName(g, font, name, stack, left, cy, inner - font.width(rarityLabel) - 6);
        g.drawString(font, rarityLabel, right - font.width(rarityLabel), cy - 2, 0xFFFFFF, true);
        cy += 11;
        g.drawString(font, subtitle, left, cy, RpgFrame.TEXT_DIM, true);
        cy += 10;
        if (base != null) base.draw(g, font, left, cy + 3);
        cy = y + textTop;
        for (FormattedCharSequence line : headerLines) {
            g.drawString(font, line, left, cy, RpgFrame.TEXT_DIM, true);
            cy += 10;
        }

        // Aperçu de l'objet, avec un petit socle
        int ix = right - icon, iy = y + iconTop;
        float scale = icon / 16f;
        g.fill(ix - 3, iy - 3, ix + icon + 3, iy + icon + 3, RpgFrame.argb(accent, 0x22));
        g.fill(ix - 2, iy + icon + 2, ix + icon + 2, iy + icon + 3, RpgFrame.argb(accent, 0x70));
        g.pose().pushPose();
        float flip = 1f, bob = 0f;
        if (ClientConfig.animatedIcon()) {
            // Petite lévitation, et toutes les 6 s l'objet se retourne comme une pièce qu'on fait tourner.
            long ms = net.minecraft.Util.getMillis();
            bob = (float) Math.sin(ms / 650.0) * 1.0f;
            float t = (ms % 6000L) / 900f;
            if (t < 1f) {
                float u = t * t * (3f - 2f * t);
                flip = Math.max(0.06f, Math.abs((float) Math.cos(u * Math.PI * 2)));
            }
        }
        g.pose().translate(ix + icon / 2f, iy + icon / 2f + bob, 0);
        g.pose().scale(scale * flip, scale, 1.0f);
        g.renderFakeItem(stack, -8, -8);
        g.pose().popPose();

        // ---- Corps (au-dessus des objets dessinés), découpé à sa zone visible
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        int bodyTop = y + headerH + 6;
        if (bodyH > 0) {
            if (scrolling) g.enableScissor(x + 1, bodyTop - 1, x + width - 1, bodyTop + bodyView + 1);
            cy = bodyTop - offset;
            for (Elem e : body) {
                if (e instanceof GapElem gap) {
                    cy += gap.height();
                } else if (e instanceof TitleElem t) {
                    cy = RpgFrame.sectionTitle(g, font, t.title(), left, cy, inner, t.color());
                } else if (e instanceof ImageElem img) {
                    img.component().renderImage(font, left, cy + 1, g);
                    cy += heightOf(e, font);
                } else if (e instanceof PassiveElem p) {
                    int h = heightOf(e, font) - 3;
                    int gutter = scrolling ? GUTTER : 0;
                    int bx0 = left, bx1 = right - gutter, by0 = cy + 1, by1 = cy + h;
                    g.fill(bx0, by0, bx1, by1, RpgFrame.argb(p.color(), p.on() ? 0x24 : 0x12));
                    int edge = RpgFrame.argb(p.color(), p.on() ? 0xB0 : 0x50);
                    g.fill(bx0, by0, bx1, by0 + 1, edge);
                    g.fill(bx0, by1 - 1, bx1, by1, edge);
                    g.fill(bx0, by0, bx0 + 1, by1, edge);
                    g.fill(bx1 - 1, by0, bx1, by1, edge);
                    int ty = by0 + 4;
                    g.drawString(font, p.name(), bx0 + 5, ty, 0xFFFFFF, true);
                    g.drawString(font, p.state(), bx1 - 5 - font.width(p.state()), ty, 0xFFFFFF, true);
                    ty += 11;
                    List<FormattedCharSequence> lines = passiveLines(p, font);
                    int descLines = font.split(p.desc(), Math.max(60, wrapWidth - 10)).size();
                    for (int i = 0; i < lines.size(); i++) {
                        g.drawString(font, lines.get(i), bx0 + 5, ty, i < descLines ? (p.on() ? 0xE8E8E8 : RpgFrame.TEXT_DIM) : RpgFrame.TEXT_FAINT, true);
                        ty += 10;
                    }
                    cy += heightOf(e, font);
                } else if (e instanceof RowElem r) {
                    Component rt = r.row().right();
                    int gutter = scrolling ? GUTTER : 0;
                    // Indice "[Maj]" : seulement s'il tient à côté de la ligne
                    if (isHint(rt) && font.width(r.row().left()) + r.row().indent() + 8 + font.width(rt) > inner - gutter) rt = Component.empty();
                    int leftMax = inner - gutter - r.row().indent() - (font.width(rt) > 0 ? font.width(rt) + 6 : 0);
                    g.drawString(font, fit(font, r.row().left(), leftMax), left + r.row().indent(), cy, 0xFFFFFF, true);
                    int rx = right - gutter - font.width(rt);
                    if (font.width(rt) > 0) g.drawString(font, rt, rx, cy, 0xFFFFFF, true);
                    cy += LINE;
                }
            }
            if (!extraWrapped.isEmpty()) {
                if (bodyH > extraWrapped.size() * 10) {
                    g.fill(left, cy + 1, right, cy + 2, RpgFrame.argb(RpgFrame.TEXT_FAINT, 0x60));
                    cy += 6;
                }
                for (FormattedCharSequence line : extraWrapped) {
                    g.drawString(font, line, left, cy, RpgFrame.TEXT_DIM, true);
                    cy += 10;
                }
            }
            if (scrolling) {
                g.disableScissor();
                RpgFrame.scrollChrome(g, font, x, width, right, bodyTop, bodyView, bodyH, offset, scrollMax, accent);
            }
        }
        if (durability != null) {
            int fy = y + height - footerH - 2;
            g.fill(left, fy, right, fy + 1, RpgFrame.argb(accent, 0x40));
            g.drawString(font, durability, x + (width - font.width(durability)) / 2, fy + 3, RpgFrame.DURABILITY, true);
        }
        g.pose().popPose();
    }

    // ---------------------------------------------------------------- Nom défilant

    /** Largeur maximale que le nom peut imposer à l'infobulle. */
    private static final int NAME_MAX_W = 110;
    private static final long NAME_WAIT_MS = 1000, NAME_PAUSE_MS = 900;
    private static final float NAME_SPEED = 0.03f; // pixels par milliseconde
    private static ItemStack nameStack = ItemStack.EMPTY;
    private static long nameSince;

    /**
     * Nom trop long pour la place : "…" d'abord, puis après un survol d'une seconde il défile
     * d'un bout à l'autre (aller-retour, avec une pause à chaque extrémité).
     */
    private static void drawName(GuiGraphics g, Font font, Component name, ItemStack stack, int x, int y, int maxW) {
        int full = font.width(name);
        long now = net.minecraft.Util.getMillis();
        if (!ItemStack.isSameItemSameComponents(stack, nameStack)) {
            nameStack = stack.copy();
            nameSince = now;
        }
        long elapsed = now - nameSince - NAME_WAIT_MS;
        if (full <= maxW || elapsed < 0) {
            g.drawString(font, fit(font, name, maxW), x, y, 0xFFFFFF, true);
            return;
        }
        int overflow = full - maxW;
        long travel = (long) (overflow / NAME_SPEED);
        long t = elapsed % (2 * (travel + NAME_PAUSE_MS));
        float shift;
        if (t < travel) shift = t * NAME_SPEED;                                   // aller
        else if (t < travel + NAME_PAUSE_MS) shift = overflow;                      // pause au bout
        else if (t < 2 * travel + NAME_PAUSE_MS) shift = overflow - (t - travel - NAME_PAUSE_MS) * NAME_SPEED; // retour
        else shift = 0;                                                             // pause au début
        g.enableScissor(x, y - 1, x + maxW, y + 10);
        g.drawString(font, name, x - Math.round(shift), y, 0xFFFFFF, true);
        g.disableScissor();
    }

    /** Ligne prête à dessiner -> texte avec ses styles (pour la relire et la remettre à nos couleurs). */
    public static Component toComponent(FormattedCharSequence seq) {
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        StringBuilder buf = new StringBuilder();
        net.minecraft.network.chat.Style[] current = {net.minecraft.network.chat.Style.EMPTY};
        seq.accept((index, style, codePoint) -> {
            if (!style.equals(current[0]) && buf.length() > 0) {
                out.append(Component.literal(buf.toString()).withStyle(current[0]));
                buf.setLength(0);
            }
            current[0] = style;
            buf.appendCodePoint(codePoint);
            return true;
        });
        if (buf.length() > 0) out.append(Component.literal(buf.toString()).withStyle(current[0]));
        return out;
    }

    private static final java.util.regex.Pattern STAT_LINE = java.util.regex.Pattern.compile("^\\s*([+-]?\\d+(?:[.,]\\d+)?\\s*%?)\\s+(.+)$");

    /** Ligne d'un autre mod qui ressemble à une stat ("3 Attack Range") : mêmes couleurs que nos stats. */
    private static Component asStatLine(Component line) {
        var m = STAT_LINE.matcher(line.getString());
        if (!m.matches()) return line.copy().withStyle(s -> s.withColor(RpgFrame.TEXT_SOFT));
        String value = m.group(1).trim();
        // Même écriture que nos stats : "+3 Portée d'attaque"
        if (!value.startsWith("+") && !value.startsWith("-")) value = "+" + value;
        int color = value.startsWith("-") ? RpgFrame.NEGATIVE : RpgFrame.POSITIVE;
        return Component.literal(value + " ").withStyle(s -> s.withColor(color))
                .append(Component.literal(m.group(2)).withStyle(s -> s.withColor(RpgFrame.TEXT_SOFT)));
    }

    /**
     * Les mods coupent souvent leur description en plusieurs lignes au milieu d'une phrase.
     * On recolle les lignes qui ne finissent pas une phrase, pour que le texte suive la largeur de l'infobulle.
     * Les explications sont en gris clair, comme les nôtres.
     */
    private static List<Component> joinBrokenLines(List<Component> lines) {
        List<Component> out = new ArrayList<>();
        StringBuilder current = null;
        for (Component c : lines) {
            String text = c.getString().trim();
            if (current == null) current = new StringBuilder(text);
            else current.append(' ').append(text);
            if (text.matches(".*[.!?:;)\\]]$")) {
                out.add(Component.literal(current.toString()).withStyle(st -> st.withColor(RpgFrame.TEXT_DIM)));
                current = null;
            }
        }
        if (current != null) out.add(Component.literal(current.toString()).withStyle(st -> st.withColor(RpgFrame.TEXT_DIM)));
        return out;
    }

    /** Nom affiché du mod de l'objet (la ligne "Items Rarity" ajoutée par JEI & co). */
    private static String modName(ItemStack stack) {
        String ns = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        return net.neoforged.fml.ModList.get().getModContainerById(ns).map(c -> c.getModInfo().getDisplayName()).orElse("");
    }

    /** Coupe un texte trop long avec "…" (largeur fixe de l'infobulle). */
    private static FormattedCharSequence fit(Font font, Component text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text.getVisualOrderText();
        var cut = font.substrByWidth(text, Math.max(0, maxWidth - font.width("…")));
        return net.minecraft.locale.Language.getInstance().getVisualOrder(
                net.minecraft.network.chat.FormattedText.composite(cut, net.minecraft.network.chat.FormattedText.of("…")));
    }

    /** "Left Alt" -> "Alt", "Alt gauche" -> "Alt" : plus court dans l'infobulle. */
    private static Component shortKey(Component key) {
        String text = key.getString().replaceAll("(?i)\\b(left|right|gauche|droite?)\\b", "").trim();
        return Component.literal(text.isEmpty() ? key.getString() : text);
    }

    /**
     * Coupe les paragraphes (avec leurs couleurs : valeurs surlignées...) en lignes : étroites tant qu'on est
     * à côté de l'aperçu, pleines ensuite.
     * @param top ordonnée de la première ligne, @param narrowUntil ordonnée où l'aperçu s'arrête
     */
    private static List<FormattedCharSequence> flow(Font font, List<Component> paras, int top, int narrowUntil, int narrow, int full) {
        List<FormattedCharSequence> out = new ArrayList<>();
        int[] y = {top};
        for (Component para : paras) {
            // Découpe en mots, chaque mot gardant les styles de ses morceaux
            List<List<Component>> words = new ArrayList<>();
            List<Component> current = new ArrayList<>();
            para.visit((style, text) -> {
                String[] parts = text.split(" ", -1);
                for (int k = 0; k < parts.length; k++) {
                    if (k > 0) { // un espace : le mot en cours est fini
                        if (!current.isEmpty()) words.add(new ArrayList<>(current));
                        current.clear();
                    }
                    if (!parts[k].isEmpty()) current.add(Component.literal(parts[k]).withStyle(style));
                }
                return java.util.Optional.empty();
            }, Style.EMPTY);
            if (!current.isEmpty()) words.add(new ArrayList<>(current));

            MutableComponent line = null;
            for (List<Component> word : words) {
                MutableComponent w = Component.empty();
                word.forEach(w::append);
                int limit = Math.max(40, y[0] < narrowUntil ? narrow : full);
                if (line != null && font.width(line) + font.width(" ") + font.width(w) > limit) {
                    out.add(line.getVisualOrderText());
                    y[0] += 10;
                    line = null;
                }
                if (line == null) line = Component.empty().append(w);
                else line.append(" ").append(w);
            }
            if (line != null) {
                out.add(line.getVisualOrderText());
                y[0] += 10;
            }
        }
        return out;
    }

    private static List<FormattedCharSequence> wrap(Font font, List<Component> lines, int width) {
        List<FormattedCharSequence> out = new ArrayList<>();
        for (Component c : lines) {
            if (c.getString().isEmpty()) out.add(FormattedCharSequence.EMPTY);
            else out.addAll(font.split(c, Math.max(40, width)));
        }
        return out;
    }

    /** Rareté vanilla (ou d'un autre mod) -> couleur du cadre. */
    static ModRarity fromVanilla(Rarity rarity) {
        String name = rarity.name();
        if (name.contains("MYTHIC")) return ModRarity.MYTHIC;
        if (name.contains("LEGENDARY")) return ModRarity.LEGENDARY;
        return switch (rarity.ordinal()) {
            case 1 -> ModRarity.UNCOMMON;
            case 2 -> ModRarity.RARE;
            case 3 -> ModRarity.EPIC;
            default -> name.equals("COMMON") ? ModRarity.COMMON : ModRarity.RARE;
        };
    }

    /** Nourriture : ce qu'elle rend. */
    private static void foodRows(ItemStack stack, List<Elem> body) {
        var food = stack.get(net.minecraft.core.component.DataComponents.FOOD);
        if (food == null) return;
        body.add(new RowElem(new Row(Component.literal("+" + food.nutrition() + " ").withStyle(s -> s.withColor(RpgFrame.POSITIVE))
                .append(Component.translatable("tooltip.itemsrarity.rpg.nutrition").withStyle(s -> s.withColor(RpgFrame.TEXT_SOFT))),
                Component.translatable("tooltip.itemsrarity.rpg.saturation", fmt(food.saturation())).withStyle(s -> s.withColor(RpgFrame.TEXT_FAINT)), 0)));
    }

    // ---------------------------------------------------------------- Valeur de base (Dégâts / Armure)

    /** "🛡 5  Armure" ou "⚔ 6 (5 - 7)  Dégâts · 1.6 att/s" sur une seule ligne. */
    private record BaseValue(Component label, String value, String range, boolean armor) {
        int width(Font font) {
            return Math.max(12 + (int) (font.width(value) * 1.5f) + 4 + (range.isEmpty() ? 0 : font.width(range)), 12 + font.width(label));
        }

        /** Hauteur : la valeur, plus la vitesse d'attaque en dessous s'il y en a une. */
        int height() {
            return label.getString().isEmpty() ? 14 : 23;
        }

        void draw(GuiGraphics g, Font font, int x, int y) {
            if (armor) {
                g.blitSprite(net.minecraft.resources.ResourceLocation.withDefaultNamespace("hud/armor_full"), x, y + 2, 9, 9);
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

    private static BaseValue baseValue(ItemStack stack, ItemDefinition def, ItemStats stats) {
        ItemAttributeModifiers mods = stack.getAttributeModifiers();

        // Armure : la défense de la pièce
        if (stack.getItem() instanceof ArmorItem) {
            double armor = sum(mods, Attributes.ARMOR, AttributeModifier.Operation.ADD_VALUE);
            if (armor > 0) {
                return new BaseValue(Component.empty(), fmt(armor), "", true); // l'icône d'armure suffit
            }
        }

        // Arme : la ligne de base "Dégâts" du mod si elle existe, sinon les dégâts vanilla
        String value = null, range = "";
        if (def != null && stats != null) {
            for (String statId : def.stats.keySet()) {
                StatDefinition sd = RarityData.stat(statId);
                if (sd == null || !sd.base || !stats.lines().containsKey(statId)) continue;
                value = String.valueOf(stats.get(statId));
                int[] r = StatEngine.range(def, statId, stats.upgrades());
                if (r != null) range = "(" + sd.formatRange(r[0], r[1]) + ")";
                break;
            }
        } else if (def != null) {
            for (String statId : def.stats.keySet()) {
                StatDefinition sd = RarityData.stat(statId);
                int[] r = def.range(statId);
                if (sd == null || !sd.base || r == null) continue;
                value = sd.formatRange(r[0], r[1]);
                break;
            }
        }
        if (value == null) {
            double dmg = sum(mods, Attributes.ATTACK_DAMAGE, AttributeModifier.Operation.ADD_VALUE);
            if (dmg <= 0) return null;
            value = fmt(1.0 + dmg);
        }
        // L'icône ⚔ suffit pour "Dégâts" : on n'affiche que la vitesse d'attaque à côté.
        MutableComponent label = Component.empty();
        double speed = sum(mods, Attributes.ATTACK_SPEED, AttributeModifier.Operation.ADD_VALUE);
        if (speed != 0) label.append(Component.translatable("tooltip.itemsrarity.rpg.speed", fmt(4.0 + speed)));
        return new BaseValue(label, value, range, false);
    }

    private static double sum(ItemAttributeModifiers mods, Holder<Attribute> attribute, AttributeModifier.Operation op) {
        double total = 0;
        for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
            if (e.attribute().equals(attribute) && e.modifier().operation() == op) total += e.modifier().amount();
        }
        return total;
    }

    private static String fmt(double v) {
        return Math.abs(v - Math.rint(v)) < 0.05 ? String.valueOf((long) Math.rint(v)) : String.format(Locale.ROOT, "%.1f", v);
    }

    /** Attributs vanilla restants (robustesse, recul...) : ni les nôtres, ni dégâts/vitesse/armure (déjà en haut). */
    private static List<Row> vanillaAttributeRows(ItemStack stack) {
        List<Row> rows = new ArrayList<>();
        for (ItemAttributeModifiers.Entry e : stack.getAttributeModifiers().modifiers()) {
            if (AttributeEvents.isOurModifier(e.modifier().id())) continue;
            Holder<Attribute> attr = e.attribute();
            if (attr.equals(Attributes.ATTACK_DAMAGE) || attr.equals(Attributes.ATTACK_SPEED) || attr.equals(Attributes.ARMOR)) continue;
            double amount = e.modifier().amount();
            if (amount == 0) continue;
            String text = switch (e.modifier().operation()) {
                case ADD_VALUE -> (amount > 0 ? "+" : "") + fmt(attr.equals(Attributes.KNOCKBACK_RESISTANCE) ? amount * 10 : amount);
                default -> (amount > 0 ? "+" : "") + fmt(amount * 100) + " %";
            };
            int color = amount > 0 ? RpgFrame.mix(RpgFrame.POSITIVE, 0x8FB8FF, 0.5f) : RpgFrame.NEGATIVE;
            Component left = Component.literal(text + " ").withStyle(s -> s.withColor(color))
                    .append(Component.translatable(attr.value().getDescriptionId()).withStyle(s -> s.withColor(RpgFrame.TEXT_SOFT)));
            rows.add(new Row(left, Component.empty(), 0));
        }
        return rows;
    }

    // ---------------------------------------------------------------- Lignes de stats

    /**
     * L'indice "[Maj]" : il n'élargit jamais l'infobulle, il ne s'affiche que s'il reste la place
     * (reconnu par identité, une seule instance par image).
     */
    private static final ThreadLocal<Component> HINT = ThreadLocal.withInitial(() -> null);

    private static boolean isHint(Component c) {
        return c != null && c == HINT.get();
    }

    /** Maj enfoncée : une courte explication grisée sous la stat (coupée en lignes étroites). */
    private static void addStatHelp(List<Elem> body, Font font, StatDefinition stat) {
        if (stat == null || !net.minecraft.client.gui.screens.Screen.hasShiftDown()) return;
        String key = "stat." + stat.namespace + "." + stat.id + ".desc";
        if (!net.minecraft.locale.Language.getInstance().has(key)) return;
        Component desc = Component.translatable(key);
        for (var part : font.getSplitter().splitLines(desc, 150, net.minecraft.network.chat.Style.EMPTY)) {
            body.add(new RowElem(new Row(Component.literal(part.getString())
                    .withStyle(st -> st.withColor(RpgFrame.TEXT_FAINT).withItalic(true)), Component.empty(), 8)));
        }
    }

    private static Row statRow(String statId, int value, int[] range) {
        StatDefinition stat = RarityData.stat(statId);
        if (stat == null) return new Row(Component.literal(value + " " + statId).withStyle(ChatFormatting.DARK_GRAY), Component.empty(), 0);
        if (!stat.isActive()) {
            return new Row(Component.literal(stat.formatValue(value, true) + " ").append(stat.name())
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.STRIKETHROUGH), Component.empty(), 0);
        }
        // Au-dessus du maximum théorique (forgemagie) : la valeur passe en or
        boolean overmax = range != null && value > range[1];
        int valueColor = overmax ? RpgFrame.OVERMAX : value < 0 ? RpgFrame.NEGATIVE : RpgFrame.POSITIVE;
        MutableComponent left = Component.literal(stat.formatValue(value, true) + " ").withStyle(s -> s.withColor(valueColor))
                .append(stat.name().withStyle(s -> s.withColor(RpgFrame.TEXT_SOFT)));
        if (range != null) {
            // Fourchette juste après la stat (pas collée à droite) : l'infobulle reste étroite.
            left.append(Component.literal(" (" + stat.formatRange(range[0], range[1]) + ")").withStyle(s -> s.withColor(RpgFrame.TEXT_FAINT)));
        }
        return new Row(left, Component.empty(), 0);
    }

    private static Row possibleRow(StatDefinition stat, int[] r) {
        String unit = "percent".equalsIgnoreCase(stat.format) ? " %" : "";
        Component left = Component.literal(stat.formatRange(r[0], r[1]) + unit + " ").withStyle(s -> s.withColor(RpgFrame.TEXT_DIM))
                .append(stat.name().withStyle(s -> s.withColor(RpgFrame.TEXT_DIM)));
        return new Row(left, Component.empty(), 0);
    }

    // ---------------------------------------------------------------- Lignes vanilla / autres mods

    /**
     * Ce qui reste de l'infobulle vanilla une fois retirés : le nom, nos lignes, les enchantements,
     * le bloc des attributs ("Dans la main principale : ...") et la durabilité (affichés autrement).
     */
    private static List<Component> extraLines(List<Component> vanilla, ItemStack stack, Set<String> enchantTexts) {
        Set<String> attributeHeaders = new HashSet<>();
        for (EquipmentSlotGroup group : EquipmentSlotGroup.values()) {
            attributeHeaders.add(Component.translatable("item.modifiers." + group.getSerializedName()).getString());
        }
        String durability = stack.isDamageableItem()
                ? Component.translatable("item.durability", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage()).getString()
                : null;

        List<Component> out = new ArrayList<>();
        boolean inAttributes = false;
        for (int i = 1; i < vanilla.size(); i++) {
            Component c = vanilla.get(i);
            if (TooltipHandler.isOurs(c)) continue;
            String s = c.getString();
            if (attributeHeaders.contains(s)) {
                inAttributes = true;
                while (!out.isEmpty() && out.get(out.size() - 1).getString().isBlank()) out.remove(out.size() - 1);
                continue;
            }
            if (inAttributes) {
                if (s.startsWith(" ")) continue;
                inAttributes = false;
            }
            if (enchantTexts.contains(s) || s.equals(durability)) continue;
            out.add(c);
        }
        while (!out.isEmpty() && out.get(0).getString().isBlank()) out.remove(0);
        while (!out.isEmpty() && out.get(out.size() - 1).getString().isBlank()) out.remove(out.size() - 1);
        return out;
    }

    // ---------------------------------------------------------------- Type d'objet

    static Component typeName(ItemStack stack) {
        Item item = stack.getItem();
        String key;
        if (item instanceof ArmorItem armor) {
            key = switch (armor.getType()) {
                case HELMET -> "helmet";
                case CHESTPLATE -> "chestplate";
                case LEGGINGS -> "leggings";
                case BOOTS -> "boots";
                default -> "armor";
            };
        } else if (item instanceof SwordItem) key = "sword";
        else if (item instanceof AxeItem) key = "axe";
        else if (item instanceof PickaxeItem) key = "pickaxe";
        else if (item instanceof ShovelItem) key = "shovel";
        else if (item instanceof HoeItem) key = "hoe";
        else if (item instanceof MaceItem) key = "mace";
        else if (item instanceof TridentItem) key = "trident";
        else if (item instanceof CrossbowItem) key = "crossbow";
        else if (item instanceof BowItem) key = "bow";
        else if (item instanceof FishingRodItem) key = "fishing_rod";
        else if (item instanceof ShieldItem || stack.canPerformAction(net.neoforged.neoforge.common.ItemAbilities.SHIELD_BLOCK)) key = "shield";
        else if (item instanceof EnchantedBookItem) key = "enchanted_book";
        else if (item instanceof PotionItem) key = "potion";
        else if (item instanceof SpawnEggItem) key = "spawn_egg";
        else if (stack.has(net.minecraft.core.component.DataComponents.FOOD)) key = "food";
        else if (stack.has(net.minecraft.core.component.DataComponents.JUKEBOX_PLAYABLE)) key = "music_disc";
        else if (item instanceof BlockItem) key = "block";
        else if (stack.has(net.minecraft.core.component.DataComponents.TOOL)) key = "tool";
        else if (RarityData.itemDef(stack) != null) {
            String slot = RarityData.itemDef(stack).slot;
            key = slot != null && StatEngine.ACCESSORY_SLOTS.contains(slot) ? slot : "gear";
        }
        else key = "material";
        return Component.translatable("type.itemsrarity." + key);
    }

    // ---------------------------------------------------------------- Conditions d'équipement

    /** "Condition : Armure totale ≤ 8", en vert si le joueur la remplit, en rouge sinon. */
    private static void addConditions(List<Elem> body, @org.jetbrains.annotations.Nullable ItemDefinition def) {
        if (def == null || !def.hasConditions()) return;
        Player player = Minecraft.getInstance().player;
        body.add(new GapElem(3));
        def.conditions.forEach((key, limit) -> {
            boolean ok = player == null || net.Kyap.ItemsRarity.util.Conditions.met(player, key, limit);
            String shown = limit == Math.rint(limit) ? String.valueOf(limit.longValue()) : String.valueOf(limit);
            Component line = Component.literal(ok ? "✔ " : "✖ ").append(Component.translatable("tooltip.itemsrarity.condition." + key, shown))
                    .withStyle(s -> s.withColor(ok ? RpgFrame.POSITIVE : RpgFrame.NEGATIVE));
            body.add(new RowElem(new Row(line, Component.empty(), 0)));
        });
    }

    // ---------------------------------------------------------------- Passifs

    /** Passifs de l'objet, puis ceux de sa panoplie (avec le nombre de pièces requis). */
    private static void addPassives(List<Elem> body, @org.jetbrains.annotations.Nullable ItemDefinition def) {
        if (def == null) return;
        Player player = Minecraft.getInstance().player;
        java.util.Set<String> active = new HashSet<>();
        if (player != null) for (var p : net.Kyap.ItemsRarity.passive.Passives.active(player)) active.add(p.id);
        boolean first = true;
        if (def.passives != null) {
            for (String id : def.passives) {
                var p = RarityData.passive(id);
                if (p == null) continue;
                if (first) body.add(new GapElem(3));
                first = false;
                body.add(passiveElem(p, player, active.contains(id), null));
            }
        }
        if (def.hasSet()) {
            SetDefinition set = RarityData.set(def.set);
            if (set != null && set.passives != null) {
                for (var entry : set.passives.entrySet()) {
                    for (String id : entry.getValue()) {
                        var p = RarityData.passive(id);
                        if (p == null) continue;
                        if (first) body.add(new GapElem(3));
                        first = false;
                        body.add(passiveElem(p, player, active.contains(id), entry.getKey()));
                    }
                }
            }
        }
    }

    private static PassiveElem passiveElem(net.Kyap.ItemsRarity.data.PassiveDefinition p, @org.jetbrains.annotations.Nullable Player player,
                                           boolean on, @org.jetbrains.annotations.Nullable String pieces) {
        int color = on ? 0xF2C14E : 0x9A8A6A;
        MutableComponent name = Component.literal("✧ ").append(p.name()).withStyle(s -> s.withColor(color).withBold(true));
        if (pieces != null) name.append(Component.translatable("tooltip.itemsrarity.passive.pieces", pieces)
                .withStyle(s -> s.withColor(RpgFrame.TEXT_FAINT).withBold(false)));
        Component state;
        int left = net.Kyap.ItemsRarity.passive.Passives.clientCooldownLeft(p.id);
        if (!on) {
            state = Component.translatable("tooltip.itemsrarity.passive.inactive").withStyle(s -> s.withColor(RpgFrame.TEXT_FAINT));
        } else if (left > 0) {
            state = Component.translatable("tooltip.itemsrarity.passive.cooldown", left).withStyle(s -> s.withColor(0xF0A040));
        } else if (player != null && waiting(p, player)) {
            state = Component.translatable("tooltip.itemsrarity.passive.waiting").withStyle(s -> s.withColor(0xE8D25A));
        } else {
            state = Component.translatable("tooltip.itemsrarity.passive.active").withStyle(s -> s.withColor(RpgFrame.POSITIVE));
        }
        Component formula = null;
        if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            StringBuilder sb = new StringBuilder(p.effect);
            p.params.entrySet().forEach(e -> sb.append(" · ").append(e.getKey()).append(' ').append(e.getValue().getAsString()));
            if (p.cooldown > 0) sb.append(" · cooldown ").append((int) p.cooldown).append(" s");
            formula = Component.literal(sb.toString());
        }
        return new PassiveElem(name, state, p.description(), formula, color, on);
    }

    /** Condition du passif pas remplie en ce moment (vie trop haute, hors de l'eau, sous la pluie...). */
    private static boolean waiting(net.Kyap.ItemsRarity.data.PassiveDefinition p, Player player) {
        return switch (p.effect) {
            case "crit_explosion" -> player.getHealth() > p.num("max_health", 2.0);
            case "life_steal" -> p.num("below_health", 0) > 0 && player.getHealth() > player.getMaxHealth() * p.num("below_health", 0)
                    && p.num("ratio", 0) <= 0;
            case "strength_in_water", "healing_in_water" -> !player.isInWater();
            case "dodge" -> p.flag("no_rain") && player.isInWaterOrRain();
            default -> false;
        };
    }
}
