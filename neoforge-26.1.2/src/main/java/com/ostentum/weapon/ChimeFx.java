package com.ostentum.weapon;

import com.ostentum.OstentumSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Recall Chime's effects, made the Ultima Ratio way (Oculifelis): amethyst and End light, all of it
 * {@link OstentumFx} particles (Iris-safe). Needs OstentumFx from the tridents delivery.
 */
public final class ChimeFx {
    static final int AMETHYST = 0xB57EDC, DEEP = 0x6A3FA0, PALE = 0xF0D8FF, VOID = 0x2A1540, STONE = 0xDFE09F, DULL = 0x8A7A9A;

    /** The mark set: a ring of light spreading over the ground, shards drifting down, a ring at the chest. */
    public static void mark(ServerLevel level, Player p) {
        RandomSource r = level.getRandom();
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI * 2 / 16;
            SeaFx.shoot(level, OstentumFx.of(OstentumFx.MOTE, i % 2 == 0 ? PALE : AMETHYST), p.getX() + Math.cos(a) * 0.2, p.getY() + 0.1,
                p.getZ() + Math.sin(a) * 0.2, Math.cos(a) * 0.12, 0, Math.sin(a) * 0.12);
        }
        for (int i = 0; i < 6; i++) {
            SeaFx.shoot(level, OstentumFx.of(OstentumFx.SHARD, AMETHYST), p.getX() + SeaFx.g(r, 0.4), p.getY() + 1.6 + r.nextDouble() * 0.5,
                p.getZ() + SeaFx.g(r, 0.4), 0, 0.02, 0);
        }
        level.sendParticles(OstentumFx.of(OstentumFx.RING, AMETHYST), p.getX(), p.getY() + 1.1, p.getZ(), 1, 0, 0, 0, 0);
        level.playSound(null, p.blockPosition(), OstentumSounds.CHIME_MARK.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** Can't pay: a few dull shards drop and a grey ring. */
    public static void fail(ServerLevel level, Player p) {
        RandomSource r = level.getRandom();
        for (int i = 0; i < 6; i++) {
            SeaFx.shoot(level, OstentumFx.of(OstentumFx.SHARD, DULL), p.getX() + SeaFx.g(r, 0.2), p.getY() + 1.3, p.getZ() + SeaFx.g(r, 0.2),
                SeaFx.g(r, 0.03), r.nextDouble() * 0.05, SeaFx.g(r, 0.03));
        }
        level.sendParticles(OstentumFx.of(OstentumFx.RING, DULL), p.getX(), p.getY() + 1.3, p.getZ(), 1, 0, 0, 0, 0);
        level.playSound(null, p.blockPosition(), OstentumSounds.CHIME_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** The wind-up, {@code delay} ticks: two arms of light spiral in and up round the player, a ring with each chime,
     * shards whirling for the last ten ticks. Scheduled with Weapons.later, every 2 ticks. */
    public static void windup(ServerLevel level, Player p, int delay) {
        for (int t = 0; t < delay; t += 2) {
            int at = t;
            Weapons.later(level, t, () -> {
                if (!p.isAlive() || p.level() != level) {
                    return;
                }
                double f = at / (double) delay, rad = 1.6 - 1.3 * f;
                for (int j = 0; j < 2; j++) {
                    double a = at * 0.35 + j * Math.PI;
                    double x = p.getX() + Math.cos(a) * rad, y = p.getY() + 0.2 + 1.4 * f + 0.6 * j, z = p.getZ() + Math.sin(a) * rad;
                    SeaFx.shoot(level, OstentumFx.of(OstentumFx.MOTE, j == 0 ? PALE : AMETHYST), x, y, z, (p.getX() - x) * 0.04, 0.01, (p.getZ() - z) * 0.04);
                }
                if (at % 20 == 0) {
                    level.sendParticles(OstentumFx.of(OstentumFx.RING, AMETHYST), p.getX(), p.getY() + 1.0, p.getZ(), 1, 0, 0, 0, 0);
                }
                if (at >= delay - 10) {
                    double a = at * 0.9;
                    level.sendParticles(OstentumFx.of(OstentumFx.SHARD, PALE), p.getX() + Math.cos(a) * 0.5, p.getY() + 1.0, p.getZ() + Math.sin(a) * 0.5,
                        1, 0, 0, 0, 0);
                }
            });
        }
    }

    /** Leaving: End light drawn into the player from all round (replaces REVERSE_PORTAL), and the depart sound. */
    public static void depart(ServerLevel level, Vec3 at) {
        RandomSource r = level.getRandom();
        int[] cols = {AMETHYST, DEEP, PALE};
        for (int i = 0; i < 24; i++) {
            Vec3 d = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.7, r.nextGaussian()).normalize();
            Vec3 from = at.add(0, 1.0, 0).add(d.scale(1.4));
            SeaFx.shoot(level, OstentumFx.of(OstentumFx.MOTE, cols[i % 3]), from.x, from.y, from.z, -d.x * 0.14, -d.y * 0.14, -d.z * 0.14);
        }
        level.sendParticles(OstentumFx.of(OstentumFx.RING, AMETHYST), at.x, at.y + 1.0, at.z, 1, 0, 0, 0, 0);
        level.playSound(null, at.x, at.y, at.z, OstentumSounds.CHIME_DEPART.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** Arriving: a burst of light and shards, a ring over the ground (replaces END_ROD), and the arrive sound. */
    public static void arrive(ServerLevel level, Vec3 at) {
        RandomSource r = level.getRandom();
        for (int i = 0; i < 20; i++) {
            Vec3 d = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize().scale(0.12);
            SeaFx.shoot(level, OstentumFx.of(OstentumFx.MOTE, i % 3 == 0 ? PALE : AMETHYST), at.x, at.y + 1.0, at.z, d.x, d.y, d.z);
        }
        for (int i = 0; i < 10; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            SeaFx.shoot(level, OstentumFx.of(OstentumFx.SHARD, AMETHYST), at.x, at.y + 1.0, at.z, Math.cos(a) * 0.08, 0.05 + r.nextDouble() * 0.08,
                Math.sin(a) * 0.08);
        }
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI * 2 / 16;
            SeaFx.shoot(level, OstentumFx.of(OstentumFx.MOTE, i % 4 == 0 ? STONE : AMETHYST), at.x + Math.cos(a) * 0.2, at.y + 0.1, at.z + Math.sin(a) * 0.2,
                Math.cos(a) * 0.14, 0, Math.sin(a) * 0.14);
        }
        level.sendParticles(OstentumFx.of(OstentumFx.RING, AMETHYST), at.x, at.y + 1.0, at.z, 1, 0, 0, 0, 0);
        level.playSound(null, at.x, at.y, at.z, OstentumSounds.CHIME_ARRIVE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** The lingering marker the holder sees at the mark (client, called every tick while held, mark in this dimension). */
    public static void marker(Level level, Vec3 mark, int tick) {
        if (tick % 3 == 0) {
            level.addAlwaysVisibleParticle(OstentumFx.of(OstentumFx.MOTE, tick % 9 == 0 ? PALE : AMETHYST), mark.x + (level.getRandom().nextDouble() - 0.5) * 0.3,
                mark.y + 0.1 + level.getRandom().nextDouble() * 1.6, mark.z + (level.getRandom().nextDouble() - 0.5) * 0.3, 0, 0.01, 0);
        }
        if (tick % 40 == 0) {
            level.addAlwaysVisibleParticle(OstentumFx.of(OstentumFx.RING, DEEP), mark.x, mark.y + 0.15, mark.z, 0, 0, 0);
        }
    }

    private ChimeFx() {}
}
