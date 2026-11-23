package com.ostentum.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.BiConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/** 1.21.1 stand-in for 26.x's {@code submitCustomGeometry}: draws into the buffer of {@code type} at the pose's top. */
final class Geom {
    static void draw(PoseStack pose, MultiBufferSource buffers, RenderType type, BiConsumer<PoseStack.Pose, VertexConsumer> geometry) {
        geometry.accept(pose.last(), buffers.getBuffer(type));
    }

    private Geom() {}
}
