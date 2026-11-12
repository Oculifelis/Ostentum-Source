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
 * soft discs and sparks are short streaks. The same layers stood upright make the Pathfinder's Glaive's column. Coordinates are camera-relative quads in vanilla's lightning format
 * (position and colour, added onto the frame). Nothing here is version-specific.
 */
final class Bolts {
    static final int CORE = 0xFFFFFF;
    static final int BODY = 0xBFE8FF;
    static final int GLOW = 0x6F86FF;
    /** The glaive's gold, as in its swing trail. */
    static final int GOLD_CORE = 0xFFFAEC, GOLD_BODY = 0xFFD666, GOLD_GLOW = 0xE0A838;
    /** The column's molten blend: gold running into orange, white-gold at the heart. */
    static final int MELT_CORE = 0xFFF2CC, MELT_GOLD = 0xFFC23A, MELT_ORANGE = 0xFF7418, MELT_EMBER = 0xE04A0C;

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

    /**
     * The Pathfinder's Glaive's column ({@link GlaiveBeam}), UR's beam stood upright in the glaive's gold: a soft wide glow,
     * a body and a white-hot core from {@code base} up {@code height} blocks, breathing as UR's beams pulse, with bright bands
     * climbing it and fading out at the top, a glare where it stands and motes rising round its foot. {@code k} shrinks it all
     * toward the camera (the picture is unchanged, but a far column stays inside the far plane and the fog); {@code width}
     * is the core's width before that.
     */
    void column(Vec3 base, double height, float width, float k, float time) {
        column(base, height, width, k, time, false);
    }

    /**
     * The column's tint alone: the glow and body in deeper molten colours, for a blended (not added) render type drawn under
     * {@link #column(Vec3, double, float, float, float)}. Added light alone turns white on a bright sky.
     */
    void columnTint(Vec3 base, double height, float width, float k, float time) {
        column(base, height, width, k, time, true);
    }

    private void column(Vec3 base, double height, float width, float k, float time, boolean tint) {
        int segs = 128;
        float w = width * k * (1.0F + 0.12F * Mth.sin(time * 0.25F));
        Vec3 foot = base.subtract(cam).scale(k);
        for (int i = 0; i < segs; i++) {
            double y0 = height * Math.pow(i / (double) segs, 2), y1 = height * Math.pow((i + 1) / (double) segs, 2);
            Vec3 p = foot.add(0, y0 * k, 0), q = foot.add(0, y1 * k, 0);
            float a0 = shine(y0, height, time), a1 = shine(y1, height, time);
            float m0 = melt(y0, time), m1 = melt(y1, time);
            int body0 = mix(MELT_GOLD, MELT_ORANGE, m0), body1 = mix(MELT_GOLD, MELT_ORANGE, m1);
            int glow0 = mix(MELT_ORANGE, MELT_EMBER, m0), glow1 = mix(MELT_ORANGE, MELT_EMBER, m1);
            if (tint) {
                soft(p, q, w * 6.0F, glow0, a0 * 0.4F, glow1, a1 * 0.4F);
                soft(p, q, w * 2.6F, body0, a0 * 0.75F, body1, a1 * 0.75F);
            } else {
                soft(p, q, w * 7.0F, glow0, a0 * 0.28F, glow1, a1 * 0.28F);
                soft(p, q, w * 2.6F, body0, a0 * 0.55F, body1, a1 * 0.55F);
                solid(p, q, w, MELT_CORE, a0 * 0.95F, a1 * 0.95F);
            }
        }
        if (tint) {
            return;
        }
        glare(cam.add(foot.add(0, 0.15 * k, 0)), 0.9F * k * (0.85F + 0.15F * Mth.sin(time * 0.25F)), 0.9F, MELT_CORE, MELT_GOLD, MELT_ORANGE);
        for (int i = 0; i < 8; i++) {
            float phase = (time / 60.0F + i / 8.0F) % 1.0F;
            double a = i * Mth.TWO_PI / 8 + time * 0.05;
            Vec3 p = foot.add(new Vec3(Math.cos(a) * 0.4, phase * 7.0, Math.sin(a) * 0.4).scale(k));
            Vec3 q = p.add(0, 0.35 * k, 0);
            float alpha = Mth.sin(phase * Mth.PI);
            soft(p, q, 0.14F * k, mix(MELT_GOLD, MELT_ORANGE, phase), alpha * 0.6F);
            solid(p, q, 0.035F * k, MELT_CORE, alpha);
        }
    }

    /** How bright the column is {@code y} blocks up: breathing bands climbing it, fading out over its top part. */
    private static float shine(double y, double height, float time) {
        float top = Mth.clamp((float) (1.0 - Math.sqrt(y / height)) / 0.4F, 0.0F, 1.0F);
        return top * (0.8F + 0.2F * Mth.sin((float) (y * 0.45 - time * 0.3F)));
    }

    /** 0 gold to 1 orange, {@code y} blocks up: two uneven waves sliding slowly down it, like metal running as it melts. */
    private static float melt(double y, float time) {
        float s = 0.65F * Mth.sin((float) (y * 0.5 + time * 0.05F)) + 0.35F * Mth.sin((float) (y * 0.19 + time * 0.03F + 1.3F));
        return Mth.clamp(0.5F + 0.55F * s, 0.0F, 1.0F);
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
            layers(pts.get(i).subtract(cam), pts.get(i + 1).subtract(cam), width * taper, alpha, CORE, BODY, GLOW);
        }
    }

    /** One stretch of a beam (camera-relative points): glow, body and core. */
    private void layers(Vec3 p, Vec3 q, float width, float alpha, int core, int body, int glow) {
        soft(p, q, width * 7.0F, glow, alpha * 0.28F);
        soft(p, q, width * 2.6F, body, alpha * 0.55F);
        solid(p, q, width, core, alpha * 0.95F);
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
        glare(at, radius, alpha, CORE, BODY, GLOW);
    }

    private void glare(Vec3 at, float radius, float alpha, int core, int body, int glow) {
        Vec3 c = at.subtract(cam);
        Vec3 view = c.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : c.normalize();
        Vec3 u = view.cross(Math.abs(view.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 v = u.cross(view).normalize();
        int segs = 14;
        int mid = argb(alpha * 0.55F, body), rim = argb(0.0F, glow);
        int hot = argb(alpha * 0.8F, core);
        for (int i = 0; i < segs; i++) {
            double a0 = i * Mth.TWO_PI / segs, a1 = (i + 1) * Mth.TWO_PI / segs;
            Vec3 e0 = c.add(u.scale(Math.cos(a0) * radius)).add(v.scale(Math.sin(a0) * radius));
            Vec3 e1 = c.add(u.scale(Math.cos(a1) * radius)).add(v.scale(Math.sin(a1) * radius));
            quad(c, c, e1, e0, mid, mid, rim, rim);
            Vec3 h0 = c.add(u.scale(Math.cos(a0) * radius * 0.25)).add(v.scale(Math.sin(a0) * radius * 0.25));
            Vec3 h1 = c.add(u.scale(Math.cos(a1) * radius * 0.25)).add(v.scale(Math.sin(a1) * radius * 0.25));
            quad(c, c, h1, h0, hot, hot, argb(0.0F, core), argb(0.0F, core));
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

    /** {@link #soft(Vec3, Vec3, float, int, float)} shading from one colour and alpha at p to another at q. */
    private void soft(Vec3 p, Vec3 q, float width, int rgbP, float alphaP, int rgbQ, float alphaQ) {
        Vec3 side = side(p, q, width);
        if (side == null) {
            return;
        }
        int onP = argb(alphaP, rgbP), offP = argb(0.0F, rgbP), onQ = argb(alphaQ, rgbQ), offQ = argb(0.0F, rgbQ);
        quad(p, p.add(side), q.add(side), q, onP, offP, offQ, onQ);
        quad(p.subtract(side), p, q, q.subtract(side), offP, onP, onQ, offQ);
    }

    private void solid(Vec3 p, Vec3 q, float width, int rgb, float alphaP, float alphaQ) {
        Vec3 side = side(p, q, width);
        if (side == null) {
            return;
        }
        int cp = argb(alphaP, rgb), cq = argb(alphaQ, rgb);
        quad(p.subtract(side), p.add(side), q.add(side), q.subtract(side), cp, cp, cq, cq);
    }

    private static Vec3 side(Vec3 p, Vec3 q, float width) {
        Vec3 side = q.subtract(p).cross(p.add(q).scale(0.5));
        return side.lengthSqr() < 1.0E-12 ? null : side.normalize().scale(width * 0.5);
    }

    private static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255), g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
        return (r << 16) | (g << 8) | (int) Mth.lerp(t, a & 255, b & 255);
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
