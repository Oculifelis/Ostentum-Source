package com.ostentum.whip;

import com.ostentum.Props;
import com.fundamentum.glow.Glows;
import com.fundamentum.whip.WhipDefinition;
import com.fundamentum.whip.WhipHit;
import com.fundamentum.whip.WhipHooks;
import com.fundamentum.whip.Whips;
import com.mojang.serialization.MapCodec;
import com.ostentum.Ostentum;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;


/**
 * Ostentum's whips, on Fundamentum's cord whips: three crafted (Vine, then Glow Berry from it, and Spider Silk) and the
 * Ender Whip, found only in End ship chests ({@link EndShipLoot}). Each lash is its own texture, drawn untinted; the
 * glow berries and the Ender Whip's flecks and tip also glow ({@code client.WhipGlow}). Numbers are Ostentum's own picks
 * against Fundamentum's default leather whip (4.5 blocks, 6 at the tip). Between swings each sits coiled under its
 * handle. Each plays differently (1.7.1): the Vine Whip's tip yanks, the Glow Berry Whip lights targets in berry
 * orange, the Spider Silk Whip's three strands each go for their own target, and the Ender Whip homes in.
 */
public final class OstentumWhips {
    public static final RegistryObject<Item> VINE_WHIP = Props.simple(Ostentum.ITEMS, "vine_whip", p -> p.stacksTo(1).enchantable(12));
    public static final RegistryObject<Item> WEEPING_VINE_WHIP = Props.simple(Ostentum.ITEMS, "weeping_vine_whip", p -> p.stacksTo(1).enchantable(14));
    public static final RegistryObject<Item> TWISTING_VINE_WHIP = Props.simple(Ostentum.ITEMS, "twisting_vine_whip", p -> p.stacksTo(1).enchantable(14));
    public static final RegistryObject<Item> GLOW_BERRY_WHIP = Props.simple(Ostentum.ITEMS, "glow_berry_whip", p -> p.stacksTo(1).enchantable(14));
    public static final RegistryObject<Item> SILK_WHIP = Props.simple(Ostentum.ITEMS, "silk_whip", p -> p.stacksTo(1).enchantable(14));
    public static final RegistryObject<Item> ENDER_WHIP = Props.simple(Ostentum.ITEMS, "ender_whip", p -> p.stacksTo(1).rarity(Rarity.RARE).enchantable(18));
    /** Shader showcase (1.8.3): the Ender Whip with its cord drawn through vanilla's End portal starfield. Hidden (1.9.3): only from an Ender Whip thrown into an End portal ({@link EndRiftWhip}). */
    public static final RegistryObject<Item> END_RIFT_WHIP = Props.simple(Ostentum.ITEMS, "end_rift_whip", p -> p.stacksTo(1).rarity(Rarity.EPIC).enchantable(18));

    private static final DeferredRegister<com.mojang.serialization.Codec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
        DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Ostentum.MODID);

    static {
        LOOT_MODIFIERS.register("end_ship_loot", () -> EndShipLoot.CODEC);
    }

    public static void init(IEventBus modBus) {
        LOOT_MODIFIERS.register(modBus);
        register("vine_whip", vine());
        register("weeping_vine_whip", weeping());
        register("twisting_vine_whip", twisting());
        register("glow_berry_whip", glowBerry());
        register("silk_whip", silk());
        register("ender_whip", ender());
        register("end_rift_whip", endRift());
        EndRiftWhip.init();
        com.ostentum.whip.more.MoreWhips.init();
    }

    /**
     * Where the cord leaves Ostentum's 3D handles (tools/whip/handle3d.py): the handles sit lowered along the stick
     * diagonal so the fist closes on the middle of the grip, which puts the collar's top at handheld pixel (9.16, 6.84).
     */
    public static final float HANDLE_END_U = 9.16F, HANDLE_END_V = 6.84F;

    /** Registers an Ostentum whip with the cord leaving its 3D handle's top. */
    public static void register(String id, WhipDefinition whip) {
        Whips.register(Ostentum.id(id), whip.toBuilder().handleEnd(HANDLE_END_U, HANDLE_END_V).build());
    }

    /** The lash texture of a whip, in {@code textures/entity/whip/}. */
    public static ResourceLocation lash(String whip) {
        return Ostentum.id("textures/entity/whip/" + whip + ".png");
    }

    /**
     * The starter: a short vine lash that marks what it hits for the player's summons. A tip hit snags the target and
     * yanks it toward the player (less for heavy ones).
     */
    static WhipDefinition vine() {
        return WhipDefinition.builder(Ostentum.id("vine_whip"))
            .tuning(t -> t.length(4.5).damage(5).knockback(0))
            .coiled().texture(lash("vine_whip")).width(0.14F, 0.05F)
            .summonTag(new WhipDefinition.SummonTag(240, 2, 1.0, 0.0))
            .sounds(SoundEvents.PLAYER_ATTACK_SWEEP, SoundEvents.VINE_STEP)
            .crackParticle(ParticleTypes.COMPOSTER)
            .hooks(new WhipHooks() {
                @Override
                public void landed(WhipHit hit, boolean hurt) {
                    LivingEntity target = hit.living();
                    if (hurt && hit.tip && target != null) {
                        double resist = 1.0 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
                        Vec3 pull = hit.player.position().subtract(target.position()).normalize().scale(0.9 * resist);
                        target.push(pull.x, 0.25 * resist, pull.z);
                        target.hurtMarked = true;
                    }
                }
            })
            .build();
    }

    /**
     * The crimson Vine Whip (1.8.1): a little longer and harder than the Vine Whip, and every hit heals the player (half
     * a heart, a full heart on a tip hit).
     */
    static WhipDefinition weeping() {
        return WhipDefinition.builder(Ostentum.id("weeping_vine_whip"))
            .tuning(t -> t.length(5.0).damage(6).knockback(0))
            .coiled().texture(lash("weeping_vine_whip")).width(0.14F, 0.05F)
            .summonTag(new WhipDefinition.SummonTag(240, 2, 1.0, 0.0))
            .sounds(SoundEvents.PLAYER_ATTACK_SWEEP, SoundEvents.WEEPING_VINES_STEP)
            .crackParticle(ParticleTypes.CRIMSON_SPORE)
            .hooks(new WhipHooks() {
                @Override
                public void landed(WhipHit hit, boolean hurt) {
                    if (hurt && hit.living() != null) {
                        hit.player.heal(hit.tip ? 2.0F : 1.0F);
                    }
                }
            })
            .build();
    }

    /**
     * The warped Vine Whip (1.8.1): a little longer and harder than the Vine Whip, and a tip hit throws the target up into
     * the air (less for heavy ones), to fall back down.
     */
    static WhipDefinition twisting() {
        return WhipDefinition.builder(Ostentum.id("twisting_vine_whip"))
            .tuning(t -> t.length(5.0).damage(6).knockback(0))
            .coiled().texture(lash("twisting_vine_whip")).width(0.14F, 0.05F)
            .summonTag(new WhipDefinition.SummonTag(240, 2, 1.0, 0.0))
            .sounds(SoundEvents.PLAYER_ATTACK_SWEEP, SoundEvents.WEEPING_VINES_STEP)
            .crackParticle(ParticleTypes.WARPED_SPORE)
            .hooks(new WhipHooks() {
                @Override
                public void landed(WhipHit hit, boolean hurt) {
                    LivingEntity target = hit.living();
                    if (hurt && hit.tip && target != null) {
                        double resist = 1.0 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
                        target.push(0, 0.85 * resist, 0);
                        target.hurtMarked = true;
                    }
                }
            })
            .build();
    }

    /** The glow berries' orange: the Glow Berry Whip's outline colour and crack ring. */
    public static final int GLOW_BERRY = 0xF19645;

    /** The vine whip hung with glow berries: every hit outlines the target in glow-berry orange, and summons crit what it marks. */
    static WhipDefinition glowBerry() {
        return WhipDefinition.builder(Ostentum.id("glow_berry_whip"))
            .tuning(t -> t.length(5.0).damage(6))
            .coiled().texture(lash("glow_berry_whip")).width(0.14F, 0.05F)
            .summonTag(new WhipDefinition.SummonTag(240, 3, 1.0, 0.10))
            .sounds(SoundEvents.PLAYER_ATTACK_SWEEP, SoundEvents.CAVE_VINES_PICK_BERRIES)
            .crackParticle(ParticleTypes.SPORE_BLOSSOM_AIR)
            .shockwave(0xC0F19645)
            .hooks(new WhipHooks() {
                @Override
                public void landed(WhipHit hit, boolean hurt) {
                    LivingEntity target = hit.living();
                    if (hurt && target != null) {
                        Glows.apply(target, GLOW_BERRY, 160);
                    }
                }
            })
            .build();
    }

    /**
     * Three thin silk wisps and a quicker swing: each wisp goes for its own hostile within 50 degrees of the look, so one
     * swing can web three; every hit webs the target (Slowness II).
     */
    static WhipDefinition silk() {
        return WhipDefinition.builder(Ostentum.id("silk_whip"))
            .tuning(t -> t.length(5.5).damage(5).strands(3).strandSpread(10).maxTargets(3).cooldownTicks(11))
            .homing(50).strandTargets()
            .coiled().texture(lash("silk_whip")).width(0.07F, 0.03F)
            .sounds(SoundEvents.PLAYER_ATTACK_SWEEP, SoundEvents.SPIDER_STEP)
            .crackParticle(ParticleTypes.WHITE_ASH)
            .hooks(onHit(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false))
            .build();
    }

    /**
     * End ship loot: the longest and hardest lash. It homes: each swing turns toward the hostile nearest the crosshair
     * within 35 degrees and in reach. Every hit lifts the target like a shulker's bullet, and the crack rings purple.
     */
    /** The Ender Whip's numbers under its own id; client.EndRiftStyle draws the cord as a slit into the End. */
    static WhipDefinition endRift() {
        return ender("end_rift_whip").toBuilder().glow(0).build();
    }

    static WhipDefinition ender() {
        return ender("ender_whip");
    }

    static WhipDefinition ender(String id) {
        return WhipDefinition.builder(Ostentum.id(id))
            .tuning(t -> t.length(6.5).damage(9))
            .homing(35).shockwave(0xC0B061E8)
            .coiled().texture(lash("ender_whip")).width(0.11F, 0.04F).glow(0x60B070FF)
            .summonTag(new WhipDefinition.SummonTag(240, 5, 1.2, 0.10))
            .sounds(SoundEvents.PLAYER_ATTACK_SWEEP, SoundEvents.SHULKER_SHOOT)
            .crackParticle(ParticleTypes.REVERSE_PORTAL)
            .particles((level, strand, segment, along, pos, speed, random) -> {
                if (random.nextFloat() < 0.04F + 0.1F * (float) along) {
                    level.addParticle(ParticleTypes.PORTAL, pos.x, pos.y, pos.z,
                        (random.nextDouble() - 0.5) * 0.4, -random.nextDouble() * 0.3, (random.nextDouble() - 0.5) * 0.4);
                }
            })
            .hooks(onHit(MobEffects.LEVITATION, 20, 0, false))
            .build();
    }

    /** An effect on whatever the cord hurts, anywhere along it; only on a crit (a cracking tip) when {@code critOnly}. */
    private static WhipHooks onHit(net.minecraft.world.effect.MobEffect effect, int ticks, int amplifier,
                                   boolean critOnly) {
        return new WhipHooks() {
            @Override
            public void landed(WhipHit hit, boolean hurt) {
                LivingEntity target = hit.living();
                if (hurt && target != null && (hit.crit || !critOnly)) {
                    target.addEffect(new MobEffectInstance(effect, ticks, amplifier), hit.player);
                }
            }
        };
    }

    private OstentumWhips() {}
}
