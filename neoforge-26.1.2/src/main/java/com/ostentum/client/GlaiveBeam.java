package com.ostentum.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ostentum.Ostentum;
import com.ostentum.mixin.ItemDisplayAccess;
import com.ostentum.weapon.PathfindersGlaive;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * The Pathfinder's Glaive's light column: a beacon beam in the glaive's
 * gold from the planted glaive up into the sky as far as a beacon's goes,
 * past the top of the world. It is drawn for every planted glaive the client can see and,
 * at any range, for every waypoint on a glaive in the player's own inventory, whether or not its chunk is loaded.
 * A beam past the render distance is drawn scaled in toward the camera, which looks the same (a uniform scale about
 * the eye leaves the picture unchanged) but stays inside the far plane and the fog. Vanilla's beacon render types, so
 * shader packs (Iris) draw it as they draw beacon beams.
 */
@EventBusSubscriber(modid = Ostentum.MODID, value = Dist.CLIENT)
public final class GlaiveBeam {
    static final int COLOR = 0xFFE0B040;

    private GlaiveBeam() {}

    @SubscribeEvent
    public static void draw(SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return;
        }
        Set<BlockPos> beams = new LinkedHashSet<>();
        Inventory inventory = mc.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(PathfindersGlaive.GLAIVE.get())) {
                BlockPos at = PathfindersGlaive.waypoint(stack, level);
                if (at != null) {
                    beams.add(at);
                }
            }
        }
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof Display.ItemDisplay display && ((ItemDisplayAccess) display).ostentum$getItemStack().is(PathfindersGlaive.GLAIVE.get())) {
                beams.add(display.blockPosition());
            }
        }
        if (beams.isEmpty()) {
            return;
        }
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float time = Math.floorMod(level.getGameTime(), 40) + pt;
        Vec3 cam = event.getLevelRenderState().cameraRenderState.pos;
        double near = Math.max(24.0, mc.options.getEffectiveRenderDistance() * 16.0 * 0.7);
        PoseStack pose = event.getPoseStack();
        for (BlockPos at : beams) {
            int bottom = at.getY() + 1;
            double dx = at.getX() + 0.5 - cam.x, dz = at.getZ() + 0.5 - cam.z;
            double far = Math.sqrt(dx * dx + dz * dz);
            float radius = Math.max(1.0F, (float) far / 96.0F);
            pose.pushPose();
            if (far > near) {
                float k = (float) (near / far);
                pose.scale(k, k, k);
            }
            pose.translate(at.getX() - cam.x, bottom - cam.y, at.getZ() - cam.z);
            BeaconRenderer.submitBeaconBeam(pose, event.getSubmitNodeCollector(), BeaconRenderer.BEAM_LOCATION, 1.0F, time, 0,
                BeaconRenderer.MAX_RENDER_Y, COLOR, BeaconRenderer.SOLID_BEAM_RADIUS * radius, BeaconRenderer.BEAM_GLOW_RADIUS * radius);
            pose.popPose();
        }
    }
}
