package com.ostentum.client;

import com.fundamentum.whip.WhipStyle;
import com.fundamentum.whip.WhipsClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ostentum.Ostentum;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Shader showcase (1.8.3): the End Rift Whip's cord is drawn through vanilla's End portal render type, whose starfield
 * is laid out in screen space, so the cord reads as a slit into the End with the stars standing still behind it as it
 * moves. No halo (1.8.4: an additive rim at the same depth flickered against the portal). Client only; registered from {@link OstentumClient}.
 */
final class EndRiftStyle implements WhipStyle {
    static void register() {
        WhipsClient.style(Ostentum.id("end_rift_whip"), new EndRiftStyle());
    }

    @Override
    public boolean ribbon() {
        return false;
    }

    @Override
    public void draw(Cord cord, PoseStack pose, MultiBufferSource buffers) {
        Geom.draw(pose, buffers, RenderType.endPortal(), (at, buffer) -> strip(at, buffer, cord));
    }

    /** A camera-facing strip at the whip's widths, in both windings so it shows whichever way the portal type culls. */
    private static void strip(PoseStack.Pose at, VertexConsumer buffer, Cord cord) {
        Vec3[] p = cord.points();
        int n = p.length;
        Vec3[] left = new Vec3[n], right = new Vec3[n];
        for (int i = 0; i < n; i++) {
            Vec3 tangent = p[Math.min(n - 1, i + 1)].subtract(p[Math.max(0, i - 1)]);
            Vec3 side = tangent.cross(p[i].scale(-1));
            side = side.lengthSqr() < 1.0E-10 ? new Vec3(0, 1, 0) : side.normalize();
            double half = Mth.lerp(cord.along(i), cord.whip().width, cord.whip().tipWidth) * 0.5;
            left[i] = p[i].add(side.scale(half));
            right[i] = p[i].subtract(side.scale(half));
        }
        for (int i = 0; i < n - 1; i++) {
            Vec3[] q = {left[i], right[i], right[i + 1], left[i + 1]};
            for (int k = 0; k < 4; k++) {
                vertex(at, buffer, q[k]);
            }
            for (int k = 3; k >= 0; k--) {
                vertex(at, buffer, q[k]);
            }
        }
    }

    private static void vertex(PoseStack.Pose at, VertexConsumer buffer, Vec3 v) {
        buffer.addVertex(at, (float) v.x, (float) v.y, (float) v.z);
    }
}
