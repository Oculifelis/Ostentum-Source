package com.ostentum.client;

import com.ostentum.Ostentum;
import com.ostentum.weapon.AbyssalTrident;
import com.ostentum.weapon.CoralTrident;
import com.ostentum.weapon.FisherKingsTrident;
import com.ostentum.weapon.FrostbiteHatchet;
import com.ostentum.weapon.PathfindersGlaive;
import com.ostentum.weapon.RecallChime;
import com.ostentum.weapon.WeathervaneSpear;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Dev client only (-Dostentum.throwshot=true, gradlew runClient -Pthrowshot): the thrown and lightning weapons in a
 * fresh flat world, saving run/screenshots/throwshot-*.png: the Frostbite Hatchet in flight (a burst of frames), the
 * hatchet coming home to the hotbar slot it left (and to the first free one when that slot was filled meanwhile), the
 * Weathervane Spear charged and discharging into two pigs, the Pathfinder's Glaive held enchanted and its planted beam
 * seen from 120 and 300 blocks. The window is moved off screen. Logs "THROWSHOT" lines; slot checks print PASS/FAIL.
 */
@EventBusSubscriber(modid = Ostentum.MODID, value = Dist.CLIENT)
public final class ThrowShot {
    static final boolean ON = Boolean.getBoolean("ostentum.throwshot");

    /** One scene: camera, look, a server setup, how many ticks to wait, frames to grab (tick offsets) and a check. */
    private record Scene(String label, CameraType camera, float yRot, float xRot, Consumer<ServerPlayer> setup, int[] frames,
                         Consumer<ServerPlayer> check) {}

    private static final List<Scene> SCENES = new ArrayList<>();
    private static final Map<String, java.util.function.Supplier<EntityType<?>>> TRIDENT_TYPES = new java.util.LinkedHashMap<>();
    private static boolean started;
    private static boolean hidden;
    private static int index;
    private static int step;
    private static int ticks;
    private static int failed;

    static {
        TRIDENT_TYPES.put("abyssal", () -> AbyssalTrident.THROWN.get());
        TRIDENT_TYPES.put("coral", () -> CoralTrident.THROWN.get());
        TRIDENT_TYPES.put("fisher_kings", () -> FisherKingsTrident.THROWN.get());
        int[] burst = new int[14];
        for (int i = 0; i < burst.length; i++) {
            burst[i] = 4 + i * 2;
        }
        SCENES.add(new Scene("hatchet-flight", CameraType.FIRST_PERSON, 0.0F, 5.0F, p -> {
            clear(p);
            FrostbiteHatchet.Thrown thrown = new FrostbiteHatchet.Thrown(FrostbiteHatchet.THROWN.get(), p.level());
            thrown.setOwner(p);
            thrown.setItem(new ItemStack(FrostbiteHatchet.HATCHET.get()));
            thrown.setPos(p.getX() - 4.5, p.getEyeY() - 0.3, p.getZ() + 4.0);
            thrown.setDeltaMovement(0.75, 0.02, 0.0);
            p.level().addFreshEntity(thrown);
        }, burst, p -> {}));
        int[] side = new int[10];
        for (int i = 0; i < side.length; i++) {
            side[i] = 3 + i * 2;
        }
        SCENES.add(new Scene("hatchet-throw", CameraType.THIRD_PERSON_FRONT, 90.0F, 0.0F, p -> {
            clear(p);
            p.setGameMode(GameType.SURVIVAL);
            p.getInventory().setItem(3, new ItemStack(FrostbiteHatchet.HATCHET.get()));
            p.getInventory().setSelectedSlot(3);
            FrostbiteHatchet.HATCHET.get().use(p.level(), p, InteractionHand.MAIN_HAND);
        }, side, p -> {}));
        // return-to-slot: thrown from slot 3, it comes back to slot 3
        SCENES.add(new Scene("return-slot3", CameraType.FIRST_PERSON, 0.0F, 0.0F, p -> {
            clear(p);
            p.setGameMode(GameType.SURVIVAL);
            p.getInventory().setItem(3, new ItemStack(FrostbiteHatchet.HATCHET.get()));
            p.getInventory().setSelectedSlot(3);
            FrostbiteHatchet.HATCHET.get().use(p.level(), p, InteractionHand.MAIN_HAND);
            say((p.getInventory().getItem(3).isEmpty() ? "PASS" : "FAIL") + " hatchet left slot 3 when thrown");
        }, new int[] {60}, p -> {
            boolean back = p.getInventory().getItem(3).is(FrostbiteHatchet.HATCHET.get());
            say((back ? "PASS" : "FAIL") + " hatchet thrown from slot 3 came back to slot 3 (" + where(p) + "), selected "
                + p.getInventory().getSelectedSlot());
        }));
        // slot taken: dirt fills slot 3 mid flight, so it comes back to the first free hotbar slot (0)
        SCENES.add(new Scene("return-taken", CameraType.FIRST_PERSON, 0.0F, 0.0F, p -> {
            clear(p);
            p.setGameMode(GameType.SURVIVAL);
            p.getInventory().setItem(3, new ItemStack(FrostbiteHatchet.HATCHET.get()));
            p.getInventory().setSelectedSlot(3);
            FrostbiteHatchet.HATCHET.get().use(p.level(), p, InteractionHand.MAIN_HAND);
            p.getInventory().setItem(3, new ItemStack(Items.DIRT));
        }, new int[] {60}, p -> {
            boolean slot0 = p.getInventory().getItem(0).is(FrostbiteHatchet.HATCHET.get());
            say((slot0 ? "PASS" : "FAIL") + " slot 3 taken: hatchet came back to the first free hotbar slot 0 (" + where(p) + ")");
            p.setGameMode(GameType.CREATIVE);
        }));
        SCENES.add(new Scene("spear-held", CameraType.THIRD_PERSON_FRONT, 0.0F, 10.0F, p -> {
            clear(p);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(WeathervaneSpear.SPEAR.get()));
        }, new int[] {30}, p -> {}));
        SCENES.add(new Scene("spear-held-1st", CameraType.FIRST_PERSON, 0.0F, 10.0F, p -> {}, new int[] {20}, p -> {}));
        SCENES.add(new Scene("spear-charged", CameraType.THIRD_PERSON_FRONT, 0.0F, 10.0F, p -> {
            command(p, "time set 18000");
            charge(p);
        }, new int[] {25, 27, 29}, p -> {}));
        SCENES.add(new Scene("spear-charged-1st", CameraType.FIRST_PERSON, 0.0F, 10.0F, p -> charge(p), new int[] {25, 27}, p -> {}));
        int[] zap = {1, 2, 3, 4, 5, 6, 8, 10, 13};
        SCENES.add(new Scene("spear-shock", CameraType.FIRST_PERSON, 0.0F, 15.0F, p -> {
            charge(p);
            Mob a = pig(p, 0.0, 3.5);
            pig(p, 3.0, 5.0);
            a.hurtServer(p.level(), p.level().damageSources().playerAttack(p), 1.0F);
        }, zap, p -> p.level().getEntitiesOfClass(Mob.class, new AABB(p.blockPosition()).inflate(12)).forEach(Entity::discard)));
        SCENES.add(new Scene("spear-shock-day", CameraType.FIRST_PERSON, 0.0F, 15.0F, p -> {
            command(p, "time set noon");
            charge(p);
            Mob a = pig(p, 0.0, 3.5);
            pig(p, -3.0, 5.0);
            a.hurtServer(p.level(), p.level().damageSources().playerAttack(p), 1.0F);
        }, new int[] {1, 2, 3, 5}, p -> p.level().getEntitiesOfClass(Mob.class, new AABB(p.blockPosition()).inflate(12)).forEach(Entity::discard)));
        SCENES.add(new Scene("glaive-held-enchanted", CameraType.THIRD_PERSON_FRONT, 0.0F, 10.0F, p -> {
            clear(p);
            ItemStack glaive = new ItemStack(PathfindersGlaive.GLAIVE.get());
            glaive.enchant(p.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 3);
            p.setItemInHand(InteractionHand.MAIN_HAND, glaive);
            say((glaive.hasFoil() ? "FAIL glint shows on" : "PASS no glint on") + " an enchanted glaive");
        }, new int[] {30}, p -> {}));
        for (int far : new int[] {120, 300}) {
            SCENES.add(new Scene("glaive-beam-" + far, CameraType.FIRST_PERSON, 0.0F, -12.0F, p -> plantAt(p, far), new int[] {60}, p -> {}));
            SCENES.add(new Scene("glaive-beam-" + far + "-up", CameraType.FIRST_PERSON, 0.0F, -60.0F, p -> {}, new int[] {10}, p -> {}));
        }
        SCENES.add(new Scene("glaive-beam-night", CameraType.FIRST_PERSON, 0.0F, -12.0F, p -> command(p, "time set 18000"), new int[] {30}, p -> {}));
        // the glaive's five sounds: a jab into a pig (thrust, hit, trail), the braced charge, then plant and recall
        SCENES.add(new Scene("glaive-sounds", CameraType.FIRST_PERSON, 0.0F, 15.0F, p -> {
            command(p, "time set noon");
            clear(p);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PathfindersGlaive.GLAIVE.get()));
            pig(p, 0.0, 2.5);
        }, new int[] {7, 40, 95}, p -> p.level().getEntitiesOfClass(Mob.class, new AABB(p.blockPosition()).inflate(12)).forEach(Entity::discard)));
        SCENES.add(new Scene("glaive-glow", CameraType.THIRD_PERSON_FRONT, 0.0F, 10.0F, p -> {
            command(p, "time set 18000");
            clear(p);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PathfindersGlaive.GLAIVE.get()));
        }, new int[] {30}, p -> {}));
        // the three tridents: their 3D model mid flight across the view, then each of their sounds (a throw at a pig, into a
        // pool, onto grass, the Fisher King's on a cod, a riptide out of the pool)
        for (String name : TRIDENT_TYPES.keySet()) {
            SCENES.add(new Scene("trident-" + name + "-flight", CameraType.FIRST_PERSON, 0.0F, 5.0F, p -> {
                clear(p);
                command(p, "time set noon");
                Entity thrown = TRIDENT_TYPES.get(name).get().create(p.level(), EntitySpawnReason.COMMAND);
                thrown.setPos(p.getX() - 3.0, p.getEyeY() + 0.1, p.getZ() + 3.0);
                thrown.setDeltaMovement(0.55, 0.12, 0.0);
                thrown.setYRot(-90.0F);
                thrown.setXRot(0.0F);
                p.level().addFreshEntity(thrown);
            }, new int[] {3, 5, 7}, p -> p.level().getEntitiesOfClass(Entity.class, new AABB(p.blockPosition()).inflate(24),
                e -> e.getType() == TRIDENT_TYPES.get(name).get()).forEach(Entity::discard)));
            SCENES.add(new Scene("trident-" + name + "-sounds", CameraType.THIRD_PERSON_BACK, 0.0F, 20.0F, p -> {
                clear(p);
                pool(p);
                pig(p, 0.0, 3.5);
            }, new int[] {215}, p -> {
                p.level().getEntitiesOfClass(Mob.class, new AABB(p.blockPosition()).inflate(16)).forEach(Entity::discard);
                p.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(p.blockPosition()).inflate(16))
                    .forEach(Entity::discard);
            }));
        }
        SCENES.add(new Scene("chime-sounds", CameraType.THIRD_PERSON_FRONT, 0.0F, 10.0F, p -> {
            clear(p);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RecallChime.CHIME.get()));
        }, new int[] {12, 70, 80, 130}, p -> p.setGameMode(GameType.CREATIVE)));
    }

    private ThrowShot() {}

    private static void say(String s) {
        System.out.println("THROWSHOT " + s);
        if (s.startsWith("FAIL")) {
            failed++;
        }
    }

    private static String where(ServerPlayer p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(FrostbiteHatchet.HATCHET.get())) {
                return "found in slot " + i;
            }
        }
        return "not in the inventory";
    }

    private static void clear(ServerPlayer p) {
        p.getInventory().clearContent();
        p.getInventory().setSelectedSlot(0);
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.getInventory().setSelectedSlot(0);
            }
        });
    }

    private static void command(ServerPlayer p, String command) {
        MinecraftServer server = p.level().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    /** Fills the player's static (the spear's private map, by reflection, so this runs on any version of it). */
    @SuppressWarnings("unchecked")
    private static void charge(ServerPlayer p) {
        p.setItemInHand(InteractionHand.MAIN_HAND, p.getMainHandItem().is(WeathervaneSpear.SPEAR.get()) ? p.getMainHandItem()
            : new ItemStack(WeathervaneSpear.SPEAR.get()));
        try {
            var field = WeathervaneSpear.class.getDeclaredField("STATIC");
            field.setAccessible(true);
            ((Map<UUID, Integer>) field.get(null)).put(p.getUUID(), 40);
            say("PASS spear charged");
        } catch (ReflectiveOperationException e) {
            say("FAIL could not charge the spear: " + e);
        }
    }

    private static Mob pig(ServerPlayer p, double dx, double dz) {
        ServerLevel level = p.level();
        Mob mob = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
        mob.snapTo(p.getX() + dx, p.getY(), p.getZ() + dz, 180.0F, 0.0F);
        mob.setNoAi(true);
        level.addFreshEntity(mob);
        return mob;
    }

    private static void plantAt(ServerPlayer p, int far) {
        clear(p);
        ItemStack glaive = new ItemStack(PathfindersGlaive.GLAIVE.get());
        PathfindersGlaive.plant(p.level(), p, glaive, p.blockPosition().offset(0, 0, far));
        p.setItemInHand(InteractionHand.MAIN_HAND, glaive);
        say((PathfindersGlaive.planted(glaive) ? "PASS" : "FAIL") + " glaive planted " + far + " blocks out");
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!ON) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!hidden) {
            hidden = true;
            GLFW.glfwSetWindowPos(mc.getWindow().handle(), -4000, -4000);
        }
        if (mc.level == null || mc.player == null) {
            if (!started && mc.gui.screen() instanceof TitleScreen) {
                started = true;
                create(mc);
            }
            return;
        }
        if (mc.gui.screen() != null && index < SCENES.size()) {
            mc.gui.setScreen(null);
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            return;
        }
        ticks++;
        if (step == 0) {
            if (ticks == 2) {
                server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                    "time set noon"));
                server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                    "gamerule doDaylightCycle false"));
                server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                    "gamerule advance_time false"));
            }
            if (ticks >= 100) {
                next(1);
            }
            return;
        }
        if (index >= SCENES.size()) {
            if (ticks == 20) {
                say("DONE failed=" + failed);
                mc.stop();
            }
            return;
        }
        Scene scene = SCENES.get(index);
        if (step == 1) {
            mc.options.setCameraType(scene.camera);
            server.execute(() -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                p.snapTo(p.getX(), p.getY(), p.getZ(), scene.yRot, scene.xRot);
                scene.setup.accept(p);
            });
            mc.player.setYRot(scene.yRot);
            mc.player.setXRot(scene.xRot);
            if (scene.label.startsWith("return") || scene.label.equals("hatchet-throw")) {
                mc.player.getInventory().setSelectedSlot(3);
            }
            next(2);
            return;
        }
        mc.player.setYRot(scene.yRot);
        mc.player.setXRot(scene.xRot);
        if (scene.label.equals("glaive-sounds")) {
            glaiveSounds(mc, server);
        }
        if (scene.label.startsWith("trident-") && scene.label.endsWith("-sounds")) {
            tridentSounds(server, scene.label.substring("trident-".length(), scene.label.length() - "-sounds".length()));
        }
        if (scene.label.equals("chime-sounds")) {
            chimeSounds(server);
        }
        int last = scene.frames[scene.frames.length - 1];
        for (int i = 0; i < scene.frames.length; i++) {
            if (ticks == scene.frames[i]) {
                grab(mc, scene.frames.length == 1 ? scene.label : scene.label + "-" + String.format("%02d", i));
            }
        }
        if (ticks == last + 1) {
            server.execute(() -> scene.check.accept(server.getPlayerList().getPlayers().get(0)));
            index++;
            next(1);
        }
    }

    /** Drives the glaive by its keys: a jab at tick 5, the braced charge from 20 to 60, then plant (70) and recall (80). */
    private static void glaiveSounds(Minecraft mc, MinecraftServer server) {
        if (ticks == 5) {
            net.minecraft.client.KeyMapping.click(mc.options.keyAttack.getKey());
        } else if (ticks == 20) {
            mc.options.keyUse.setDown(true);
        } else if (ticks == 60) {
            mc.options.keyUse.setDown(false);
        } else if (ticks == 70 || ticks == 80) {
            boolean plant = ticks == 70;
            server.execute(() -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                ItemStack glaive = p.getMainHandItem();
                if (plant) {
                    PathfindersGlaive.plant(p.level(), p, glaive, p.blockPosition().offset(2, 0, 2));
                } else {
                    PathfindersGlaive.recall(p.level(), p, glaive);
                }
            });
        }
    }

    private static net.minecraft.world.item.Item tridentItem(String name) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(Ostentum.id(name + "_trident"));
    }

    /** A 5x5, 2 deep pool on a floor of coral blocks, 4-8 blocks ahead and to the left (+X, +Z). */
    private static void pool(ServerPlayer p) {
        net.minecraft.core.BlockPos b = p.blockPosition();
        String from = (b.getX() + 2) + " ", to = (b.getX() + 6) + " ";
        command(p, "fill " + from + (b.getY() - 3) + " " + (b.getZ() + 4) + " " + to + (b.getY() - 3) + " " + (b.getZ() + 8) + " minecraft:brain_coral_block");
        command(p, "fill " + from + (b.getY() - 2) + " " + (b.getZ() + 4) + " " + to + (b.getY() - 1) + " " + (b.getZ() + 8) + " minecraft:water");
    }

    /** Throws {@code stack} from the player's hand as a fully drawn throw, looking along yaw / pitch. */
    private static void throwAt(ServerPlayer p, ItemStack stack, float yaw, float pitch) {
        p.snapTo(p.getX(), p.getY(), p.getZ(), yaw, pitch);
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.getItem().releaseUsing(stack, p.level(), p, stack.getUseDuration(p) - 20);
    }

    private static ItemStack enchanted(ServerPlayer p, String name, ResourceKey<Enchantment> enchantment, int level) {
        ItemStack stack = new ItemStack(tridentItem(name));
        stack.enchant(p.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment), level);
        return stack;
    }

    /** Throws at the pig (5), into the pool (55), onto the grass (105), hits a cod (150), riptides out of the pool (160-170). */
    private static void tridentSounds(MinecraftServer server, String name) {
        int t = ticks;
        if (t != 5 && t != 55 && t != 105 && t != 140 && t != 150 && t != 160 && t != 170 && t != 200) {
            return;
        }
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            net.minecraft.core.BlockPos b = p.blockPosition();
            switch (t) {
                case 5 -> throwAt(p, enchanted(p, name, Enchantments.LOYALTY, 3), 0.0F, 20.0F);
                case 55 -> throwAt(p, enchanted(p, name, Enchantments.LOYALTY, 3), -33.7F, 14.0F);
                case 105 -> throwAt(p, enchanted(p, name, Enchantments.LOYALTY, 3), 180.0F, 55.0F);
                case 140 -> {
                    if (name.equals("coral")) {
                        say((CoralTrident.grow(p.level(), b.offset(3, -3, 5)) ? "PASS" : "FAIL") + " coral grows the pool's coral floor");
                    }
                }
                case 150 -> {
                    Mob cod = EntityTypes.COD.create(p.level(), EntitySpawnReason.COMMAND);
                    cod.snapTo(b.getX() + 4.5, b.getY() - 2.0, b.getZ() + 6.5, 0.0F, 0.0F);
                    cod.setNoAi(true);
                    p.level().addFreshEntity(cod);
                    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tridentItem(name)));
                    cod.hurtServer(p.level(), p.damageSources().playerAttack(p), 1.0F);
                }
                case 160 -> {
                    home = b;
                    p.teleportTo(b.getX() + 4.5, b.getY() - 2.0, b.getZ() + 6.5);
                }
                case 170 -> {
                    throwAt(p, enchanted(p, name, Enchantments.RIPTIDE, 3), 0.0F, -60.0F);
                    say((p.isAutoSpinAttack() ? "PASS " : "FAIL no ") + name + " riptide spin");
                }
                default -> {
                    if (home != null) {
                        p.teleportTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
                    }
                }
            }
        });
    }

    private static net.minecraft.core.BlockPos home;

    /** Marks (5), steps 12 blocks away and rings (10: windup, depart and arrive), then rings with no XP (100: fail). */
    private static void chimeSounds(MinecraftServer server) {
        int t = ticks;
        if (t != 5 && t != 10 && t != 100) {
            return;
        }
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            ItemStack chime = p.getMainHandItem();
            if (t == 5) {
                RecallChime.mark(p.level(), p, chime);
            } else if (t == 10) {
                p.teleportTo(p.getX() + 12, p.getY(), p.getZ());
                chime.getItem().use(p.level(), p, InteractionHand.MAIN_HAND);
            } else {
                p.setGameMode(GameType.SURVIVAL);
                p.setExperienceLevels(0);
                p.setExperiencePoints(0);
                p.totalExperience = 0;
                p.teleportTo(p.getX() + 12, p.getY(), p.getZ());
                chime.getItem().use(p.level(), p, InteractionHand.MAIN_HAND);
            }
        });
    }

    /** Logs every Ostentum sound the client plays (master volume is 0, so they are only logged). */
    @SubscribeEvent
    public static void sound(net.neoforged.neoforge.client.event.sound.PlaySoundEvent event) {
        if (ON && event.getOriginalSound() != null && event.getOriginalSound().getIdentifier().getNamespace().equals(Ostentum.MODID)) {
            say("sound " + event.getOriginalSound().getIdentifier());
        }
    }

    private static void next(int s) {
        step = s;
        ticks = 0;
    }

    private static void grab(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "throwshot-" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1, msg -> {});
    }

    private static void create(Minecraft mc) {
        say("creating a flat world");
        mc.options.pauseOnLostFocus = false;
        mc.options.renderDistance().set(6);
        mc.options.simulationDistance().set(6);
        String name = "throwshot" + System.currentTimeMillis() / 1000;
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE,
            new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(20261106L, false, false),
            provider -> provider.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
            new TitleScreen());
    }
}
