package com.ostentum.client;

import com.ostentum.Ostentum;
import com.ostentum.mixin.ItemDisplayAccess;
import com.ostentum.weapon.PathfindersGlaive;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
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
 * The Pathfinder's Glaive's light column, made the Ultima Ratio way: a gold beam with a white-hot core rising from the
 * planted glaive ({@link Bolts#column}), added on vanilla's lightning render type over a blended gold tint (so it stays gold on a bright sky), full bright, as the Weathervane Spear's bolts.
 * It is drawn for every planted glaive the client can see and, at any range, for every waypoint on a glaive in the
 * player's own inventory, whether or not its chunk is loaded. A column past the render distance is drawn scaled in toward
 * the camera, which looks the same but stays inside the far plane and the fog.
 */
@EventBusSubscriber(modid = Ostentum.MODID, value = Dist.CLIENT)
public final class GlaiveBeam {
    static final double HEIGHT = 1024.0;

    private GlaiveBeam() {}

    @SubscribeEvent
    public static void draw(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
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
        float time = Math.floorMod(level.getGameTime(), 24000L) + pt;
        Vec3 cam = event.getCamera().getPosition();
        int rd = mc.options.getEffectiveRenderDistance();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        // the gold blended over the sky first, then the light added on top
        column(beams, cam, rd, time, true).emit(event.getPoseStack().last(), buffers.getBuffer(RenderType.debugQuads()));
        buffers.endBatch(RenderType.debugQuads());
        column(beams, cam, rd, time, false).emit(event.getPoseStack().last(), buffers.getBuffer(RenderType.lightning()));
        buffers.endBatch(RenderType.lightning());
    }

    /** Every column (or its gold tint), each a little wider the farther it is so it still reads from far off. */
    static Bolts column(Set<BlockPos> beams, Vec3 cam, int renderDistance, float time, boolean tint) {
        double near = Math.max(24.0, renderDistance * 16.0 * 0.7);
        Bolts mesh = new Bolts(cam);
        for (BlockPos at : beams) {
            Vec3 base = new Vec3(at.getX() + 0.5, at.getY() + 0.05, at.getZ() + 0.5);
            double dx = base.x - cam.x, dz = base.z - cam.z;
            double far = Math.sqrt(dx * dx + dz * dz);
            float radius = Math.max(1.0F, (float) far / 96.0F);
            float k = far > near ? (float) (near / far) : 1.0F;
            if (tint) {
                mesh.columnTint(base, HEIGHT, 0.18F * radius, k, time);
            } else {
                mesh.column(base, HEIGHT, 0.18F * radius, k, time);
            }
        }
        return mesh;
    }
}
