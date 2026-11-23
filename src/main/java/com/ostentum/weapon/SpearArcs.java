package com.ostentum.weapon;

import com.ostentum.Ostentum;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * The Weathervane Spear's lightning, sent to the clients near it (1.9.4): a
 * branching bolt from one point to another, or a burst of sparks at one point. Drawing only, {@code client.LightningFx}.
 */
public final class SpearArcs {
    /** The charged jab's bolt from the spear to its target. */
    public static final byte SHOCK = 0;
    /** The hop from the first target to the next. */
    public static final byte HOP = 1;
    /** A jab on a redstone component: a thin bolt. */
    public static final byte JAB = 2;
    /** Sparks bursting at {@code from} (the static coming to charge). */
    public static final byte BURST = 3;

    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        Ostentum.id("spear_arc"), () -> VERSION, VERSION::equals, VERSION::equals);

    private SpearArcs() {}

    public static void send(ServerLevel level, Vec3 from, Vec3 to, byte kind) {
        Vec3 mid = from.add(to).scale(0.5);
        CHANNEL.send(PacketDistributor.NEAR.with(PacketDistributor.TargetPoint.p(mid.x, mid.y, mid.z, 96, level.dimension())),
            new Arc((float) from.x, (float) from.y, (float) from.z, (float) to.x, (float) to.y, (float) to.z, kind));
    }

    static void register() {
        CHANNEL.messageBuilder(Arc.class, 0, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(Arc::write).decoder(Arc::read).consumerMainThread(SpearArcs::handle).add();
    }

    private static void handle(Arc arc, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ostentum.client.LightningFx.arc(arc));
    }

    public record Arc(float fx, float fy, float fz, float tx, float ty, float tz, byte kind) {

        public Vec3 from() {
            return new Vec3(fx, fy, fz);
        }

        public Vec3 to() {
            return new Vec3(tx, ty, tz);
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeFloat(fx).writeFloat(fy).writeFloat(fz).writeFloat(tx).writeFloat(ty).writeFloat(tz).writeByte(kind);
        }

        private static Arc read(FriendlyByteBuf buf) {
            return new Arc(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readByte());
        }
    }
}
