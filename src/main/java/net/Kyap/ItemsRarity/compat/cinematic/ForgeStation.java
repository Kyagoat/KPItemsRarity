package net.Kyap.ItemsRarity.compat.cinematic;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.dyslexic.cinematic.api.client.AddonMenuStation;
import dev.dyslexic.cinematic.client.Anim;
import dev.dyslexic.cinematic.client.CameraRig;
import dev.dyslexic.cinematic.client.Fx;
import dev.dyslexic.cinematic.client.ItemPlacer;
import dev.dyslexic.cinematic.client.Picking;
import net.Kyap.ItemsRarity.client.ForgeOverlay;
import net.Kyap.ItemsRarity.item.ModItems;
import net.Kyap.ItemsRarity.screen.EnhancedAnvilBlockMenu;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * La table de forgemagie dans Cinematic Interactions.
 *
 *   - l'objet à forger est posé au centre de la table, la pile de runes (ou la pierre / la ressource) à gauche ;
 *   - on prend et on pose avec la main, comme sur les postes du mod ;
 *   - CLIC sur l'objet avec des runes à côté : un coup de marteau = une rune (la pile reste, plus besoin
 *     de les poser une par une) ;
 *   - MAJ + CLIC DROIT sur l'objet : le briser en runes ;
 *   - à l'écran, un panneau semi-transparent façon Dofus (ForgeOverlay) : les lignes de l'objet et, pour
 *     chacune, trois cases de rune (rune, Pa, Ra) prises dans l'inventaire. Clic : le personnage pose la
 *     rune sur l'objet et frappe ; Maj + clic : recommence jusqu'à réussir. Plus besoin de poser les runes.
 *
 * Les valeurs de placement (distances en blocs) sont des premiers réglages, à ajuster en jeu.
 */
public final class ForgeStation extends AddonMenuStation<EnhancedAnvilBlockMenu> {

    /** Cases du menu (dans l'ordre du menu) : 0 = rune, 1 = objet. */
    private static final int RUNE = 0, GEAR = 1;
    private static final double PICK_RADIUS = 0.2;
    /** Coup de marteau : durée totale et instant de l'impact (secondes). */
    private static final double STRIKE_LENGTH = 0.45, STRIKE_IMPACT = 0.2;

    private enum Target { NONE, RUNE, GEAR }

    public final Vec3 top;
    private Target target = Target.NONE;
    private double strikeAt = -10;

    public ForgeStation(BlockPos pos, Direction side) {
        super(pos, side, EnhancedAnvilBlockMenu.class, 2);
        this.top = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
    }

    // ---------------------------------------------------------------- Placement

    @Override
    protected Vec3 restPoint(int slot) {
        // Objet au centre, un peu vers le fond ; runes à gauche, vers le joueur
        return slot == RUNE ? top.add(right().scale(-0.24)).add(side().scale(0.12)).add(0, 0.004, 0)
                : top.add(side().scale(-0.04)).add(0, 0.004, 0);
    }

    @Override
    public List<Vec3> approach(LocalPlayer player) {
        Vec3 stand = new Vec3(top.x + side.getStepX() * 0.86, player.getY(), top.z + side.getStepZ() * 0.86);
        return Math.hypot(stand.x - player.getX(), stand.z - player.getZ()) > 0.06 ? List.of(stand) : List.of();
    }

    @Override
    public CameraRig.Pose lean() {
        Vec3 eye = top.add(side().scale(0.7)).add(0, 0.62, 0);
        float pitch = (float) Math.toDegrees(Math.atan2(0.62, 0.74));
        return new CameraRig.Pose(eye, side.getOpposite().toYRot(), pitch, 0f);
    }

    @Override
    public Vec3 rest(boolean rightHand) {
        return top.add(side().scale(0.4)).add(right().scale(rightHand ? 0.25 : -0.25)).add(0, 0.062, 0);
    }

    /** Pendant un coup : la main du personnage suit le marteau (levé, abattu sur l'objet, rebond). */
    private Vec3 hammerPoint(double t) {
        double h;
        if (t < 0.15) h = 0.34 * Anim.outCubic(t / 0.15);                                   // lever
        else if (t < STRIKE_IMPACT) h = 0.34 * (1 - Anim.inCubic((t - 0.15) / (STRIKE_IMPACT - 0.15))); // abattre
        else h = 0.08 * Anim.hump(Anim.clamp01((t - STRIKE_IMPACT) / (STRIKE_LENGTH - STRIKE_IMPACT))); // rebond
        return restPoint(GEAR).add(right().scale(0.06)).add(side().scale(0.05)).add(0, 0.07 + h, 0);
    }

    private double strikeTime() {
        return dev.dyslexic.cinematic.client.Scene.clock() - strikeAt;
    }

    @Override
    public Vec3 handTarget(Vec3 point, Vec3 shoulder, boolean holding, double press) {
        double t = strikeTime();
        if (t >= 0 && t < STRIKE_LENGTH) return hammerPoint(t).add(0, 0.05, 0);
        Vec3 dir = new Vec3(point.x - shoulder.x, 0, point.z - shoulder.z).normalize();
        double lift = holding ? 0.075 : 0.045;
        return point.add(0, 0.062 + lift - 0.06 * press, 0).subtract(dir.scale(0.075));
    }

    @Override
    public Vec3 heldItemPoint(Vec3 hand, Vec3 shoulder) {
        Vec3 dir = new Vec3(hand.x - shoulder.x, 0, hand.z - shoulder.z).normalize();
        return hand.add(dir.scale(0.075)).add(0, -0.05, 0);
    }

    // ---------------------------------------------------------------- Curseur et clics

    @Override
    public void clearCursor() {
        super.clearCursor();
        target = Target.NONE;
    }

    @Override
    public void updateCursor(boolean blocked) {
        clearCursor();
        if (blocked || menu() == null || moving()) return;
        double[] mouse = Picking.mouseGui();
        if (mouse != null) for (ForgeOverlay.RuneSlot slot : panelSlots) if (slot.contains(mouse[0], mouse[1])) return;
        Vec3 hit = Picking.cursorOnPlane(top.y);
        if (hit == null) return;
        double toRune = hit.distanceTo(restPoint(RUNE)), toGear = hit.distanceTo(restPoint(GEAR));
        if (Math.min(toRune, toGear) > PICK_RADIUS) return;
        target = toRune < toGear ? Target.RUNE : Target.GEAR;
        aim = restPoint(target == Target.RUNE ? RUNE : GEAR);
    }

    /** Cases de rune du panneau, dessinées à la dernière image (coordonnées écran). */
    private List<ForgeOverlay.RuneSlot> panelSlots = List.of();
    /** Rune posée sur la table le temps du coup de marteau (choisie dans le panneau). */
    private ItemStack flyingRune = ItemStack.EMPTY;

    @Override
    protected void act(int button, boolean shift) {
        EnhancedAnvilBlockMenu menu = menu();
        if (menu == null) return;
        // Clic dans le panneau : le personnage pose la rune choisie et frappe
        double[] mouse = Picking.mouseGui();
        if (mouse != null) {
            for (ForgeOverlay.RuneSlot slot : panelSlots) {
                if (!slot.contains(mouse[0], mouse[1])) continue;
                if (slot.count() <= 0 || slot(GEAR).isEmpty()) deny();
                else strike(EnhancedAnvilBlockMenu.lineButton(slot.line(), slot.size(), shift),
                        net.Kyap.ItemsRarity.item.custom.RuneItem.of(slot.rune().stat(), slot.rune().power(), 1));
                return;
            }
        }
        if (target == Target.NONE) return;
        ItemStack carried = menu.getCarried();
        int slot = target == Target.RUNE ? RUNE : GEAR;
        // Main pleine : on pose (si la case accepte)
        if (!carried.isEmpty()) {
            if (slotFor(carried, slot) == slot) slotClick(slot, button, false);
            else deny();
            return;
        }
        if (target == Target.GEAR && !slot(GEAR).isEmpty()) {
            if (button == 1 && shift) {
                press(EnhancedAnvilBlockMenu.BUTTON_BREAK);
                return;
            }
            if (button == 0 && !slot(RUNE).isEmpty()) {
                strike(EnhancedAnvilBlockMenu.BUTTON_FORGE, ItemStack.EMPTY);
                return;
            }
        }
        // Sinon : reprendre l'objet / la pile
        if (!slot(slot).isEmpty()) slotClick(slot, button, shift);
    }

    /** Un coup de marteau : l'animation, puis à l'impact la rune part au serveur (bouton "Forger"). */
    private void strike(int buttonId, ItemStack rune) {
        strikeAt = dev.dyslexic.cinematic.client.Scene.clock();
        flyingRune = rune;
        startMove(STRIKE_LENGTH, STRIKE_IMPACT, () -> {
            press(buttonId);
            Vec3 at = restPoint(GEAR).add(0, 0.03, 0);
            Fx.blowSparks(at, 10, 0.5);
            var level = Minecraft.getInstance().level;
            if (level != null) level.playLocalSound(at.x, at.y, at.z, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6f, 1.2f, false);
        });
    }

    private void press(int id) {
        Minecraft mc = Minecraft.getInstance();
        EnhancedAnvilBlockMenu menu = menu();
        if (menu != null && mc.gameMode != null) mc.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    public boolean busy() {
        return moving() || super.busy();
    }

    // ---------------------------------------------------------------- Rendu

    @Override
    public void renderScene(PoseStack pose, MultiBufferSource buffers, Camera camera, int light) {
        Vec3 cam = camera.getPosition();
        float yaw = ItemPlacer.awayYaw(side);
        // La pile de runes : quelques runes empilées, décalées
        ItemStack runes = slot(RUNE);
        if (!runes.isEmpty()) {
            int shown = Math.min(5, runes.getCount());
            for (int i = 0; i < shown; i++) {
                Vec3 p = restPoint(RUNE).add(0.012 * (i % 2), 0.006 * i, -0.01 * (i % 3));
                ItemPlacer.drawOriented(pose, buffers, cam, runes.copyWithCount(1), p, ItemPlacer.lying(yaw + i * 17f, 0f), 0.22f, light);
            }
        }
        // L'objet, qui tressaille au coup de marteau
        ItemStack gear = slot(GEAR);
        double t = dev.dyslexic.cinematic.client.Scene.clock() - strikeAt;
        double jolt = t >= STRIKE_IMPACT && t < STRIKE_IMPACT + 0.12 ? Anim.hump((t - STRIKE_IMPACT) / 0.12) * 0.015 : 0;
        if (!gear.isEmpty()) {
            ItemPlacer.drawOriented(pose, buffers, cam, gear, restPoint(GEAR).add(0, jolt, 0), ItemPlacer.lying(yaw, 0f), 0.36f, light);
        }
        // La rune choisie dans le panneau : elle glisse de la main jusqu'à l'objet avant le coup
        if (!flyingRune.isEmpty() && t >= 0 && t < STRIKE_IMPACT + 0.05) {
            double k = Anim.outCubic(Anim.clamp01(t / (STRIKE_IMPACT * 0.8)));
            Vec3 from = rest(false).add(0, 0.05, 0), to = restPoint(GEAR).add(0, 0.025, 0);
            ItemPlacer.drawOriented(pose, buffers, cam, flyingRune, Anim.lerp(from, to, k).add(0, 0.06 * Anim.hump(k), 0),
                    ItemPlacer.lying(yaw + 30f, 0f), 0.2f, light);
        }
        // Le marteau d'émeraude, dans la main du personnage : tête vers l'objet, il suit le geste
        if (t >= 0 && t < STRIKE_LENGTH) {
            Vec3 at = hammerPoint(t);
            float tilt = (float) (t < STRIKE_IMPACT ? 40 + 50 * Anim.clamp01(t / STRIKE_IMPACT) : 85);
            ItemPlacer.drawOriented(pose, buffers, cam, new ItemStack(ModItems.EMERALD_HAMMER.get()), at,
                    ItemPlacer.lying(yaw + 90f, tilt), 0.42f, light);
        }
    }

    @Override
    public void renderWorld(PoseStack pose, MultiBufferSource buffers, Vec3 camera, int light) {
        // Hors scène, la table affiche déjà ses objets (EnhancedBlockEntityRenderer)
    }

    @Override
    public void renderScreen(GuiGraphics g, Font font, float partialTick) {
        super.renderScreen(g, font, partialTick);
        var player = Minecraft.getInstance().player;
        double[] mouse = Picking.mouseGui();
        if (player != null) panelSlots = ForgeOverlay.render(g, font, slot(GEAR), slot(RUNE), player.getInventory(),
                mouse != null ? mouse[0] : -1, mouse != null ? mouse[1] : -1);
    }

    @Override
    public Component hint() {
        return Component.translatable("forge.itemsrarity.cinematic_hint");
    }
}
