package com.ostentum.weapon;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;

/**
 * A particle option carrying one ARGB colour: 1.20.1 has no vanilla {@code ColorParticleOption} (added in 1.20.5), so
 * this is a copy with the same getters. On the command line it takes the colour as an ARGB number.
 */
public record ColorParticleOption(ParticleType<ColorParticleOption> type, int argb) implements ParticleOptions {
    public static ColorParticleOption create(ParticleType<ColorParticleOption> type, int argb) {
        return new ColorParticleOption(type, argb);
    }

    public static Codec<ColorParticleOption> codec(ParticleType<ColorParticleOption> type) {
        return Codec.INT.xmap(argb -> new ColorParticleOption(type, argb), ColorParticleOption::argb);
    }

    @SuppressWarnings("deprecation")
    public static final ParticleOptions.Deserializer<ColorParticleOption> DESERIALIZER = new ParticleOptions.Deserializer<>() {
        @Override
        public ColorParticleOption fromCommand(ParticleType<ColorParticleOption> type, StringReader reader) throws CommandSyntaxException {
            reader.expect(' ');
            return new ColorParticleOption(type, reader.readInt());
        }

        @Override
        public ColorParticleOption fromNetwork(ParticleType<ColorParticleOption> type, FriendlyByteBuf buf) {
            return new ColorParticleOption(type, buf.readInt());
        }
    };

    @Override
    public ParticleType<?> getType() {
        return type;
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeInt(argb);
    }

    @Override
    public String writeToString() {
        return BuiltInRegistries.PARTICLE_TYPE.getKey(type) + " " + argb;
    }

    public float getRed() {
        return (argb >> 16 & 0xFF) / 255.0F;
    }

    public float getGreen() {
        return (argb >> 8 & 0xFF) / 255.0F;
    }

    public float getBlue() {
        return (argb & 0xFF) / 255.0F;
    }

    public float getAlpha() {
        return (argb >>> 24) / 255.0F;
    }
}
