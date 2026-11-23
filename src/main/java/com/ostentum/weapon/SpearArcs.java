package com.ostentum.weapon;

import com.ostentum.Ostentum;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

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

    private SpearArcs() {}

    public static void send(ServerLevel level, Vec3 from, Vec3 to, byte kind) {
        Vec3 mid = from.add(to).scale(0.5);
        PacketDistributor.sendToPlayersNear(level, null, mid.x, mid.y, mid.z, 96,
            new Arc((float) from.x, (float) from.y, (float) from.z, (float) to.x, (float) to.y, (float) to.z, kind));
    }

    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(Arc.TYPE, Arc.CODEC, (arc, context) -> com.ostentum.client.LightningFx.arc(arc));
    }

    public record Arc(float fx, float fy, float fz, float tx, float ty, float tz, byte kind) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Arc> TYPE = new CustomPacketPayload.Type<>(Ostentum.id("spear_arc"));
        public static final StreamCodec<ByteBuf, Arc> CODEC = StreamCodec.ofMember(Arc::write, Arc::read);

        public Vec3 from() {
            return new Vec3(fx, fy, fz);
        }

        public Vec3 to() {
            return new Vec3(tx, ty, tz);
        }

        private void write(ByteBuf buf) {
            buf.writeFloat(fx).writeFloat(fy).writeFloat(fz).writeFloat(tx).writeFloat(ty).writeFloat(tz).writeByte(kind);
        }

        private static Arc read(ByteBuf buf) {
            return new Arc(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readByte());
        }

        @Override
        public CustomPacketPayload.Type<Arc> type() {
            return TYPE;
        }
    }
}
