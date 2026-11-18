package net.Kyap.ItemsRarity.util;

import net.Kyap.ItemsRarity.component.ItemStats;
import net.Kyap.ItemsRarity.component.ModDataComponents;
import net.Kyap.ItemsRarity.data.ItemDefinition;
import net.Kyap.ItemsRarity.data.RarityData;
import net.Kyap.ItemsRarity.data.SetDefinition;
import net.Kyap.ItemsRarity.data.StatDefinition;
import net.Kyap.ItemsRarity.util.ModRarities.ModRarity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.neoforged.neoforge.common.ItemAbilities;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/** Jets, emplacements et panoplies. */
public final class StatEngine {
    private StatEngine() {}

    // ---------------------------------------------------------------- Jets

    /** Nombre maximum de crans de rareté gagnés à la forge. */
    public static final int MAX_UPGRADES = 2;

    /** L'objet a-t-il une définition (donc des stats possibles) ? */
    public static boolean hasDefinition(ItemStack stack) {
        return RarityData.itemDef(stack) != null;
    }

    @Nullable
    public static ItemStats stats(ItemStack stack) {
        return stack.get(ModDataComponents.STATS);
    }

    public static int upgrades(ItemStack stack) {
        ItemStats stats = stats(stack);
        return stats == null ? 0 : stats.upgrades();
    }

    /**
     * Fourchette d'une ligne en tenant compte des crans de rareté gagnés :
     * à chaque cran, le minimum devient l'ancien maximum et le maximum monte de 10 % (au moins +1).
     * Les lignes de base (Dégâts) et les malus ne bougent pas.
     */
    @Nullable
    public static int[] range(ItemDefinition def, String statId, int upgrades) {
        int[] r = def.range(statId);
        if (r == null || upgrades <= 0) return r;
        StatDefinition stat = RarityData.stat(statId);
        if ((stat != null && stat.base) || r[1] <= 0) return r;
        int min = r[0], max = r[1];
        for (int i = 0; i < upgrades; i++) {
            min = max;
            max = max + Math.max(1, Math.round(max * 0.10f));
        }
        return new int[]{min, max};
    }

    @Nullable
    public static int[] range(ItemStack stack, String statId) {
        ItemDefinition def = RarityData.itemDef(stack);
        return def == null ? null : range(def, statId, upgrades(stack));
    }

    /** Donne ses jets à un objet qui n'en a pas encore (craft, drop, coffre, échange...). */
    public static void ensureRolled(ItemStack stack, RandomSource random) {
        if (stack.isEmpty()) return;
        ItemDefinition def = RarityData.itemDef(stack);
        if (def == null) return;
        ItemStats stats = stats(stack);
        // Déjà tiré ET à jour : rien à faire. Si la définition a changé de lignes (rééquilibrage, nouvelle
        // ligne Dégâts...), l'objet est retiré automatiquement, en gardant son exo et ses crans de rareté.
        if (stats != null && stats.lines().keySet().equals(expectedLines(def))) {
            // Objets d'avant la rareté dynamique (ou définition rééquilibrée) : on recalcule la rareté.
            ModRarity expected = rarityFor(def, stats);
            if (stack.get(ModDataComponents.RARITY) != expected) stack.set(ModDataComponents.RARITY, expected);
            return;
        }
        roll(stack, def, random);
    }

    private static java.util.Set<String> expectedLines(ItemDefinition def) {
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (String stat : def.stats.keySet()) if (RarityData.stat(stat) != null && def.range(stat) != null) keys.add(stat);
        return keys;
    }

    /** (Re)tire toutes les lignes dans leurs fourchettes. Garde l'exo et les crans de rareté. */
    public static void roll(ItemStack stack, ItemDefinition def, RandomSource random) {
        int upgrades = upgrades(stack);
        Map<String, Integer> lines = new LinkedHashMap<>();
        def.stats.keySet().forEach(stat -> {
            int[] r = range(def, stat, upgrades);
            // Stat d'un mod absent (ex. "guard" sans KPBlockStamina) : pas de ligne.
            if (r != null && RarityData.stat(stat) != null) lines.put(stat, r[0] + random.nextInt(r[1] - r[0] + 1));
        });
        store(stack, def, lines, upgrades);
    }

    /** Met toutes les lignes au maximum (commande de test). */
    public static void maxOut(ItemStack stack, ItemDefinition def) {
        int upgrades = upgrades(stack);
        Map<String, Integer> lines = new LinkedHashMap<>();
        def.stats.keySet().forEach(stat -> {
            int[] r = range(def, stat, upgrades);
            if (r != null && RarityData.stat(stat) != null) lines.put(stat, r[1]);
        });
        store(stack, def, lines, upgrades);
    }

    /**
     * Monte la rareté d'un cran : chaque ligne passe au maximum de sa fourchette actuelle (sauf si elle
     * le dépasse déjà grâce à la forgemagie), puis la fourchette s'élargit vers le haut.
     * @return false si l'objet est déjà au plafond (2 crans = Mythique).
     */
    public static boolean upgradeRarity(ItemStack stack, RandomSource random) {
        ItemDefinition def = RarityData.itemDef(stack);
        if (def == null) return false;
        ensureRolled(stack, random);
        ItemStats stats = stats(stack);
        int upgrades = stats.upgrades();
        if (upgrades >= MAX_UPGRADES) return false;
        Map<String, Integer> lines = new LinkedHashMap<>(stats.lines());
        for (String stat : def.stats.keySet()) {
            int[] r = range(def, stat, upgrades);
            if (r == null || !lines.containsKey(stat)) continue;
            StatDefinition sd = RarityData.stat(stat);
            if (sd != null && sd.base) continue;
            lines.put(stat, Math.max(lines.get(stat), r[1]));
        }
        store(stack, def, lines, upgrades + 1);
        return true;
    }

    /** Remplace les jets d'un objet (forgemagie) et recalcule sa rareté. */
    public static void setStats(ItemStack stack, ItemDefinition def, ItemStats stats) {
        stack.set(ModDataComponents.STATS, stats);
        stack.set(ModDataComponents.RARITY, rarityFor(def, stats));
    }

    private static void store(ItemStack stack, ItemDefinition def, Map<String, Integer> lines, int upgrades) {
        ItemStats old = stack.get(ModDataComponents.STATS);
        ItemStats stats = new ItemStats(lines,
                old != null ? old.exoStat() : "",
                old != null ? old.exoValue() : 0,
                upgrades,
                old != null ? old.well() : 0);
        stack.set(ModDataComponents.STATS, stats);
        stack.set(ModDataComponents.RARITY, rarityFor(def, stats));
        stack.remove(ModDataComponents.LEGACY_EFFECTS);
    }

    // ---------------------------------------------------------------- Rareté (dynamique)

    /** Seuils de qualité des jets (0 à 1) pour Peu commun, Rare et Épique. */
    public static final float UNCOMMON_AT = 0.30f, RARE_AT = 0.60f, EPIC_AT = 0.85f;

    /**
     * La rareté dépend de CE QUE L'OBJET A TIRÉ, pas seulement de l'objet :
     *   qualité des jets   < 30 % -> Commun
     *                      < 60 % -> Peu commun
     *                      < 85 % -> Rare
     *                      sinon  -> Épique (jet presque parfait)
     *   1 cran de forge           -> Légendaire
     *   2 crans de forge          -> Mythique
     * Le champ "rarity" d'une définition est une rareté MINIMUM (un objet de boss peut être au moins Rare).
     */
    public static ModRarity rarityFor(ItemDefinition def, ItemStats stats) {
        ModRarity fromRoll;
        if (stats.upgrades() >= 2) fromRoll = ModRarity.MYTHIC;
        else if (stats.upgrades() == 1) fromRoll = ModRarity.LEGENDARY;
        else {
            float q = quality(def, stats);
            fromRoll = q >= EPIC_AT ? ModRarity.EPIC : q >= RARE_AT ? ModRarity.RARE : q >= UNCOMMON_AT ? ModRarity.UNCOMMON : ModRarity.COMMON;
        }
        ModRarity floor = def.parsedRarity();
        return fromRoll.ordinal() >= floor.ordinal() ? fromRoll : floor;
    }

    /** Qualité des jets de 0 à 1 (1 = jet parfait), hors lignes de base. */
    public static float quality(ItemStack stack) {
        ItemStats stats = stats(stack);
        ItemDefinition def = RarityData.itemDef(stack);
        return stats == null || def == null ? 0f : quality(def, stats);
    }

    public static float quality(ItemDefinition def, ItemStats stats) {
        float total = 0;
        int count = 0;
        for (String stat : stats.lines().keySet()) {
            StatDefinition sd = RarityData.stat(stat);
            int[] r = range(def, stat, stats.upgrades());
            if (r == null || (sd != null && sd.base)) continue;
            count++;
            // Malus (fourchette négative) : plus proche de 0 = meilleur, la formule reste la même.
            float q = r[1] == r[0] ? 1f : (stats.get(stat) - r[0]) / (float) (r[1] - r[0]);
            total += Math.max(0f, Math.min(1f, q));
        }
        return count == 0 ? 1f : total / count;
    }

    public static ModRarity rarityOf(ItemStack stack) {
        ModRarity r = stack.get(ModDataComponents.RARITY);
        if (r != null) return r;
        ItemDefinition def = RarityData.itemDef(stack);
        return def != null ? def.parsedRarity() : ModRarity.COMMON;
    }

    // ---------------------------------------------------------------- Emplacements

    /** Où l'objet donne ses stats : armure -> son emplacement, bouclier -> main gauche, le reste -> main droite. */
    public static EquipmentSlotGroup slotGroupFor(ItemStack stack) {
        if (stack.getItem() instanceof ArmorItem armor) {
            return EquipmentSlotGroup.bySlot(armor.getEquipmentSlot());
        }
        if (stack.getItem() instanceof ShieldItem || stack.canPerformAction(ItemAbilities.SHIELD_BLOCK)) {
            return EquipmentSlotGroup.OFFHAND;
        }
        return EquipmentSlotGroup.MAINHAND;
    }

    /** Emplacements d'accessoires (portés dans les cases Curios, jamais en main). */
    public static final java.util.Set<String> ACCESSORY_SLOTS = java.util.Set.of("amulet", "ring", "relic");

    /** Amulette, anneau ou relique : ses stats ne comptent que dans une case d'accessoire. */
    public static boolean isAccessory(ItemStack stack) {
        ItemDefinition def = RarityData.itemDef(stack);
        return def != null && def.slot != null && ACCESSORY_SLOTS.contains(def.slot);
    }

    /** Emplacements comptés pour les stats et les panoplies (pas l'armure de cheval/loup). */
    public static final EquipmentSlot[] GEAR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };

    /** L'objet est-il porté au bon endroit pour compter ? */
    public static boolean isWornCorrectly(ItemStack stack, EquipmentSlot slot) {
        return !stack.isEmpty() && !isAccessory(stack) && slotGroupFor(stack).test(slot);
    }

    // ---------------------------------------------------------------- Panoplies

    /** Nombre de pièces portées par panoplie. */
    public static Map<String, Integer> countSetPieces(LivingEntity entity) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (EquipmentSlot slot : GEAR_SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!isWornCorrectly(stack, slot)) continue;
            ItemDefinition def = RarityData.itemDef(stack);
            if (def != null && def.hasSet() && Conditions.met(entity, def)) counts.merge(def.set, 1, Integer::sum);
        }
        // Parures : amulette, anneau, relique portés dans les cases Curios. Un même objet ne compte
        // qu'une fois (deux anneaux identiques = une pièce).
        java.util.Set<net.minecraft.world.item.Item> seen = new java.util.HashSet<>();
        for (ItemStack stack : net.Kyap.ItemsRarity.compat.CuriosCompat.equipped(entity)) {
            ItemDefinition def = RarityData.itemDef(stack);
            if (def == null || !def.hasSet() || !isAccessory(stack) || !Conditions.met(entity, def) || !seen.add(stack.getItem())) continue;
            counts.merge(def.set, 1, Integer::sum);
        }
        return counts;
    }

    /** Total des points de stats portés : jets + exos (équipement et accessoires) + bonus de panoplie actifs. */
    public static Map<String, Integer> totalStats(LivingEntity entity) {
        Map<String, Integer> totals = new LinkedHashMap<>();
        java.util.List<ItemStack> worn = new java.util.ArrayList<>();
        for (EquipmentSlot slot : GEAR_SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (isWornCorrectly(stack, slot)) worn.add(stack);
        }
        for (ItemStack stack : net.Kyap.ItemsRarity.compat.CuriosCompat.equipped(entity)) {
            if (isAccessory(stack)) worn.add(stack);
        }
        for (ItemStack stack : worn) {
            ItemStats stats = stats(stack);
            if (stats == null || !Conditions.met(entity, RarityData.itemDef(stack))) continue;
            stats.lines().forEach((stat, value) -> {
                StatDefinition sd = RarityData.stat(stat);
                if (sd == null || !sd.base) totals.merge(stat, value, Integer::sum);
            });
            if (stats.hasExo()) totals.merge(stats.exoStat(), stats.exoValue(), Integer::sum);
        }
        countSetPieces(entity).forEach((setId, count) -> {
            SetDefinition set = RarityData.set(setId);
            if (set != null) set.activeBonus(count).forEach((stat, value) -> totals.merge(stat, value, Integer::sum));
        });
        return totals;
    }

    public static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
