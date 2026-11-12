package com.ostentum.client;

import com.fundamentum.whip.WhipShaders;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;
import com.ostentum.Ostentum;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * The Blaze Whip's four floating rods (1.8.1), drawn over its 3D held handle and moved every frame like a blaze's: they
 * circle the grip slowly, each bobs up and down on its own phase and turns on its long axis, unlit (Fundamentum's unlit
 * render type, 1.8.2) so they glow like a blaze's. The item
 * definition draws {@code blaze_whip_held} and this over it ({@code "type": "ostentum:blaze_rods"}); the boxes match
 * tools/whip/handle3d.py's (1 x 4 px rods, 2.9 px out from the grip's axis, the handle turned -45 degrees about Z onto
 * the stick diagonal and lowered by its SHIFT).
 */
final class BlazeRodsRenderer implements NoDataSpecialModelRenderer {
    private static final Identifier SPRITE = Ostentum.id("item/blaze_whip_handle");
    /** Each rod's middle along the handle, in model pixels (handle3d.py's ranges, lowered by its SHIFT of -3.36). */
    private static final float[] MIDDLE = {4.64F, 4.14F, 5.14F, 3.64F};
    private static final float RADIUS = 2.9F;
    /** Seconds per turn round the grip, per bob, per turn of a rod on its own axis. */
    private static final double ORBIT = 6.0, BOB = 1.5, SPIN = 2.5;

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, int overlay, boolean foil, int outline) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.ITEMS).getSprite(SPRITE);
        double t = System.nanoTime() / 1.0E9;
        collector.submitCustomGeometry(pose, WhipShaders.unlit(sprite.atlasLocation(), false), (p, buffer) -> {
            for (int i = 0; i < MIDDLE.length; i++) {
                rod(p, buffer, sprite, rod(i, t));
            }
        });
    }

    /** Rod {@code i}'s transform at time {@code t}, from a rod centred on the origin to model pixels. */
    private static Matrix4f rod(int i, double t) {
        double around = i * Math.PI / 2 + 2 * Math.PI * t / ORBIT;
        float bob = (float) (0.6 * Math.sin(2 * Math.PI * t / BOB + i * 1.3));
        float spin = (float) (2 * Math.PI * t / SPIN + i);
        return new Matrix4f().translate(8, 8, 8).rotateZ((float) Math.toRadians(-45)).translate(-8, -8, -8)
            .translate(8 + RADIUS * (float) Math.cos(around), MIDDLE[i] + bob, 8 + RADIUS * (float) Math.sin(around))
            .rotateY(spin);
    }

    /** One 1 x 4 x 1 px rod: its sides take the rod strip of the handle texture (8,0)-(9,4), its ends (8,5)-(9,6). */
    private static void rod(PoseStack.Pose p, VertexConsumer buffer, TextureAtlasSprite sprite, Matrix4f m) {
        float x0 = -0.5F, x1 = 0.5F, y0 = -2, y1 = 2, z0 = -0.5F, z1 = 0.5F;
        float[][][] faces = {
            {{x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}},
            {{x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}},
            {{x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}},
            {{x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}},
            {{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}},
            {{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}},
        };
        for (int f = 0; f < faces.length; f++) {
            boolean end = f >= 4;
            float u0 = 8, u1 = 9, v0 = end ? 5 : 0, v1 = end ? 6 : 4;
            Vector3f[] c = new Vector3f[4];
            for (int k = 0; k < 4; k++) {
                c[k] = m.transformPosition(new Vector3f(faces[f][k][0], faces[f][k][1], faces[f][k][2]));
            }
            Vector3f n = new Vector3f(c[1]).sub(c[0]).cross(new Vector3f(c[2]).sub(c[1])).normalize();
            for (int k = 0; k < 4; k++) {
                float u = k == 0 || k == 1 ? u0 : u1, v = k == 0 || k == 3 ? v0 : v1;
                buffer.addVertex(p, c[k].x / 16.0F, c[k].y / 16.0F, c[k].z / 16.0F).setColor(0xFFFFFFFF)
                    .setUv(sprite.getU(u / 16.0F), sprite.getV(v / 16.0F)).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(p, n.x, n.y, n.z);
            }
        }
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        for (int i = 0; i < MIDDLE.length; i++) {
            for (double t = 0; t < ORBIT; t += ORBIT / 8) {
                Matrix4f m = rod(i, t);
                output.accept(m.transformPosition(new Vector3f(0, 2.6F, 0)).div(16));
                output.accept(m.transformPosition(new Vector3f(0, -2.6F, 0)).div(16));
            }
        }
    }

    record Unbaked() implements NoDataSpecialModelRenderer.Unbaked {
        static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public @Nullable SpecialModelRenderer<Void> bake(SpecialModelRenderer.BakingContext context) {
            return new BlazeRodsRenderer();
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
