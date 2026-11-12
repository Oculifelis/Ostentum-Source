package com.ostentum.weapon;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * The Engineer's Crossbow's zipline (1.9.4): a rope strung between two anchor blocks, drawn sagging a little
 * ({@link #point}), that a player holding the crossbow catches by jumping up to it. Riding is server-authoritative: the
 * player rides a {@link Rider}, an invisible seat the server slides along the rope (vanilla's entity sync carries it to
 * every client, so nothing is predicted and nothing rubber-bands). Speed comes from gravity along the rope's slope,
 * faster downhill, slower uphill, sliding back on a rise; jump lets go, sneak brakes, forward climbs slowly. A block
 * or a mob in the way stops the rider. The line snaps when an anchor block goes, and fades after {@link #LIFE} ticks.
 */
public class Zipline extends Entity {
    /** The longest rope, in blocks. */
    public static final double MAX_LENGTH = 48.0;
    /** The shortest rope worth stringing. */
    public static final double MIN_LENGTH = 3.0;
    /** How far the middle hangs below the straight line, per block of rope. */
    public static final double SAG = 0.03;
    /** Ticks a rope lasts (ten minutes). */
    public static final int LIFE = 12000;
    /** How far a rider's feet hang below the rope. */
    public static final double HANG = 2.0;

    private static final EntityDataAccessor<Vector3fc> DATA_A = SynchedEntityData.defineId(Zipline.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3fc> DATA_B = SynchedEntityData.defineId(Zipline.class, EntityDataSerializers.VECTOR3);

    private BlockPos anchorA = BlockPos.ZERO;
    private BlockPos anchorB = BlockPos.ZERO;
    private @Nullable UUID owner;
    private int age;

    public Zipline(EntityType<? extends Zipline> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** A new rope from {@code a} (on block {@code blockA}) to {@code b} (on {@code blockB}). */
    public static Zipline create(ServerLevel level, Vec3 a, BlockPos blockA, Vec3 b, BlockPos blockB, @Nullable UUID owner) {
        Zipline line = new Zipline(EngineersCrossbow.ZIPLINE.get(), level);
        line.setPos(a.x, a.y, a.z);
        line.entityData.set(DATA_A, new Vector3f((float) a.x, (float) a.y, (float) a.z));
        line.entityData.set(DATA_B, new Vector3f((float) b.x, (float) b.y, (float) b.z));
        line.anchorA = blockA;
        line.anchorB = blockB;
        line.owner = owner;
        return line;
    }

    public Vec3 a() {
        Vector3fc v = this.entityData.get(DATA_A);
        return new Vec3(v.x(), v.y(), v.z());
    }

    public Vec3 b() {
        Vector3fc v = this.entityData.get(DATA_B);
        return new Vec3(v.x(), v.y(), v.z());
    }

    public @Nullable UUID owner() {
        return this.owner;
    }

    public double length() {
        return a().distanceTo(b());
    }

    /** The rope's point at {@code t} (0 at a, 1 at b): the straight line, sagging {@link #SAG} per block in the middle. */
    public static Vec3 point(Vec3 a, Vec3 b, double t) {
        double sag = SAG * a.distanceTo(b);
        return a.add(b.subtract(a).scale(t)).subtract(0, sag * 4 * t * (1 - t), 0);
    }

    /** d(point)/dt: the rope's direction (not normalised; its length is blocks per unit of t). */
    public static Vec3 tangent(Vec3 a, Vec3 b, double t) {
        double sag = SAG * a.distanceTo(b);
        return b.subtract(a).subtract(0, sag * 4 * (1 - 2 * t), 0);
    }

    public Vec3 point(double t) {
        return point(a(), b(), t);
    }

    /** The t of the rope point nearest {@code p} (a few Newton-free refinement passes over the sagging line). */
    public double nearest(Vec3 p) {
        Vec3 a = a();
        Vec3 b = b();
        Vec3 ab = b.subtract(a);
        double t = Math.max(0, Math.min(1, p.subtract(a).dot(ab) / ab.lengthSqr()));
        for (int i = 0; i < 4; i++) {
            Vec3 tan = tangent(a, b, t);
            t = Math.max(0, Math.min(1, t + p.subtract(point(a, b, t)).dot(tan) / tan.lengthSqr()));
        }
        return t;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_A, new Vector3f());
        builder.define(DATA_B, new Vector3f());
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (++this.age > LIFE) {
            snap(level, false);
            return;
        }
        if (this.age % 4 == 0 && (!holds(level, this.anchorA) || !holds(level, this.anchorB))) {
            snap(level, true);
        }
    }

    /** Whether the anchor block is still there (unloaded chunks count as there). */
    private static boolean holds(ServerLevel level, BlockPos pos) {
        return !level.isLoaded(pos) || !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /** The rope goes: dust along it, a snap (or a soft fade at the end of its life). Riders let go on their next tick. */
    public void snap(ServerLevel level, boolean broke) {
        Vec3 a = a();
        Vec3 b = b();
        int steps = (int) Math.ceil(length() * 2);
        DustParticleOptions dust = new DustParticleOptions(0x8A6A3A, 1.0F);
        for (int i = 0; i <= steps; i++) {
            Vec3 p = point(a, b, (double) i / steps);
            level.sendParticles(dust, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
        }
        level.playSound(null, BlockPos.containing(point(a, b, 0.5)), broke ? SoundEvents.LEAD_BREAK : SoundEvents.LEAD_UNTIED, SoundSource.PLAYERS, 1.0F, 1.0F);
        this.discard();
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        Vec3 a = input.read("a", Vec3.CODEC).orElse(this.position());
        Vec3 b = input.read("b", Vec3.CODEC).orElse(this.position());
        this.entityData.set(DATA_A, new Vector3f((float) a.x, (float) a.y, (float) a.z));
        this.entityData.set(DATA_B, new Vector3f((float) b.x, (float) b.y, (float) b.z));
        this.anchorA = input.read("anchor_a", BlockPos.CODEC).orElse(BlockPos.containing(a));
        this.anchorB = input.read("anchor_b", BlockPos.CODEC).orElse(BlockPos.containing(b));
        this.owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
        this.age = input.getIntOr("age", 0);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.store("a", Vec3.CODEC, a());
        output.store("b", Vec3.CODEC, b());
        output.store("anchor_a", BlockPos.CODEC, this.anchorA);
        output.store("anchor_b", BlockPos.CODEC, this.anchorB);
        output.storeNullable("owner", UUIDUtil.CODEC, this.owner);
        output.putInt("age", this.age);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE_ENTITY;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160 * 160;
    }

    /** The rope a player at {@code hands} could catch: within reach of a rope point, away from the anchors. */
    public static @Nullable Catch reach(ServerLevel level, Vec3 hands, double reach) {
        List<Zipline> lines = level.getEntitiesOfClass(Zipline.class, new AABB(hands, hands).inflate(MAX_LENGTH + 2), Entity::isAlive);
        Catch best = null;
        for (Zipline line : lines) {
            double t = line.nearest(hands);
            double margin = 0.6 / Math.max(1.0, line.length());
            if (t <= margin || t >= 1 - margin) {
                continue;
            }
            double d = line.point(t).distanceTo(hands);
            if (d <= reach && (best == null || d < best.distance())) {
                best = new Catch(line, t, d);
            }
        }
        return best;
    }

    public record Catch(Zipline line, double t, double distance) {}

    /** {@code player} catches {@code line} at {@code t}, carrying their speed along it. Returns the seat, or null. */
    public static @Nullable Rider attach(ServerLevel level, Player player, Zipline line, double t) {
        if (player.isPassenger() || player.isSpectator()) {
            return null;
        }
        Rider rider = seat(level, player, line, t);
        if (rider == null) {
            return null;
        }
        Vec3 at = line.point(t);
        player.resetFallDistance();
        level.playSound(null, BlockPos.containing(at), SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 0.7F, 1.4F);
        if (player instanceof ServerPlayer sp) {
            sp.sendOverlayMessage(Component.translatable("item.ostentum.engineers_crossbow.riding"));
        }
        return rider;
    }

    /**
     * Seats {@code passenger} on a new rider on {@code line} at {@code t}, carrying its speed along the rope; null if it
     * cannot ride. Players come through {@link #attach}; the self-test hangs a mob (NeoForge's FakePlayer never rides).
     */
    public static @Nullable Rider seat(ServerLevel level, Entity passenger, Zipline line, double t) {
        Vec3 tan = tangent(line.a(), line.b(), t);
        double speed = passenger.getDeltaMovement().dot(tan.normalize());
        Rider rider = new Rider(EngineersCrossbow.RIDER.get(), level);
        rider.line = line.getUUID();
        rider.rope = line;
        rider.t = t;
        rider.speed = Math.max(-Rider.MAX_SPEED, Math.min(Rider.MAX_SPEED, speed));
        Vec3 at = line.point(t);
        rider.setPos(at.x, at.y, at.z);
        if (!level.addFreshEntity(rider)) {
            return null;
        }
        if (!passenger.startRiding(rider, true, true)) {
            rider.discard();
            return null;
        }
        rider.positionRider(passenger);
        return rider;
    }

    /**
     * The seat a zipline rider hangs from. Server side it slides along its line each tick; client side it only shows
     * where the server put it. Never saved: a rider that logs out or dies lets go first.
     */
    public static class Rider extends Entity {
        public static final double GRAVITY = 0.06;
        public static final double MAX_SPEED = 1.1;
        static final double CLIMB = 0.015;
        static final double CLIMB_MAX = 0.12;

        @Nullable UUID line;
        double t;
        /** Blocks per tick along the rope, positive toward b. */
        double speed;
        /** Set by {@link #step} when something stopped the rider this tick. */
        boolean blocked;

        public Rider(EntityType<? extends Rider> type, Level level) {
            super(type, level);
            this.noPhysics = true;
            this.setNoGravity(true);
        }

        public double t() {
            return this.t;
        }

        public double speed() {
            return this.speed;
        }

        public boolean blocked() {
            return this.blocked;
        }

        /** The rope it was seated on, held directly (a by-UUID lookup misses ropes in chunks whose entities are not loaded). */
        @Nullable Zipline rope;

        public @Nullable Zipline zipline() {
            if (this.rope != null) {
                return this.rope.isAlive() ? this.rope : null;
            }
            return this.line != null && this.level() instanceof ServerLevel level && level.getEntity(this.line) instanceof Zipline z && z.isAlive() ? z : null;
        }

        @Override
        protected void defineSynchedData(SynchedEntityData.Builder builder) {}

        @Override
        public void tick() {
            super.tick();
            if (!(this.level() instanceof ServerLevel level)) {
                return;
            }
            Entity passenger = this.getFirstPassenger();
            Zipline zipline = zipline();
            if (passenger == null || zipline == null || !passenger.isAlive() || passenger instanceof LivingEntity living && living.isDeadOrDying()) {
                letGo(level, false);
                return;
            }
            boolean jump = false;
            boolean brake = false;
            boolean forward = false;
            if (passenger instanceof ServerPlayer player) {
                jump = player.getLastClientInput().jump();
                brake = player.getLastClientInput().shift();
                forward = player.getLastClientInput().forward();
            }
            if (jump) {
                letGo(level, true);
                return;
            }
            step(zipline, passenger, brake, forward ? passenger.getLookAngle() : null);
        }

        /**
         * One tick of the ride on {@code zipline}: gravity along the slope, drag, the brake and a slow climb toward
         * {@code climb} (a look direction, or null), then the move, stopped short by blocks, mobs and the ends.
         */
        public void step(Zipline zipline, Entity passenger, boolean brake, @Nullable Vec3 climb) {
            Vec3 a = zipline.a();
            Vec3 b = zipline.b();
            Vec3 tan = tangent(a, b, this.t);
            double perT = tan.length();
            Vec3 dir = tan.scale(1 / perT);
            this.speed -= GRAVITY * dir.y;
            this.speed *= 0.99;
            this.speed -= Math.signum(this.speed) * Math.min(Math.abs(this.speed), 0.002);
            if (climb != null) {
                double way = Math.signum(climb.dot(dir));
                if (way != 0 && this.speed * way < CLIMB_MAX) {
                    this.speed += way * CLIMB;
                }
            }
            if (brake) {
                this.speed *= 0.8;
                if (Math.abs(this.speed) < 0.02) {
                    this.speed = 0;
                }
            }
            this.speed = Math.max(-MAX_SPEED, Math.min(MAX_SPEED, this.speed));
            double margin = 0.6 / Math.max(1.0, zipline.length());
            this.blocked = false;
            // sub-steps of at most a third of a block, each checked against blocks and mobs
            int steps = Math.max(1, (int) Math.ceil(Math.abs(this.speed) / 0.33));
            for (int i = 0; i < steps; i++) {
                double next = this.t + this.speed / steps / perT;
                if (next <= margin || next >= 1 - margin) {
                    next = Math.max(margin, Math.min(1 - margin, next));
                    this.blocked = true;
                }
                Vec3 to = point(a, b, next);
                if (!clear(passenger, to)) {
                    this.blocked = true;
                    break;
                }
                this.t = next;
                this.setPos(to.x, to.y, to.z);
                if (this.blocked) {
                    break;
                }
            }
            if (this.blocked) {
                this.speed = 0;
            }
            passenger.resetFallDistance();
        }

        /**
         * Whether {@code passenger}, hanging from the rope at {@code rope}, would touch no block and no other living thing.
         * The box is built at the hang point, not moved from where the passenger is now (a player's server position can lag).
         */
        static boolean clear(Entity passenger, Vec3 rope) {
            AABB box = passenger.getDimensions(passenger.getPose()).makeBoundingBox(rope.x, rope.y - HANG, rope.z).deflate(0.05);
            Level level = passenger.level();
            if (!level.noCollision(passenger, box)) {
                return false;
            }
            return level.getEntities(passenger, box, e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e.isPickable()
                && !(e.getVehicle() instanceof Rider r && r == passenger.getVehicle())).isEmpty();
        }

        /** The passenger lets go: with a jump, carried on at the rope's speed and a little up. The seat goes. */
        public void letGo(ServerLevel level, boolean jumped) {
            Entity passenger = this.getFirstPassenger();
            Zipline zipline = zipline();
            Vec3 carry = Vec3.ZERO;
            if (zipline != null) {
                Vec3 tan = tangent(zipline.a(), zipline.b(), this.t);
                carry = tan.normalize().scale(this.speed);
            }
            if (passenger != null) {
                passenger.stopRiding();
                passenger.setDeltaMovement(carry.add(0, jumped ? 0.32 : 0.0, 0));
                passenger.syncVelocity = true;
                passenger.resetFallDistance();
                if (passenger instanceof Player player) {
                    EngineersCrossbow.dropped(player, level.getGameTime());
                }
            }
            this.discard();
        }

        @Override
        protected void positionRider(Entity passenger, Entity.MoveFunction move) {
            move.accept(passenger, this.getX(), this.getY() - HANG, this.getZ());
        }

        @Override
        public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
            // let go where they hang, if that is clear; else just under the rope
            Vec3 hang = new Vec3(this.getX(), this.getY() - HANG, this.getZ());
            if (passenger.level().noCollision(passenger, passenger.getDimensions(passenger.getPose()).makeBoundingBox(hang))) {
                return hang;
            }
            return super.getDismountLocationForPassenger(passenger);
        }

        @Override
        public boolean shouldRiderSit() {
            return false;
        }

        @Override
        protected boolean canAddPassenger(Entity passenger) {
            return this.getPassengers().isEmpty() && passenger instanceof LivingEntity;
        }

        @Override
        public boolean shouldBeSaved() {
            return false;
        }

        @Override
        protected void readAdditionalSaveData(ValueInput input) {}

        @Override
        protected void addAdditionalSaveData(ValueOutput output) {}

        @Override
        public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
            return false;
        }

        @Override
        public boolean isPickable() {
            return false;
        }

        @Override
        public PushReaction getPistonPushReaction() {
            return PushReaction.IGNORE_ENTITY;
        }

        @Override
        public boolean isIgnoringBlockTriggers() {
            return true;
        }
    }

    /** Ropes nearest first, for tests and the attach search. */
    static Comparator<Zipline> nearestTo(Vec3 p) {
        return Comparator.comparingDouble(z -> z.point(z.nearest(p)).distanceToSqr(p));
    }
}
