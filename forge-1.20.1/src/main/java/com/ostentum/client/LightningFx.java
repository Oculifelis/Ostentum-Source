package com.ostentum.client;

import com.ostentum.Ostentum;
import com.ostentum.weapon.SpearArcs;
import com.ostentum.weapon.WeathervaneSpear;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/**
 * The Weathervane Spear's lightning (1.9.4), as on 26.1.2: forked, flickering bolts with a white core, a cyan body and a
 * soft glow, a glare and spark streaks where they land, and a crackle on a charged spear; vanilla's lightning render
 * type, full bright. The geometry is {@link Bolts}.
 */
@Mod.EventBusSubscriber(modid = Ostentum.MODID, value = Dist.CLIENT)
public final class LightningFx {
    private record Live(Vec3 from, Vec3 to, byte kind, long start, int seed) {}

    private static final List<Live> LIVE = new ArrayList<>();
    private static final RandomSource SEEDS = RandomSource.create();

    private LightningFx() {}

    /** A bolt from the server ({@link SpearArcs}). */
    public static void arc(SpearArcs.Arc arc) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            LIVE.add(new Live(arc.from(), arc.to(), arc.kind(), level.getGameTime(), SEEDS.nextInt()));
        }
    }

    @SubscribeEvent
    public static void draw(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            LIVE.clear();
            return;
        }
        float pt = event.getPartialTick();
        double now = level.getGameTime() + pt;
        Vec3 cam = event.getCamera().getPosition();
        Bolts mesh = new Bolts(cam);
        LIVE.removeIf(l -> now - l.start > Bolts.life(l.kind) || now < l.start - 40);
        for (Live l : LIVE) {
            mesh.strike(l.from, l.to, l.kind, (float) (now - l.start), l.seed);
        }
        for (Player player : level.players()) {
            if (!WeathervaneSpear.showsCharge(player.getMainHandItem()) || player.position().distanceToSqr(cam) > 64 * 64 || player.isInvisible()) {
                continue;
            }
            boolean firstPerson = player == mc.player && mc.options.getCameraType() == CameraType.FIRST_PERSON;
            Vec3[] spear = spear(player, pt, firstPerson, cam);
            mesh.crackle(spear[0], spear[1], level.getGameTime(), player.getId(), firstPerson ? 0.6F : 1.0F);
        }
        if (!mesh.isEmpty()) {
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            mesh.emit(event.getPoseStack().last(), buffers.getBuffer(RenderType.lightning()));
            buffers.endBatch(RenderType.lightning());
        }
    }

    /** Roughly where a held spear runs, butt (hand) to head, in world space. */
    static Vec3[] spear(Player player, float pt, boolean firstPerson, Vec3 cam) {
        Vec3 look = player.getViewVector(pt);
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(look).normalize();
        if (firstPerson) {
            Vec3 hand = cam.add(look.scale(0.55)).add(right.scale(0.34)).add(up.scale(-0.32));
            return new Vec3[] {hand, hand.add(look.scale(0.9)).add(up.scale(0.12))};
        }
        float yaw = Mth.rotLerp(pt, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 bodyRight = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        Vec3 hand = player.getPosition(pt).add(0, player.isCrouching() ? 0.75 : 0.95, 0).add(bodyRight.scale(0.38));
        return new Vec3[] {hand.subtract(look.scale(0.4)), hand.add(look.scale(1.5))};
    }
}
