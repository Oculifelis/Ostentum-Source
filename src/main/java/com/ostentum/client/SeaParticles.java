package com.ostentum.client;

import com.ostentum.Ostentum;
import com.ostentum.weapon.OstentumFx;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.particle.SpriteSet;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import org.joml.Vector3f;

/**
 * The client side of {@link OstentumFx}: one particle class, eight behaviours. Every kind is drawn on vanilla's
 * TRANSLUCENT particle layer (alpha blended), which Iris shader packs render like vanilla's own particles; nothing
 * here uses additive blending or a custom pipeline. The glowing kinds are full-bright.
 */
@EventBusSubscriber(modid = Ostentum.MODID, value = Dist.CLIENT)
public final class SeaParticles {
    private static final int FULL_BRIGHT = 15728880;

    enum Kind { MOTE, BUBBLE, CORAL, SPRAY, GLINT, FISH, RING, SHARD }

    @SubscribeEvent
    public static void providers(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(OstentumFx.MOTE.get(), s -> provider(s, Kind.MOTE));
        event.registerSpriteSet(OstentumFx.BUBBLE.get(), s -> provider(s, Kind.BUBBLE));
        event.registerSpriteSet(OstentumFx.CORAL.get(), s -> provider(s, Kind.CORAL));
        event.registerSpriteSet(OstentumFx.SPRAY.get(), s -> provider(s, Kind.SPRAY));
        event.registerSpriteSet(OstentumFx.GLINT.get(), s -> provider(s, Kind.GLINT));
        event.registerSpriteSet(OstentumFx.FISH.get(), s -> provider(s, Kind.FISH));
        event.registerSpriteSet(OstentumFx.RING.get(), s -> provider(s, Kind.RING));
        event.registerSpriteSet(OstentumFx.SHARD.get(), s -> provider(s, Kind.SHARD));
    }

    private static ParticleProvider<ColorParticleOption> provider(SpriteSet sprites, Kind kind) {
        return (o, level, x, y, z, xa, ya, za) -> new Sea(level, x, y, z, xa, ya, za, sprites, kind, o.getRed(), o.getGreen(), o.getBlue());
    }

    static final class Sea extends TextureSheetParticle {
        private final SpriteSet sprites;
        private final Kind kind;
        private final float phase;
        private final int pick;
        private boolean flip;

        Sea(ClientLevel level, double x, double y, double z, double xa, double ya, double za, SpriteSet sprites, Kind kind, float r, float g, float b) {
            super(level, x, y, z);
            this.sprites = sprites;
            this.kind = kind;
            this.xd = xa;
            this.yd = ya;
            this.zd = za;
            this.rCol = r;
            this.gCol = g;
            this.bCol = b;
            this.phase = this.random.nextFloat() * 10.0F;
            this.pick = this.random.nextInt(4);
            this.roll = this.oRoll = this.random.nextFloat() * Mth.TWO_PI;
            this.hasPhysics = false;
            this.gravity = 0.0F;
            this.friction = 0.9F;
            switch (kind) {
                case MOTE -> life(20, 14, 0.08F, 0.07F);
                case BUBBLE -> {
                    life(24, 20, 0.06F, 0.06F);
                    this.gravity = -0.03F;
                    this.friction = 0.85F;
                    this.roll = this.oRoll = 0.0F;
                }
                case CORAL -> {
                    life(30, 20, 0.06F, 0.03F);
                    this.gravity = 0.4F;
                    this.friction = 0.96F;
                    this.hasPhysics = true;
                }
                case SPRAY -> {
                    life(14, 8, 0.05F, 0.04F);
                    this.gravity = 0.6F;
                    this.friction = 0.98F;
                    this.hasPhysics = true;
                    this.roll = this.oRoll = 0.0F;
                }
                case GLINT -> life(10, 6, 0.1F, 0.12F);
                case FISH -> {
                    life(24, 6, 0.25F, 0.0F);
                    this.gravity = 0.5F;
                    this.friction = 0.99F;
                    this.roll = this.oRoll = 0.0F;
                }
                case RING -> {
                    life(14, 0, 0.12F, 0.0F);
                    this.roll = this.oRoll = 0.0F;
                }
                case SHARD -> {
                    life(20, 10, 0.07F, 0.04F);
                    this.gravity = 0.25F;
                    this.friction = 0.94F;
                    this.hasPhysics = true;
                }
            }
            sprite();
        }

        private void life(int min, int spread, float size, float sizeSpread) {
            this.lifetime = min + (spread > 0 ? this.random.nextInt(spread + 1) : 0);
            this.quadSize = size + this.random.nextFloat() * sizeSpread;
        }

        private void sprite() {
            switch (kind) {
                case MOTE, GLINT -> setSpriteFromAge(sprites);
                case BUBBLE -> setSprite(sprites.get(age >= lifetime - 2 ? 3 : pick % 3, 3));
                case CORAL -> setSprite(sprites.get(pick, 3));
                case SPRAY -> setSprite(sprites.get(pick % 2, 1));
                case FISH -> setSprite(sprites.get((age / 3) % 2, 1));
                case SHARD -> setSprite(sprites.get(pick % 3, 2));
                case RING -> setSprite(sprites.get(0, 1));
            }
        }

        @Override
        public void tick() {
            if (kind == Kind.CORAL) {
                // chips sink slowly under water
                this.gravity = level.getFluidState(BlockPos.containing(x, y, z)).isEmpty() ? 0.4F : 0.08F;
            }
            if (kind == Kind.SPRAY && !level.getFluidState(BlockPos.containing(x, y, z)).isEmpty() && age > 2) {
                remove();
                return;
            }
            super.tick();
            float t = (float) age / lifetime;
            sprite();
            oRoll = roll;
            switch (kind) {
                case MOTE -> {
                    alpha = Mth.sin(Mth.clamp(t, 0.0F, 1.0F) * Mth.PI) * 0.9F;
                    yd += 0.0004;
                }
                case BUBBLE -> {
                    alpha = 0.85F;
                    xd += Mth.sin(age * 0.5F + phase) * 0.002;
                    zd += Mth.cos(age * 0.45F + phase) * 0.002;
                }
                case CORAL, SPRAY, SHARD -> {
                    alpha = t < 0.75F ? 1.0F : Mth.clamp((1.0F - t) / 0.25F, 0.0F, 1.0F);
                    if (kind != Kind.SPRAY && !onGround) {
                        roll += 0.2F;
                    }
                }
                case GLINT -> roll += 0.2F;
                case FISH -> alpha = 0.78F * Mth.clamp((1.0F - t) * 3.0F, 0.0F, 1.0F);
                case RING -> alpha = Mth.clamp(1.0F - t * t, 0.0F, 1.0F);
            }
        }

        @Override
        public float getQuadSize(float a) {
            float t = Mth.clamp((age + a) / lifetime, 0.0F, 1.0F);
            return switch (kind) {
                case GLINT -> quadSize * (0.5F + 0.8F * Mth.sin(t * Mth.PI));
                case RING -> 0.12F + 0.9F * t;
                default -> quadSize;
            };
        }

        /** The fish's nose follows its path on screen: mirrored when it swims left, rolled to its climb or fall. */
        @Override
        public void render(VertexConsumer buffer, Camera camera, float partialTickTime) {
            if (kind == Kind.FISH) {
                Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
                double sx = -(xd * left.x() + yd * left.y() + zd * left.z());
                double sy = xd * up.x() + yd * up.y() + zd * up.z();
                flip = sx < 0;
                roll = oRoll = (float) (flip ? -Math.atan2(sy, -sx) : Math.atan2(sy, sx));
            }
            super.render(buffer, camera, partialTickTime);
        }

        @Override
        protected float getU0() {
            return flip ? super.getU1() : super.getU0();
        }

        @Override
        protected float getU1() {
            return flip ? super.getU0() : super.getU1();
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        public int getLightColor(float a) {
            return switch (kind) {
                case MOTE, GLINT, FISH, RING, SHARD -> FULL_BRIGHT;
                default -> super.getLightColor(a);
            };
        }
    }

    private SeaParticles() {}
}
