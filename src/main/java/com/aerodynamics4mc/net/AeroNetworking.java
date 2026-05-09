package com.aerodynamics4mc.net;

import java.util.Optional;

import com.aerodynamics4mc.runtime.AeroServerRuntime;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

public final class AeroNetworking {
    private static int id = 0;

    private AeroNetworking() {
    }

    public static void registerChannel(SimpleChannel channel) {
        channel.registerMessage(
            id++,
            AeroClientL2PreferencePayload.class,
            (msg, buf) -> msg.toBytes(buf),
            AeroClientL2PreferencePayload::new,
            (msg, ctx) -> {
                NetworkEvent.Context context = ctx.get();
                ServerPlayer sender = context.getSender();
                if (sender != null) {
                    context.enqueueWork(() ->
                        AeroServerRuntime.handleClientL2Preference(sender, msg.localL2Enabled));
                }
                context.setPacketHandled(true);
            },
            Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        channel.registerMessage(
            id++,
            AeroFlowPayload.class,
            (msg, buf) -> msg.toBytes(buf),
            AeroFlowPayload::new,
            (msg, ctx) -> {
                NetworkEvent.Context context = ctx.get();
                context.enqueueWork(() -> AeroClientPayloadHandler.flow.accept(msg));
                context.setPacketHandled(true);
            },
            Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        channel.registerMessage(
            id++,
            AeroCoarseWindPayload.class,
            (msg, buf) -> msg.toBytes(buf),
            AeroCoarseWindPayload::new,
            (msg, ctx) -> {
                NetworkEvent.Context context = ctx.get();
                context.enqueueWork(() -> AeroClientPayloadHandler.coarseWind.accept(msg));
                context.setPacketHandled(true);
            },
            Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        channel.registerMessage(
            id++,
            AeroFlowAnalysisPayload.class,
            (msg, buf) -> msg.toBytes(buf),
            AeroFlowAnalysisPayload::new,
            (msg, ctx) -> {
                NetworkEvent.Context context = ctx.get();
                context.enqueueWork(() -> AeroClientPayloadHandler.flowAnalysis.accept(msg));
                context.setPacketHandled(true);
            },
            Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        channel.registerMessage(
            id++,
            AeroRuntimeStatePayload.class,
            (msg, buf) -> msg.toBytes(buf),
            AeroRuntimeStatePayload::new,
            (msg, ctx) -> {
                NetworkEvent.Context context = ctx.get();
                context.enqueueWork(() -> AeroClientPayloadHandler.runtimeState.accept(msg));
                context.setPacketHandled(true);
            },
            Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }
}
