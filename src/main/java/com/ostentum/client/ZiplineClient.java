package com.ostentum.client;

import com.fundamentum.curve.Curve;
import com.fundamentum.curve.client.TubeMesh;
import com.fundamentum.api.ItemHud;
import com.mojang.blaze3d.vertex.PoseStack;
import com.ostentum.Ostentum;
import com.ostentum.weapon.EngineersCrossbow;
import com.ostentum.weapon.Zipline;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;

/**
 * The Engineer's Crossbow's client side (1.9.4): the zipline's rope (a sagging tube through Fundamentum's
 * {@link TubeMesh}), the invisible rider seat, the hanging pose (mixin.ZiplineArmsMixin) and
 * the crossbow's lines in Fundamentum's item HUD while it is held: mode, bolts loaded, rope anchor state, ammo and its count.
 */
@Mod.EventBusSubscriber(modid = Ostentum.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ZiplineClient {
    static final ResourceLocation ROPE = Ostentum.id("textures/entity/zipline_rope.png");

    private ZiplineClient() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EngineersCrossbow.ZIPLINE.get(), RopeRenderer::new);
        event.registerEntityRenderer(EngineersCrossbow.RIDER.get(), NoopRenderer::new);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        ItemHud.registerItem(EngineersCrossbow.CROSSBOW.get(), ZiplineClient::hudLines);
    }

    // ------------------------------------------------------------------------------------------------ the rope
    static final class RopeRenderer extends EntityRenderer<Zipline> {
        RopeRenderer(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public boolean shouldRender(Zipline line, Frustum culler, double camX, double camY, double camZ) {
            Vec3 a = line.a();
            Vec3 b = line.b();
            return culler.isVisible(new AABB(a, b).inflate(0.5).expandTowards(0, -Zipline.SAG * line.length() - 0.5, 0));
        }

        @Override
        public void render(Zipline line, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light) {
            Vec3 a = line.a();
            Vec3 b = line.b();
            Vec3 origin = line.getPosition(partialTicks);
            int n = Math.max(4, (int) Math.ceil(line.length() * 2));
            List<Curve.Frame> frames = new ArrayList<>(n + 1);
            int[] lights = new int[n + 1];
            double dist = 0;
            Vec3 prev = null;
            for (int i = 0; i <= n; i++) {
                double t = (double) i / n;
                Vec3 p = Zipline.point(a, b, t);
                Vec3 tan = Zipline.tangent(a, b, t).normalize();
                Vec3 side = tan.cross(new Vec3(0, 1, 0));
                side = side.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : side.normalize();
                Vec3 up = side.cross(tan).normalize();
                if (prev != null) {
                    dist += p.distanceTo(prev);
                }
                prev = p;
                frames.add(new Curve.Frame(v(p.subtract(origin)), v(tan), v(up), v(side), dist));
                lights[i] = LevelRenderer.getLightColor(line.level(), BlockPos.containing(p));
            }
            TubeMesh.tube(poseStack.last(), buffers.getBuffer(RenderType.entityCutout(ROPE)), frames, 0.045F, 6, 2.0F, 0xFFFFFFFF, lights, false);
            super.render(line, yaw, partialTicks, poseStack, buffers, light);
        }

        private static Curve.Vec v(Vec3 p) {
            return new Curve.Vec(p.x, p.y, p.z);
        }

        @Override
        public ResourceLocation getTextureLocation(Zipline line) {
            return ROPE;
        }
    }

    // ------------------------------------------------------------------------------------------------ the HUD
    /**
     * The crossbow's lines in Fundamentum's item HUD (right of the hotbar, placed and styled by the player's
     * fundamentum-hud-client.toml): the mode and the bolts loaded ("Combat 3/5", red at 0), in rope mode whether the
     * first anchor is set, then the ammo it takes and how many you carry ("Arrow x23", red "No ammo").
     */
    static List<Component> hudLines(Player player, ItemStack stack) {
        boolean rope = EngineersCrossbow.ropeMode(stack);
        ItemStack ammo = player.getProjectile(stack);
        int count = 0;
        if (!ammo.isEmpty()) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (ItemStack.isSameItemSameTags(s, ammo)) {
                    count += s.getCount();
                }
            }
        }
        int loaded = EngineersCrossbow.loadedCount(stack);
        int capacity = rope ? 1 : EngineersCrossbow.MAGAZINE;
        Component mode = Component.translatable("hud.ostentum.engineers_crossbow." + (rope ? "rope" : "combat"))
            .append(Component.literal("  " + loaded + "/" + capacity).withStyle(loaded == 0 ? ChatFormatting.RED : ChatFormatting.GRAY));
        if (rope) {
            boolean anchored = EngineersCrossbow.anchored(stack);
            mode = mode.copy().append("  ").append(Component.translatable(anchored
                ? "hud.ostentum.engineers_crossbow.anchor_set" : "hud.ostentum.engineers_crossbow.anchor_none")
                .withStyle(anchored ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        }
        String countText = count == 0 && player.getAbilities().instabuild ? "\u221e" : String.valueOf(count);
        Component ammoLine = ammo.isEmpty()
            ? Component.translatable("hud.ostentum.engineers_crossbow.no_ammo").withStyle(ChatFormatting.RED)
            : ammo.getHoverName().copy().withStyle(ChatFormatting.GRAY).append(Component.literal(" \u00d7" + countText).withStyle(ChatFormatting.WHITE));
        return List.of(mode, ammoLine);
    }
}
