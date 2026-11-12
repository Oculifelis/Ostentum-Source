package com.ostentum.weapon;

import com.ostentum.OstentumSounds;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.PlayLevelSoundEvent;

/**
 * The three tridents' effects and sounds, made the Ultima Ratio way (Oculifelis). The trails are spawned on the client
 * by the thrown trident itself; impacts and abilities are sent from the server. Every particle is one of
 * {@link OstentumFx}'s, so all of it survives Iris shader packs.
 */
public final class SeaFx {
    /** Abyssal (the weapons session's draft palette, A and B alike): dim teal first, the pale tip colour sparingly. */
    static final int ABY_DEEP = 0x1FA5A8, ABY_TEAL = 0x3FE0D0, ABY_PALE = 0x9DFFF0, ABY_INK = 0x0B1A24, ABY_BUBBLE = 0x4F9A9E, ABY_DRIP = 0x24495A;
    static final int COR_ROSE = 0xF28AA8, COR_BUBBLE = 0xCFEAF2;
    static final int[] REEF = {0xE8507A, 0x3F62D8, 0xA43FC8, 0xD8343A, 0xE3C84B};
    static final int FK_GOLD = 0xFFD34D, FK_RICH = 0xE0A020, FK_SPRAY = 0x9FD3E6, FK_FISH = 0xFFE39A;

    public enum Style {
        ABYSSAL(() -> AbyssalTrident.TRIDENT.get(), OstentumSounds.ABYSSAL_THROW, OstentumSounds.ABYSSAL_HIT, OstentumSounds.ABYSSAL_HIT_GROUND,
            OstentumSounds.ABYSSAL_RETURN, OstentumSounds.ABYSSAL_RIPTIDE),
        CORAL(() -> CoralTrident.TRIDENT.get(), OstentumSounds.CORAL_THROW, OstentumSounds.CORAL_HIT, OstentumSounds.CORAL_HIT_GROUND,
            OstentumSounds.CORAL_RETURN, OstentumSounds.CORAL_RIPTIDE),
        FISHER(() -> FisherKingsTrident.TRIDENT.get(), OstentumSounds.FISHER_THROW, OstentumSounds.FISHER_HIT, OstentumSounds.FISHER_HIT_GROUND,
            OstentumSounds.FISHER_RETURN, OstentumSounds.FISHER_RIPTIDE);

        final Supplier<Item> item;
        final RegistryObject<SoundEvent> thrown, hit, hitGround, back, riptide;

        Style(Supplier<Item> item, RegistryObject<SoundEvent> thrown, RegistryObject<SoundEvent> hit, RegistryObject<SoundEvent> hitGround,
              RegistryObject<SoundEvent> back, RegistryObject<SoundEvent> riptide) {
            this.item = item;
            this.thrown = thrown;
            this.hit = hit;
            this.hitGround = hitGround;
            this.back = back;
            this.riptide = riptide;
        }

        public SoundEvent hitGround() {
            return hitGround.get();
        }

        /** The trident style of a held stack, or null. */
        public static Style of(ItemStack stack) {
            for (Style s : values()) {
                if (stack.is(s.item.get())) {
                    return s;
                }
            }
            return null;
        }
    }

    public static Style style(Entity e) {
        if (e instanceof AbyssalTrident.Thrown) {
            return Style.ABYSSAL;
        }
        return e instanceof CoralTrident.Thrown ? Style.CORAL : Style.FISHER;
    }

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(SeaFx::atEntity);
        MinecraftForge.EVENT_BUS.addListener(SeaFx::atPosition);
    }

    // ------------------------------------------------------------------------------------------------ sounds
    /** The trident whose own vanilla code is running on this thread (its hit and return sounds are swapped). */
    static final ThreadLocal<WeaponTrident.Thrown> SOUNDING = new ThreadLocal<>();

    /** Vanilla's throw (Ostentum's releaseUsing, at the trident) and riptide (at the player) become the trident's own. */
    private static void atEntity(PlayLevelSoundEvent.AtEntity event) {
        if (event.getSound() == null) {
            return;
        }
        SoundEvent sound = event.getSound().value();
        Entity e = event.getEntity();
        if (e instanceof WeaponTrident.Thrown thrown && sound == SoundEvents.TRIDENT_THROW) {
            event.setSound(Holder.direct(style(thrown).thrown.get()));
        } else if (e instanceof Player player && (sound == SoundEvents.TRIDENT_RIPTIDE_1 || sound == SoundEvents.TRIDENT_RIPTIDE_2
            || sound == SoundEvents.TRIDENT_RIPTIDE_3)) {
            Style s = Style.of(player.getUseItem());
            s = s != null ? s : Style.of(player.getMainHandItem());
            s = s != null ? s : Style.of(player.getOffhandItem());
            if (s != null) {
                event.setSound(Holder.direct(s.riptide.get()));
            }
        }
    }

    /** ThrownTrident plays TRIDENT_HIT and TRIDENT_RETURN at its position from its own code. */
    private static void atPosition(PlayLevelSoundEvent.AtPosition event) {
        WeaponTrident.Thrown thrown = SOUNDING.get();
        if (thrown == null || event.getSound() == null) {
            return;
        }
        SoundEvent sound = event.getSound().value();
        if (sound == SoundEvents.TRIDENT_HIT) {
            event.setSound(Holder.direct(style(thrown).hit.get()));
        } else if (sound == SoundEvents.TRIDENT_RETURN) {
            event.setSound(Holder.direct(style(thrown).back.get()));
            event.setNewVolume(Math.min(event.getNewVolume(), 1.0F));
        }
    }

    // ------------------------------------------------------------------------------------------------ helpers
    static void shoot(ServerLevel level, ParticleOptions o, double x, double y, double z, double vx, double vy, double vz) {
        double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (len < 1.0E-5) {
            level.sendParticles(o, x, y, z, 1, 0, 0, 0, 0);
        } else {
            level.sendParticles(o, x, y, z, 0, vx / len, vy / len, vz / len, len);
        }
    }

    static double g(RandomSource r, double s) {
        return (r.nextDouble() - 0.5) * 2 * s;
    }

    // ------------------------------------------------------------------------------------------------ the thrown trident (client)
    /** Each client tick of a thrown trident in flight (the caller skips it while stuck in a block): its flight trail,
     * or its return trail when Loyalty brings it home. */
    public static void trail(WeaponTrident.Thrown t) {
        Level level = t.level();
        RandomSource r = t.level().getRandom();
        Vec3 v = t.getDeltaMovement();
        double x = t.getX() - v.x * 0.5, y = t.getY() + 0.1 - v.y * 0.5, z = t.getZ() - v.z * 0.5;
        boolean back = t.isNoPhysics();
        boolean wet = t.isInWater();
        int age = t.tickCount;
        switch (style(t)) {
            case ABYSSAL -> {
                level.addParticle(OstentumFx.of(OstentumFx.MOTE, back ? ABY_TEAL : ABY_DEEP), x, y, z, 0, 0.004, 0);
                if (back && age % 2 == 0) {
                    level.addParticle(OstentumFx.of(OstentumFx.MOTE, ABY_PALE), t.getX(), t.getY() + 0.1, t.getZ(), 0, 0, 0);
                }
                if (age % 2 == 0) {
                    if (wet) {
                        level.addParticle(OstentumFx.of(OstentumFx.BUBBLE, ABY_BUBBLE), x + g(r, 0.1), y, z + g(r, 0.1), 0, 0.02, 0);
                    } else {
                        level.addParticle(OstentumFx.of(OstentumFx.SPRAY, ABY_DRIP), x, y, z, 0, -0.02, 0);
                    }
                }
            }
            case CORAL -> {
                if (wet || age % 2 == 0) {
                    level.addParticle(OstentumFx.of(OstentumFx.BUBBLE, COR_BUBBLE), x + g(r, 0.1), y, z + g(r, 0.1), 0, 0.02, 0);
                }
                if (age % 3 == 0) {
                    level.addParticle(OstentumFx.of(OstentumFx.CORAL, REEF[(age / 3) % REEF.length]), x, y, z, v.x * 0.1, 0.02, v.z * 0.1);
                }
                if (back) {
                    level.addParticle(OstentumFx.of(OstentumFx.MOTE, COR_ROSE), x, y, z, 0, 0, 0);
                }
            }
            case FISHER -> {
                level.addParticle(OstentumFx.of(OstentumFx.MOTE, back ? FK_GOLD : FK_RICH), x, y, z, 0, 0.004, 0);
                if (age % 2 == 0) {
                    level.addParticle(OstentumFx.of(OstentumFx.GLINT, FK_GOLD), t.getX() + g(r, 0.1), t.getY() + 0.1, t.getZ() + g(r, 0.1), 0, 0, 0);
                } else if (!wet) {
                    level.addParticle(OstentumFx.of(OstentumFx.SPRAY, FK_SPRAY), x, y, z, 0, -0.01, 0);
                }
            }
        }
    }

    /** A player spinning with Riptide: a spiral round them, two arms, rising (client, each tick). */
    public static void riptide(Player p, Style s) {
        Level level = p.level();
        int t = p.tickCount;
        for (int j = 0; j < 2; j++) {
            double a = t * 0.9 + j * Math.PI;
            double x = p.getX() + Math.cos(a) * 0.8, y = p.getY() + 0.3 + (t % 10) * 0.15 + j * 0.2, z = p.getZ() + Math.sin(a) * 0.8;
            switch (s) {
                case ABYSSAL -> {
                    level.addParticle(OstentumFx.of(OstentumFx.MOTE, j == 0 ? ABY_TEAL : ABY_DEEP), x, y, z, 0, 0, 0);
                    level.addParticle(OstentumFx.of(OstentumFx.BUBBLE, ABY_BUBBLE), x, y, z, 0, 0.02, 0);
                }
                case CORAL -> {
                    level.addParticle(OstentumFx.of(OstentumFx.BUBBLE, COR_BUBBLE), x, y, z, 0, 0.02, 0);
                    if (t % 2 == 0) {
                        level.addParticle(OstentumFx.of(OstentumFx.CORAL, REEF[(t + j) % REEF.length]), x, y, z, 0, 0.02, 0);
                    }
                }
                case FISHER -> {
                    level.addParticle(OstentumFx.of(OstentumFx.SPRAY, FK_SPRAY), x, y, z, 0, 0.02, 0);
                    if (t % 2 == 0) {
                        level.addParticle(OstentumFx.of(OstentumFx.GLINT, FK_GOLD), x, y, z, 0, 0, 0);
                    }
                }
            }
        }
    }

    /** Now and then, while a trident is held (client; not drawn for your own first-person view). */
    public static void held(Player p, Style s) {
        int t = p.tickCount;
        double yaw = Math.toRadians(p.yBodyRot);
        // the tip of a trident held upright in the right hand, third person
        double x = p.getX() - Math.cos(yaw) * 0.4, y = p.getY() + 2.05, z = p.getZ() - Math.sin(yaw) * 0.4;
        Level level = p.level();
        RandomSource r = p.getRandom();
        switch (s) {
            case ABYSSAL -> {
                if (t % 8 == 0) {
                    level.addParticle(OstentumFx.of(OstentumFx.MOTE, r.nextInt(4) == 0 ? ABY_TEAL : ABY_DEEP), x + g(r, 0.1), y + g(r, 0.15), z + g(r, 0.1),
                        0, 0.01, 0);
                }
            }
            case CORAL -> {
                if (t % 6 == 0 && p.isInWater()) {
                    level.addParticle(OstentumFx.of(OstentumFx.BUBBLE, COR_BUBBLE), x + g(r, 0.1), y, z + g(r, 0.1), 0, 0.03, 0);
                }
            }
            case FISHER -> {
                if (t % 14 == 0) {
                    level.addParticle(OstentumFx.of(OstentumFx.GLINT, FK_GOLD), x + g(r, 0.12), y + g(r, 0.15), z + g(r, 0.12), 0, 0, 0);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ server: landing, water
    /** Where the trident first stops, in a block or on a mob. */
    public static void land(ServerLevel level, WeaponTrident.Thrown t) {
        RandomSource r = level.getRandom();
        double x = t.getX(), y = t.getY() + 0.1, z = t.getZ();
        switch (style(t)) {
            case ABYSSAL -> {
                for (int i = 0; i < 14; i++) {
                    double a = i * Math.PI * 2 / 14;
                    shoot(level, OstentumFx.of(OstentumFx.MOTE, i % 4 == 0 ? ABY_TEAL : ABY_DEEP), x, y, z, Math.cos(a) * 0.12, 0.02, Math.sin(a) * 0.12);
                }
                for (int i = 0; i < 8; i++) {
                    shoot(level, OstentumFx.of(OstentumFx.BUBBLE, ABY_BUBBLE), x + g(r, 0.4), y, z + g(r, 0.4), 0, 0.02 + r.nextDouble() * 0.04, 0);
                }
                level.sendParticles(OstentumFx.of(OstentumFx.MOTE, ABY_INK), x, y, z, 5, 0.3, 0.05, 0.3, 0.0);
            }
            case CORAL -> {
                for (int i = 0; i < 12; i++) {
                    double a = r.nextDouble() * Math.PI * 2, s = 0.05 + r.nextDouble() * 0.09;
                    shoot(level, OstentumFx.of(OstentumFx.CORAL, REEF[r.nextInt(REEF.length)]), x, y, z, Math.cos(a) * s, 0.08 + r.nextDouble() * 0.12,
                        Math.sin(a) * s);
                }
                for (int i = 0; i < 6; i++) {
                    shoot(level, OstentumFx.of(OstentumFx.BUBBLE, COR_BUBBLE), x + g(r, 0.4), y, z + g(r, 0.4), 0, 0.02 + r.nextDouble() * 0.03, 0);
                }
            }
            case FISHER -> {
                for (int i = 0; i < 14; i++) {
                    double a = r.nextDouble() * Math.PI * 2, s = 0.05 + r.nextDouble() * 0.07;
                    shoot(level, OstentumFx.of(OstentumFx.SPRAY, FK_SPRAY), x, y, z, Math.cos(a) * s, 0.12 + r.nextDouble() * 0.13, Math.sin(a) * s);
                }
                level.sendParticles(OstentumFx.of(OstentumFx.GLINT, FK_GOLD), x, y + 0.3, z, 5, 0.5, 0.3, 0.5, 0.0);
            }
        }
    }

    /** The first tick in water: a ring of spray at the surface and a short column of bubbles. */
    public static void splash(ServerLevel level, WeaponTrident.Thrown t) {
        Style s = style(t);
        RandomSource r = level.getRandom();
        int spray = s == Style.ABYSSAL ? ABY_DRIP : FK_SPRAY;
        int bubble = s == Style.ABYSSAL ? ABY_BUBBLE : COR_BUBBLE;
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI * 2 / 12;
            shoot(level, OstentumFx.of(OstentumFx.SPRAY, spray), t.getX(), t.getY() + 0.2, t.getZ(), Math.cos(a) * 0.08, 0.18 + r.nextDouble() * 0.1,
                Math.sin(a) * 0.08);
        }
        level.sendParticles(OstentumFx.of(OstentumFx.BUBBLE, bubble), t.getX(), t.getY() - 0.4, t.getZ(), 8, 0.2, 0.4, 0.2, 0.02);
        if (s == Style.FISHER) {
            level.sendParticles(OstentumFx.of(OstentumFx.GLINT, FK_GOLD), t.getX(), t.getY() + 0.3, t.getZ(), 3, 0.4, 0.2, 0.4, 0.0);
        }
    }

    // ------------------------------------------------------------------------------------------------ Abyssal: the drowning field
    /** The field opening: a ring of teal light drawn inward and a rush of bubbles. */
    public static void abyssOpen(ServerLevel level, Vec3 at, double radius) {
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI * 2 / 24;
            shoot(level, OstentumFx.of(OstentumFx.MOTE, i % 6 == 0 ? ABY_TEAL : ABY_DEEP), at.x + Math.cos(a) * radius, at.y + 0.2, at.z + Math.sin(a) * radius,
                -Math.cos(a) * 0.2, 0.01, -Math.sin(a) * 0.2);
        }
        level.sendParticles(OstentumFx.of(OstentumFx.BUBBLE, ABY_BUBBLE), at.x, at.y + 0.4, at.z, 16, radius / 3, 0.3, radius / 3, 0.04);
    }

    /** Every 4 ticks of a live field (replaces the SQUID_INK ring and BUBBLE_POP). */
    public static void abyssField(ServerLevel level, Vec3 at, double radius, long time) {
        RandomSource r = level.getRandom();
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5 + time * 0.05;
            level.sendParticles(OstentumFx.of(OstentumFx.MOTE, i % 5 == 0 ? ABY_TEAL : ABY_DEEP), at.x + Math.cos(a) * radius, at.y + 0.2,
                at.z + Math.sin(a) * radius, 1, 0, 0, 0, 0.0);
        }
        for (int i = 0; i < 5; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * radius;
            shoot(level, OstentumFx.of(OstentumFx.BUBBLE, ABY_BUBBLE), at.x + Math.cos(a) * d, at.y + 0.2 + r.nextDouble() * 0.4, at.z + Math.sin(a) * d,
                0, 0.03, 0);
        }
        level.sendParticles(OstentumFx.of(OstentumFx.MOTE, ABY_INK), at.x, at.y + 0.1, at.z, 1, 1.0, 0.0, 1.0, 0.0);
    }

    /** A mob losing air in the field: bubbles from its mouth (every 4 ticks). */
    public static void drowning(ServerLevel level, LivingEntity living) {
        Vec3 m = living.getEyePosition();
        shoot(level, OstentumFx.of(OstentumFx.BUBBLE, ABY_BUBBLE), m.x, m.y, m.z, 0, 0.04, 0);
        if (living.getAirSupply() < 60) {
            shoot(level, OstentumFx.of(OstentumFx.BUBBLE, ABY_PALE), m.x, m.y, m.z, 0, 0.035, 0);
        }
    }

    // ------------------------------------------------------------------------------------------------ Coral: whirlpool, growth
    /** Each tick of a live whirlpool (replaces its three BUBBLE particles). */
    public static void whirl(ServerLevel level, Vec3 at, long time) {
        double spin = time * 0.6;
        for (int j = 0; j < 2; j++) {
            int i = j * 2 + (int) (time % 2);
            double a = spin + i * Math.PI / 2, rad = 1.2 - 0.25 * i;
            shoot(level, OstentumFx.of(OstentumFx.BUBBLE, COR_BUBBLE), at.x + Math.cos(a) * rad, at.y + 0.35 * i, at.z + Math.sin(a) * rad,
                -Math.sin(a) * 0.04, 0.02, Math.cos(a) * 0.04);
        }
        if (time % 3 == 0) {
            double a = spin * 0.7;
            shoot(level, OstentumFx.of(OstentumFx.CORAL, REEF[(int) (time / 3 % REEF.length)]), at.x + Math.cos(a) * 1.3, at.y + 0.5, at.z + Math.sin(a) * 1.3,
                -Math.sin(a) * 0.08, 0.02, Math.cos(a) * 0.08);
        }
    }

    /** The thrower being pulled to the whirlpool: a wake of bubbles behind them. */
    public static void pulled(ServerLevel level, Entity owner) {
        Vec3 v = owner.getDeltaMovement();
        level.sendParticles(OstentumFx.of(OstentumFx.BUBBLE, COR_BUBBLE), owner.getX() - v.x, owner.getY() + 0.9 - v.y, owner.getZ() - v.z, 1, 0.2, 0.3, 0.2, 0.0);
    }

    /** A coral or kelp grown at {@code pos}: chips in the coral's own colour, bubbles, the grow sound. Replaces levelEvent 1505. */
    public static void grow(ServerLevel level, BlockPos pos, BlockState state) {
        int c = coralColour(state);
        RandomSource r = level.getRandom();
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        for (int i = 0; i < 10; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            shoot(level, OstentumFx.of(OstentumFx.CORAL, c), x, y, z, Math.cos(a) * 0.07, 0.05 + r.nextDouble() * 0.08, Math.sin(a) * 0.07);
        }
        level.sendParticles(OstentumFx.of(OstentumFx.BUBBLE, COR_BUBBLE), x, y, z, 4, 0.3, 0.2, 0.3, 0.02);
        level.playSound(null, pos, OstentumSounds.CORAL_GROW.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    static int coralColour(BlockState s) {
        String id = s.getBlock().getDescriptionId();
        if (id.contains("tube")) {
            return REEF[1];
        } else if (id.contains("brain")) {
            return REEF[0];
        } else if (id.contains("bubble")) {
            return REEF[2];
        } else if (id.contains("fire")) {
            return REEF[3];
        } else if (id.contains("horn")) {
            return REEF[4];
        }
        return s.is(Blocks.KELP) || s.is(Blocks.KELP_PLANT) ? 0x4E8A3A : 0x5FA040;
    }

    // ------------------------------------------------------------------------------------------------ Fisher King's: the catch
    /** The catch: gold-lit spray, glints, and one spectral fish leaping toward the thrower for each stack caught. */
    public static void fishCatch(ServerLevel level, Entity at, LivingEntity owner, int caught) {
        RandomSource r = level.getRandom();
        double x = at.getX(), y = at.getY() + 0.5, z = at.getZ();
        for (int i = 0; i < 18; i++) {
            double a = r.nextDouble() * Math.PI * 2, s = 0.04 + r.nextDouble() * 0.06;
            shoot(level, OstentumFx.of(OstentumFx.SPRAY, FK_SPRAY), x, y, z, Math.cos(a) * s, 0.15 + r.nextDouble() * 0.15, Math.sin(a) * s);
        }
        level.sendParticles(OstentumFx.of(OstentumFx.GLINT, FK_GOLD), x, y + 0.3, z, 6, 0.6, 0.35, 0.6, 0.0);
        Vec3 to = owner.position().subtract(at.position());
        Vec3 flat = new Vec3(to.x, 0, to.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : flat.normalize();
        for (int i = 0; i < Math.max(1, Math.min(3, caught)); i++) {
            double s = 0.14 + r.nextDouble() * 0.05;
            shoot(level, OstentumFx.of(OstentumFx.FISH, FK_FISH), x + g(r, 0.3), y, z + g(r, 0.3), flat.x * s, 0.28 + r.nextDouble() * 0.06, flat.z * s);
        }
        level.playSound(null, at.blockPosition(), OstentumSounds.FISHER_CATCH.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** The aquatic bonus landing: a ring of gold glints and a small fish leaping off the target. */
    public static void aquatic(ServerLevel level, LivingEntity target) {
        RandomSource r = level.getRandom();
        double x = target.getX(), y = target.getY(0.6), z = target.getZ();
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            shoot(level, OstentumFx.of(OstentumFx.GLINT, FK_GOLD), x + Math.cos(a) * 0.5, y, z + Math.sin(a) * 0.5, Math.cos(a) * 0.03, 0.01, Math.sin(a) * 0.03);
        }
        shoot(level, OstentumFx.of(OstentumFx.FISH, FK_FISH), x, y, z, g(r, 0.12), 0.25, g(r, 0.12));
        level.playSound(null, target.blockPosition(), OstentumSounds.FISHER_AQUATIC.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private SeaFx() {}
}
