package com.ostentum.weapon;

import com.mojang.serialization.MapCodec;
import com.ostentum.Ostentum;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Ostentum's own particles, made the Ultima Ratio way (Oculifelis): small sprites tinted by the colour they are sent
 * with. They are drawn on vanilla's translucent particle layer (client.SeaParticles), which Iris shader packs draw
 * like any vanilla particle; the glowing ones are full-bright rather than additive.
 */
public final class OstentumFx {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Ostentum.MODID);

    /** A soft round glow that drifts and fades: bioluminescence, gold motes, amethyst light. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> MOTE = PARTICLES.register("sea_mote", () -> colored(false));
    /** A bubble that rises, wobbles and pops (lives in air too, unlike vanilla's). */
    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> BUBBLE = PARTICLES.register("sea_bubble", () -> colored(false));
    /** A chip of coral that tumbles and falls (slowly under water). */
    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> CORAL = PARTICLES.register("sea_coral", () -> colored(false));
    /** A drop of water (or a dark drip) that falls and is gone when it meets water. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> SPRAY = PARTICLES.register("sea_spray", () -> colored(false));
    /** A brief four-point star. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> GLINT = PARTICLES.register("sea_glint", () -> colored(false));
    /** The Fisher King's spectral fish: leaps in an arc, nose along its path. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> FISH = PARTICLES.register("sea_fish", () -> colored(true));
    /** A ring that widens and fades (Recall Chime). */
    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> RING = PARTICLES.register("chime_ring", () -> colored(true));
    /** A glowing amethyst shard that tumbles and falls (Recall Chime). */
    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> SHARD = PARTICLES.register("chime_shard", () -> colored(false));

    static void init(IEventBus modBus) {
        PARTICLES.register(modBus);
    }

    private static ParticleType<ColorParticleOption> colored(boolean overrideLimiter) {
        return new ParticleType<>(overrideLimiter) {
            @Override
            public MapCodec<ColorParticleOption> codec() {
                return ColorParticleOption.codec(this);
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, ColorParticleOption> streamCodec() {
                return ColorParticleOption.streamCodec(this);
            }
        };
    }

    static ColorParticleOption of(DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> type, int rgb) {
        return ColorParticleOption.create(type.get(), 0xFF000000 | rgb);
    }

    private OstentumFx() {}
}
