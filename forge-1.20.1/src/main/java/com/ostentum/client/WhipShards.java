package com.ostentum.client;

import com.fundamentum.whip.WhipStyle;
import com.fundamentum.whip.WhipsClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ostentum.Ostentum;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * The Amethyst Whip's crystal spikes: a four-sided amethyst shard on every other point of the cord, leaning toward the
 * tip and turning round the cord from one to the next, longest near the handle. The painted shards on the ribbon are
 * too thin to read at the cord's width, so these stand out of it. Client only; registered from {@link OstentumClient}.
 */
final class WhipShards implements WhipStyle {
    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/block/amethyst_block.png");
    /** The turn round the cord from one shard to the next (the golden angle, so they never line up). */
    private static final double TURN = 2.39996;

    static void register() {
        WhipsClient.style(Ostentum.id("amethyst_whip"), new WhipShards());
    }

    @Override
    public void draw(Cord cord, PoseStack pose, MultiBufferSource buffers) {
        Geom.draw(pose, buffers, RenderType.entityCutout(TEXTURE), (at, buffer) -> shards(at, buffer, cord));
    }

    private static void shards(PoseStack.Pose at, VertexConsumer buffer, Cord cord) {
        Vec3[] p = cord.points();
        // Hanging or coiled between swings the cord's points sit close together, so fewer and smaller shards.
        int every = cord.swinging() ? 2 : 4;
        double scale = cord.swinging() ? 1.0 : 0.6;
        for (int i = 2; i < p.length - 1; i += every) {
            double along = cord.along(i);
            Vec3 dir = cord.direction(i);
            Vec3 a = dir.cross(Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
            Vec3 b = dir.cross(a);
            double turn = i * TURN;
            Vec3 out = a.scale(Math.cos(turn)).add(b.scale(Math.sin(turn)));
            double length = (0.22 - 0.12 * along) * scale, radius = (0.045 - 0.02 * along) * scale;
            // Out from the cord and back toward the tip, like a barb.
            Vec3 tip = p[i].add(out.scale(length * 0.8)).add(dir.scale(length * 0.6));
            Vec3 side = out.cross(dir).normalize();
            Vec3[] base = {p[i].add(dir.scale(radius)), p[i].add(side.scale(radius)), p[i].add(dir.scale(-radius)), p[i].add(side.scale(-radius))};
            int light = cord.light()[i];
            float u = (float) ((i * 0.37) % 0.75);
            for (int k = 0; k < 4; k++) {
                Vec3 c = base[k], d = base[(k + 1) % 4];
                Vec3 normal = d.subtract(c).cross(tip.subtract(c)).normalize();
                // Both windings, so a culled render type still shows every face.
                vertex(at, buffer, c, u, 1, light, normal);
                vertex(at, buffer, d, u + 0.25F, 1, light, normal);
                vertex(at, buffer, tip, u + 0.125F, 0, light, normal);
                vertex(at, buffer, tip, u + 0.125F, 0, light, normal);
                vertex(at, buffer, tip, u + 0.125F, 0, light, normal);
                vertex(at, buffer, tip, u + 0.125F, 0, light, normal);
                vertex(at, buffer, d, u + 0.25F, 1, light, normal);
                vertex(at, buffer, c, u, 1, light, normal);
            }
        }
    }

    private static void vertex(PoseStack.Pose at, VertexConsumer buffer, Vec3 p, float u, float v, int light, Vec3 n) {
        buffer.vertex(at.pose(), (float) p.x, (float) p.y, (float) p.z).color(0xFFFFFFFF).uv(u, v * 0.5F)
            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(at.normal(), (float) n.x, (float) n.y, (float) n.z).endVertex();
    }
}
