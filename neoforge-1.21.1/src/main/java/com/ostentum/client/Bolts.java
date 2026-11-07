package com.ostentum.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ostentum.weapon.SpearArcs;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Lightning geometry for {@link LightningFx}, after Ultima Ratio's BeamRenderer: camera-facing ribbons in three layers
 * (a soft wide glow, a body, a white core) along a path split by midpoint displacement, with side forks; glares are
 * soft discs and sparks are short streaks. Coordinates are camera-relative quads in vanilla's lightning format
 * (position and colour, added onto the frame). Nothing here is version-specific.
 */
final class Bolts {
    static final int CORE = 0xFFFFFF;
    static final int BODY = 0xBFE8FF;
    static final int GLOW = 0x6F86FF;

    private final Vec3 cam;
    private final List<float[]> quads = new ArrayList<>();
    private final List<int[]> colors = new ArrayList<>();

    Bolts(Vec3 cam) {
        this.cam = cam;
    }

    boolean isEmpty() {
        return quads.isEmpty();
    }

    /** How long a bolt of this kind lasts, in ticks. */
    static float life(byte kind) {
        return switch (kind) {
            case SpearArcs.SHOCK -> 9.0F;
            case SpearArcs.HOP -> 8.0F;
            case SpearArcs.JAB -> 6.0F;
            default -> 7.0F;
        };
    }

    /** One bolt {@code age} ticks into its life. */
    void strike(Vec3 from, Vec3 to, byte kind, float age, int seed) {
        float t = Mth.clamp(age / life(kind), 0.0F, 1.0F);
        // full for the first ticks, then fading; flickering twice a tick
        float fade = t < 0.2F ? 1.0F : (float) Math.pow((1.0F - t) / 0.8F, 1.4);
        RandomSource flick = RandomSource.create(seed * 31L + (long) (age * 2.0F));
        fade *= 0.6F + 0.4F * flick.nextFloat();
        if (kind == SpearArcs.BURST) {
            glare(to, 0.7F * (0.6F + 0.4F * (1 - t)), fade);
            sparks(to, seed, age, t, 9, 0.9F, fade);
            return;
        }
        float width = kind == SpearArcs.SHOCK ? 0.07F : kind == SpearArcs.HOP ? 0.055F : 0.035F;
        // the path re-forks every tick and a half, as UR's lightning does every two
        RandomSource r = RandomSource.create(seed * 7919L + (long) (age / 1.5F));
        double len = to.subtract(from).length();
        List<Vec3> main = jag(from, to, len * 0.16, kind == SpearArcs.JAB ? 4 : 5, r);
        bolt(main, width, fade);
        int forks = kind == SpearArcs.JAB ? 1 : 2 + r.nextInt(3);
        for (int k = 0; k < forks; k++) {
            int i = 2 + r.nextInt(Math.max(1, main.size() - 4));
            Vec3 at = main.get(i);
            Vec3 along = main.get(Math.min(main.size() - 1, i + 1)).subtract(at).normalize();
            Vec3 off = new Vec3(r.nextDouble() - 0.5, r.nextDouble() - 0.5, r.nextDouble() - 0.5).normalize();
            Vec3 dir = along.add(off.scale(1.1)).normalize();
            double forkLen = len * (0.12 + r.nextDouble() * 0.22);
            List<Vec3> fork = jag(at, at.add(dir.scale(forkLen)), forkLen * 0.2, 3, r);
            bolt(fork, width * 0.5F, fade * 0.8F);
        }
        glare(to, (kind == SpearArcs.SHOCK ? 0.9F : 0.65F) * (0.7F + 0.3F * (1 - t)), fade);
        glare(from, 0.35F, fade * 0.8F);
        sparks(to, seed, age, t, kind == SpearArcs.JAB ? 5 : 8, kind == SpearArcs.SHOCK ? 1.0F : 0.75F, fade);
    }

    /** The charged spear's crackle: arcs crawling along it from butt to head, sometimes leaping off the head. */
    void crackle(Vec3 butt, Vec3 head, long tick, int id, float scale) {
        RandomSource r = RandomSource.create(tick * 4057L + id * 131L);
        Vec3 along = head.subtract(butt);
        for (int k = 0; k < 2; k++) {
            double a = 0.15 + r.nextDouble() * 0.7, b = Math.min(1.0, a + 0.15 + r.nextDouble() * 0.25);
            Vec3 p = butt.add(along.scale(a)).add(jitter(r, 0.05 * scale));
            Vec3 q = butt.add(along.scale(b)).add(jitter(r, 0.08 * scale));
            bolt(jag(p, q, 0.06 * scale, 3, r), 0.018F * scale, 0.8F);
        }
        if (r.nextFloat() < 0.35F) {
            Vec3 q = head.add(jitter(r, 0.6 * scale));
            bolt(jag(head, q, 0.12 * scale, 3, r), 0.022F * scale, 0.9F);
        }
        glare(head, 0.28F * scale * (0.7F + 0.3F * r.nextFloat()), 0.7F);
    }

    private static Vec3 jitter(RandomSource r, double size) {
        return new Vec3(r.nextDouble() - 0.5, r.nextDouble() - 0.5, r.nextDouble() - 0.5).scale(size * 2);
    }

    /** Midpoint displacement: {@code depth} halvings, each moving the new point sideways by up to {@code spread}, halving. */
    static List<Vec3> jag(Vec3 from, Vec3 to, double spread, int depth, RandomSource r) {
        List<Vec3> pts = new ArrayList<>(List.of(from, to));
        Vec3 dir = to.subtract(from);
        Vec3 a = dir.cross(Math.abs(dir.normalize().y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 b = dir.cross(a).normalize();
        double s = spread;
        for (int d = 0; d < depth; d++) {
            List<Vec3> next = new ArrayList<>(pts.size() * 2);
            for (int i = 0; i < pts.size() - 1; i++) {
                Vec3 p = pts.get(i), q = pts.get(i + 1);
                next.add(p);
                next.add(p.add(q).scale(0.5).add(a.scale((r.nextDouble() - 0.5) * 2 * s)).add(b.scale((r.nextDouble() - 0.5) * 2 * s)));
            }
            next.add(pts.get(pts.size() - 1));
            pts = next;
            s *= 0.5;
        }
        return pts;
    }

    /** A bolt along {@code pts}: glow, body and core, tapering a little toward its end. */
    private void bolt(List<Vec3> pts, float width, float alpha) {
        int n = pts.size() - 1;
        for (int i = 0; i < n; i++) {
            float taper = 1.0F - 0.35F * i / n;
            Vec3 p = pts.get(i).subtract(cam), q = pts.get(i + 1).subtract(cam);
            soft(p, q, width * 7.0F * taper, GLOW, alpha * 0.28F);
            soft(p, q, width * 2.6F * taper, BODY, alpha * 0.55F);
            solid(p, q, width * taper, CORE, alpha * 0.95F);
        }
    }

    /** Short streaks flung out from {@code at}, flying outward and shortening as the bolt fades. */
    private void sparks(Vec3 at, int seed, float age, float t, int count, float size, float alpha) {
        RandomSource r = RandomSource.create(seed * 104729L);
        for (int i = 0; i < count; i++) {
            Vec3 dir = new Vec3(r.nextDouble() - 0.5, r.nextDouble() * 0.9 - 0.3, r.nextDouble() - 0.5).normalize();
            double speed = (0.08 + r.nextDouble() * 0.12) * size;
            double start = 0.1 * size + age * speed;
            double length = (0.18 + r.nextDouble() * 0.22) * size * (1.0 - t);
            Vec3 p = at.add(dir.scale(start)).add(0, -0.02 * age * age * size, 0).subtract(cam);
            Vec3 q = p.add(dir.scale(length));
            soft(p, q, 0.06F * size, BODY, alpha * 0.6F);
            solid(p, q, 0.018F * size, CORE, alpha);
        }
    }

    /** A soft round glare facing the camera: bright in the middle, nothing at the rim. */
    private void glare(Vec3 at, float radius, float alpha) {
        Vec3 c = at.subtract(cam);
        Vec3 view = c.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : c.normalize();
        Vec3 u = view.cross(Math.abs(view.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 v = u.cross(view).normalize();
        int segs = 14;
        int mid = argb(alpha * 0.55F, BODY), rim = argb(0.0F, GLOW);
        int hot = argb(alpha * 0.8F, CORE);
        for (int i = 0; i < segs; i++) {
            double a0 = i * Mth.TWO_PI / segs, a1 = (i + 1) * Mth.TWO_PI / segs;
            Vec3 e0 = c.add(u.scale(Math.cos(a0) * radius)).add(v.scale(Math.sin(a0) * radius));
            Vec3 e1 = c.add(u.scale(Math.cos(a1) * radius)).add(v.scale(Math.sin(a1) * radius));
            quad(c, c, e1, e0, mid, mid, rim, rim);
            Vec3 h0 = c.add(u.scale(Math.cos(a0) * radius * 0.25)).add(v.scale(Math.sin(a0) * radius * 0.25));
            Vec3 h1 = c.add(u.scale(Math.cos(a1) * radius * 0.25)).add(v.scale(Math.sin(a1) * radius * 0.25));
            quad(c, c, h1, h0, hot, hot, argb(0.0F, CORE), argb(0.0F, CORE));
        }
    }

    /** A ribbon from p to q facing the camera (camera-relative points), full at its centre line and clear at its edges. */
    private void soft(Vec3 p, Vec3 q, float width, int rgb, float alpha) {
        Vec3 side = side(p, q, width);
        if (side == null) {
            return;
        }
        int on = argb(alpha, rgb), off = argb(0.0F, rgb);
        quad(p, p.add(side), q.add(side), q, on, off, off, on);
        quad(p.subtract(side), p, q, q.subtract(side), off, on, on, off);
    }

    private void solid(Vec3 p, Vec3 q, float width, int rgb, float alpha) {
        Vec3 side = side(p, q, width);
        if (side == null) {
            return;
        }
        int c = argb(alpha, rgb);
        quad(p.subtract(side), p.add(side), q.add(side), q.subtract(side), c, c, c, c);
    }

    private static Vec3 side(Vec3 p, Vec3 q, float width) {
        Vec3 side = q.subtract(p).cross(p.add(q).scale(0.5));
        return side.lengthSqr() < 1.0E-12 ? null : side.normalize().scale(width * 0.5);
    }

    private static int argb(float alpha, int rgb) {
        return (Mth.clamp((int) (alpha * 255), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int ca, int cb, int cc, int cd) {
        quads.add(new float[] {(float) a.x, (float) a.y, (float) a.z, (float) b.x, (float) b.y, (float) b.z, (float) c.x, (float) c.y,
            (float) c.z, (float) d.x, (float) d.y, (float) d.z});
        colors.add(new int[] {ca, cb, cc, cd});
    }

    /** Writes every quad, both windings so no cull setting hides one. */
    void emit(PoseStack.Pose pose, VertexConsumer buffer) {
        for (int n = 0; n < quads.size(); n++) {
            float[] q = quads.get(n);
            int[] c = colors.get(n);
            for (int i = 0; i < 4; i++) {
                buffer.addVertex(pose, q[i * 3], q[i * 3 + 1], q[i * 3 + 2]).setColor(c[i]);
            }
            for (int i = 3; i >= 0; i--) {
                buffer.addVertex(pose, q[i * 3], q[i * 3 + 1], q[i * 3 + 2]).setColor(c[i]);
            }
        }
    }
}
