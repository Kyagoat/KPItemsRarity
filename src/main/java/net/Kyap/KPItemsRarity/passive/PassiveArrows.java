package net.Kyap.KPItemsRarity.passive;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Flèches ajoutées par un passif (Salve) : elles ne relancent pas les passifs de tir et ne se ramassent pas. */
public final class PassiveArrows {
    private PassiveArrows() {}

    private static boolean spawning;

    /** Une flèche ajoutée par un passif est en train d'apparaître. */
    static boolean spawning() {
        return spawning;
    }

    /** Une copie de la flèche (même munition, mêmes dégâts, même critique), tournée de "degrees" vers la droite. */
    public static void fireCopy(Player shooter, AbstractArrow original, float degrees) {
        ItemStack ammo = original.getPickupItemStackOrigin();
        if (ammo.isEmpty()) ammo = new ItemStack(Items.ARROW);
        ArrowItem item = ammo.getItem() instanceof ArrowItem arrowItem ? arrowItem : (ArrowItem) Items.ARROW;
        AbstractArrow copy = item.createArrow(original.level(), ammo, shooter, original.getWeaponItem());
        copy.setBaseDamage(original.getBaseDamage());
        copy.setCritArrow(original.isCritArrow());
        copy.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        copy.setPos(original.position());
        Vec3 motion = original.getDeltaMovement().yRot((float) Math.toRadians(-degrees));
        copy.shoot(motion.x, motion.y, motion.z, (float) motion.length(), 0f);
        spawning = true;
        try {
            original.level().addFreshEntity(copy);
        } finally {
            spawning = false;
        }
    }
}
