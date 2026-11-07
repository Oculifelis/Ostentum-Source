package com.ostentum.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ostentum.Ostentum;
import com.ostentum.mixin.ItemDisplayAccess;
import com.ostentum.weapon.PathfindersGlaive;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
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
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * The Pathfinder's Glaive's light column:
 * vanilla's beacon beam in the glaive's gold from the planted glaive up as far as a beacon's goes. It is drawn for every
 * planted glaive the client can see and, at any range, for every waypoint on a glaive in the player's own inventory,
 * whether or not its chunk is loaded. A beam past the render distance is drawn scaled in toward the camera, which looks
 * the same but stays inside the far plane and the fog. Vanilla's beacon render types, so Iris draws it as a beacon beam.
 */
@EventBusSubscriber(modid = Ostentum.MODID, value = Dist.CLIENT)
public final class GlaiveBeam {
    static final int COLOR = 0xFFE0B040;

    private GlaiveBeam() {}

    @SubscribeEvent
    public static void draw(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
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
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        double near = Math.max(24.0, mc.options.getEffectiveRenderDistance() * 16.0 * 0.7);
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
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
            BeaconRenderer.renderBeaconBeam(pose, buffers, BeaconRenderer.BEAM_LOCATION, pt, 1.0F, level.getGameTime(), 0,
                BeaconRenderer.MAX_RENDER_Y, COLOR, 0.2F * radius, 0.25F * radius);
            pose.popPose();
        }
        buffers.endBatch();
    }
}
