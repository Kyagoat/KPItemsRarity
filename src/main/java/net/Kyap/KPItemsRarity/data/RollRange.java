package net.Kyap.KPItemsRarity.data;

import net.minecraft.util.RandomSource;

/** La fourchette d'un jet de stat : de {@code min} à {@code max} compris (négative pour un malus). */
public record RollRange(int min, int max) {

    /** Une valeur au hasard dans la fourchette. */
    public int roll(RandomSource random) {
        return min + random.nextInt(max - min + 1);
    }

    /** Où se situe une valeur, de 0 (au minimum) à 1 (au maximum) ; 1 si la fourchette est un seul nombre. */
    public float position(int value) {
        return max == min ? 1f : Math.max(0f, Math.min(1f, (value - min) / (float) (max - min)));
    }

    /** Un cran de rareté de plus : le minimum devient l'ancien maximum, le maximum monte de 10 % (au moins +1). */
    public RollRange upgraded() {
        return new RollRange(max, max + Math.max(1, Math.round(max * 0.10f)));
    }

    /** "5 - 7" (ou un seul nombre), au format de la stat. */
    public String format(StatDefinition stat) {
        return stat.formatRange(min, max);
    }
}
