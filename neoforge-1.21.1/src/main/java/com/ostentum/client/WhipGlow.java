package com.ostentum.client;

import com.fundamentum.whip.WhipShaders;
import com.fundamentum.whip.WhipStyle;
import com.fundamentum.whip.WhipsClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ostentum.Ostentum;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The glowing parts of two lashes, drawn over Fundamentum's ribbon as a second, full-bright ribbon of the same shape in
 * a glow texture (only the glowing pixels are opaque): the Glow Berry Whip's berries, the Ender Whip's flecks and tip.
 * The Glow Berry Whip also hangs 3D berries off its cord (1.8.1), unlit and with a soft glow round each through
 * Fundamentum's unlit and halo render types (1.8.2: full-bright light alone still went through the lightmap and showed
 * no glow).
 * Client only; registered from {@link OstentumClient}.
 */
final class WhipGlow implements WhipStyle {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("fundamentum", "textures/misc/white.png");
    /** A berry's top, sides and underside: lit from above, full bright. */
    private static final int BERRY_TOP = 0xFFFFC77A, BERRY_SIDE = 0xFFF19645, BERRY_UNDER = 0xFFC8682A;
    /** The soft glow round each berry (additive; alpha is its strength). */
    private static final int BERRY_GLOW = 0x90FFB040;

    private final ResourceLocation texture;
    private final boolean berries;

    private WhipGlow(ResourceLocation texture, boolean berries) {
        this.texture = texture;
        this.berries = berries;
    }

    static void register() {
        for (String whip : new String[] {"glow_berry_whip", "ender_whip"}) {
            WhipsClient.style(Ostentum.id(whip), new WhipGlow(Ostentum.id("textures/entity/whip/" + whip + "_glow.png"),
                whip.equals("glow_berry_whip")));
        }
    }

    @Override
    public void draw(Cord cord, PoseStack pose, MultiBufferSource buffers) {
        Geom.draw(pose, buffers, RenderType.eyes(texture), (at, buffer) -> ribbon(at, buffer, cord));
        if (berries) {
            Geom.draw(pose, buffers, WhipShaders.unlit(WHITE, false), (at, buffer) -> berries(at, buffer, cord));
            Geom.draw(pose, buffers, WhipShaders.halo(), (at, buffer) -> {
                for (Vec3 c : berryCentres(cord)) {
                    WhipsClient.glowDot(at, buffer, c, 0.16, BERRY_GLOW);
                }
            });
        }
    }

    /**
     * The Glow Berry Whip's berries as little full-bright cubes hung off the cord, every fifth point on alternate sides
     * (the painted ones are a pixel wide on the ribbon and do not show).
     */
    private static void berries(PoseStack.Pose at, VertexConsumer buffer, Cord cord) {
        java.util.List<Vec3> centres = berryCentres(cord);
        for (int k = 0; k < centres.size(); k++) {
            cube(at, buffer, centres.get(k), half(cord, 3 + k * every(cord)));
        }
    }

    /** Where the berries hang: every fifth point (eighth when idle) from the fourth, on alternate sides, camera-relative. */
    private static java.util.List<Vec3> berryCentres(Cord cord) {
        Vec3[] p = cord.points();
        java.util.List<Vec3> out = new java.util.ArrayList<>();
        for (int i = 3, side = 1; i < p.length; i += every(cord), side = -side) {
            Vec3 across = cord.direction(i).cross(new Vec3(0, 1, 0));
            across = across.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : across.normalize();
            double half = half(cord, i);
            out.add(p[i].add(across.scale(side * (half + 0.03))).add(0, -half, 0));
        }
        return out;
    }

    private static int every(Cord cord) {
        return cord.swinging() ? 5 : 8;
    }

    private static double half(Cord cord, int i) {
        return 0.04 - 0.012 * cord.along(i);
    }

    private static void cube(PoseStack.Pose at, VertexConsumer buffer, Vec3 c, double h) {
        double x0 = c.x - h, x1 = c.x + h, y0 = c.y - h, y1 = c.y + h, z0 = c.z - h, z1 = c.z + h;
        face(at, buffer, BERRY_TOP, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        face(at, buffer, BERRY_UNDER, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        face(at, buffer, BERRY_SIDE, 0, 0, -1, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        face(at, buffer, BERRY_SIDE, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        face(at, buffer, BERRY_SIDE, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        face(at, buffer, BERRY_SIDE, 1, 0, 0, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    /** One face, in both windings so a culled render type shows it from either side. */
    private static void face(PoseStack.Pose at, VertexConsumer buffer, int color, float nx, float ny, float nz, double... v) {
        for (int pass = 0; pass < 2; pass++) {
            for (int k = 0; k < 4; k++) {
                int j = (pass == 0 ? k : 3 - k) * 3;
                buffer.addVertex(at, (float) v[j], (float) v[j + 1], (float) v[j + 2]).setColor(color).setUv(0.5F, 0.5F)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(at, nx, ny, nz);
            }
        }
    }

    /** The same camera-facing strip Fundamentum draws: u across, v from handle to tip, tapering by the whip's widths. */
    private static void ribbon(PoseStack.Pose at, VertexConsumer buffer, Cord cord) {
        Vec3[] p = cord.points();
        int n = p.length;
        Vec3[] left = new Vec3[n], right = new Vec3[n];
        for (int i = 0; i < n; i++) {
            Vec3 tangent = p[Math.min(n - 1, i + 1)].subtract(p[Math.max(0, i - 1)]);
            Vec3 side = tangent.cross(p[i].scale(-1));
            side = side.lengthSqr() < 1.0E-10 ? new Vec3(0, 1, 0) : side.normalize();
            double half = Mth.lerp(cord.along(i), cord.whip().width, cord.whip().tipWidth) * 0.5;
            // A hair nearer the camera than Fundamentum's cord, so the glow never z-fights it.
            Vec3 at0 = p[i].scale(0.995);
            left[i] = at0.add(side.scale(half));
            right[i] = at0.subtract(side.scale(half));
        }
        for (int i = 0; i < n - 1; i++) {
            float v0 = (float) cord.along(i), v1 = (float) cord.along(i + 1);
            vertex(at, buffer, left[i], 0, v0);
            vertex(at, buffer, right[i], 1, v0);
            vertex(at, buffer, right[i + 1], 1, v1);
            vertex(at, buffer, left[i + 1], 0, v1);
        }
    }

    private static void vertex(PoseStack.Pose at, VertexConsumer buffer, Vec3 p, float u, float v) {
        buffer.addVertex(at, (float) p.x, (float) p.y, (float) p.z).setColor(0xFFFFFFFF).setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(at, 0, 1, 0);
    }
}
