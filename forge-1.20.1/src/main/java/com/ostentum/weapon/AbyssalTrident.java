package com.ostentum.weapon;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.RegistryObject;

/**
 * Abyssal Trident (trident + heart of the sea). Where it lands it leaves a drowning field for {@link #FIELD} ticks: mobs
 * inside lose air even on land, and drown when it runs out (those that breathe water are spared). It also slows Elder
 * Guardians and Wardens, in the field and on a hit.
 */
public final class AbyssalTrident {
    public static final RegistryObject<EntityType<Thrown>> THROWN = WeaponTrident.type("abyssal_trident", Thrown::new);
    public static final RegistryObject<WeaponTrident.Item> TRIDENT = Weapons.item("abyssal_trident",
        p -> new WeaponTrident.Item(p, THROWN), WeaponTrident::props);

    public static final int FIELD = 100;
    static final double RADIUS = 3.5;
    /** Air taken each tick, on top of what the game itself does (on land it gives 4 back a tick). */
    static final int DRAIN = 9;

    record Field(ServerLevel level, Vec3 at, Entity owner, long until) {}

    private static final List<Field> FIELDS = new ArrayList<>();

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(AbyssalTrident::fields);
    }

    public static final class Thrown extends WeaponTrident.Thrown {
        public Thrown(EntityType<? extends Thrown> type, Level level) {
            super(type, level);
        }

        @Override
        net.minecraft.world.item.Item item() {
            return TRIDENT.get();
        }

        @Override
        void land(ServerLevel level) {
            field(level, position(), getOwner());
        }

        @Override
        protected void onHitEntity(EntityHitResult hit) {
            if (hit.getEntity() instanceof LivingEntity living && heavy(living)) {
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
            }
            super.onHitEntity(hit);
        }
    }

    static boolean heavy(LivingEntity entity) {
        return entity.getType() == EntityType.ELDER_GUARDIAN || entity.getType() == EntityType.WARDEN;
    }

    public static void field(ServerLevel level, Vec3 at, Entity owner) {
        FIELDS.add(new Field(level, at, owner, level.getGameTime() + FIELD));
        level.playSound(null, net.minecraft.core.BlockPos.containing(at), SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 0.8F, 0.6F);
    }

    private static void fields(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        for (Iterator<Field> it = FIELDS.iterator(); it.hasNext(); ) {
            Field field = it.next();
            ServerLevel level = field.level();
            if (level.getGameTime() > field.until()) {
                it.remove();
                continue;
            }
            if (level.getGameTime() % 4 == 0) {
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4 + level.getGameTime() * 0.05;
                    level.sendParticles(ParticleTypes.SQUID_INK, field.at().x + Math.cos(a) * RADIUS, field.at().y + 0.2,
                        field.at().z + Math.sin(a) * RADIUS, 1, 0, 0.05, 0, 0.0);
                }
                level.sendParticles(ParticleTypes.BUBBLE_POP, field.at().x, field.at().y + 0.8, field.at().z, 6, RADIUS / 2, 0.6, RADIUS / 2, 0.0);
            }
            for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(field.at(), field.at()).inflate(RADIUS, 2.0, RADIUS))) {
                if (living == field.owner() || living.distanceToSqr(field.at()) > RADIUS * RADIUS + 4) {
                    continue;
                }
                if (heavy(living)) {
                    living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 2));
                }
                drown(level, living);
            }
        }
    }

    /** One tick of the field on {@code living}. */
    public static void drown(ServerLevel level, LivingEntity living) {
        if (living.canBreatheUnderwater() || living instanceof Player player && player.getAbilities().invulnerable) {
            return;
        }
        int air = living.getAirSupply() - DRAIN;
        if (air <= -20) {
            air = 0;
            living.hurt(level.damageSources().drown(), 2.0F);
        }
        living.setAirSupply(air);
    }

    private AbyssalTrident() {}
}
