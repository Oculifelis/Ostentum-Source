package com.ostentum;

import com.fundamentum.moon.NightEvent;
import com.fundamentum.moon.NightEvents;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The Pillager Siege (fundamentum/night_events/siege.json): on a night it rolls, pillagers and vindicators come at
 * players near a village in up to six waves, about forty seconds apart. Players who killed a raider and saw the
 * night through to dawn are Heroes of the Village for two minutes. The last wave has a captain, who may drop the
 * Fundamentum disc ({@link MoonDiscs}); the siege's music is client side ({@code client/MoonMusic}).
 */
final class Siege implements NightEvents.Listener {
    static final String EVENT = "ostentum:siege";
    static final String TAG = "ostentum_siege";
    /** The last wave is led by a captain (a patrol leader, ominous banner and all), who may drop the Fundamentum disc. */
    static final String CAPTAIN_TAG = "ostentum_siege_captain";
    static final int WAVES = 6;
    static final int WAVE_TICKS = 800;
    static final int HERO_TICKS = 2400;

    /** Per level: waves spawned so far and the game time of the next one. */
    private record State(int waves, long next) {}

    private static final Map<String, State> STATE = new ConcurrentHashMap<>();
    private static final Set<UUID> DEFENDERS = ConcurrentHashMap.newKeySet();

    private Siege() {}

    static void init() {
        NightEvents.addListener(new Siege());
    }

    private static String key(ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    @Override
    public void onStart(ServerLevel level, NightEvent event) {
        if (event.id().equals(EVENT)) {
            STATE.put(key(level), new State(0, level.getGameTime() + 200));
            DEFENDERS.clear();
        }
    }

    @Override
    public void whileUp(ServerLevel level, NightEvent event) {
        if (!event.id().equals(EVENT)) {
            return;
        }
        State state = STATE.computeIfAbsent(key(level), k -> new State(0, level.getGameTime()));
        if (state.waves() >= WAVES || level.getGameTime() < state.next()) {
            return;
        }
        int size = Math.min(8, 3 + state.waves());
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && !player.isCreative() && level.isCloseToVillage(player.blockPosition(), 3)) {
                boolean last = state.waves() == WAVES - 1;
                for (int i = 0; i < size; i++) {
                    spawn(level, player, i % 3 == 2 ? EntityTypes.VINDICATOR : EntityTypes.PILLAGER, last && i == 0);
                }
            }
        }
        STATE.put(key(level), new State(state.waves() + 1, level.getGameTime() + WAVE_TICKS));
    }

    private static void spawn(ServerLevel level, ServerPlayer player, EntityType<? extends Mob> type, boolean captain) {
        RandomSource random = level.getRandom();
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = 24 + random.nextDouble() * 8;
        int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
        int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
        if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
            return;
        }
        BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        Mob mob = type.create(level, EntitySpawnReason.EVENT);
        if (mob == null) {
            return;
        }
        mob.snapTo(pos, random.nextFloat() * 360.0F, 0.0F);
        if (captain && mob instanceof PatrollingMonster leader && leader.canBeLeader()) {
            leader.setPatrolLeader(true); // finalizeSpawn then gives it the ominous banner
            mob.addTag(CAPTAIN_TAG);
        }
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
        mob.addTag(TAG);
        mob.setPersistenceRequired();
        mob.setTarget(player);
        level.addFreshEntity(mob);
    }

    @Override
    public void onKill(ServerLevel level, ServerPlayer player, LivingEntity dead, NightEvent event) {
        if (event.id().equals(EVENT) && dead.entityTags().contains(TAG)) {
            DEFENDERS.add(player.getUUID());
        }
    }

    @Override
    public void onEnd(ServerLevel level, NightEvent event, String outcome) {
        if (!event.id().equals(EVENT)) {
            return;
        }
        STATE.remove(key(level));
        if (NightEvents.DAWN.equals(outcome)) {
            for (UUID id : DEFENDERS) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
                if (player != null) {
                    player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, HERO_TICKS, 0, false, false, true));
                }
            }
        }
        DEFENDERS.clear();
    }
}
