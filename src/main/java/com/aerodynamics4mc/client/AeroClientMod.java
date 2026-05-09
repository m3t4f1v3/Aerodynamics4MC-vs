package com.aerodynamics4mc.client;

import com.aerodynamics4mc.AeroMod;
import com.aerodynamics4mc.api.AeroWindSample;
import com.aerodynamics4mc.api.SamplePolicy;
import com.aerodynamics4mc.net.AeroClientL2PreferencePayload;
import com.aerodynamics4mc.net.AeroClientPayloadHandler;
import com.aerodynamics4mc.net.AeroCoarseWindPayload;
import com.aerodynamics4mc.net.AeroFlowPayload;
import com.aerodynamics4mc.net.AeroFlowAnalysisPayload;
import com.aerodynamics4mc.net.AeroRuntimeStatePayload;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

public final class AeroClientMod {
    public static AeroClientMod instance;
    private final AeroVisualizer visualizer = new AeroVisualizer();
    private final IrisWindBridge irisWindBridge = new IrisWindBridge(visualizer);
    private final ClientL2Solver clientL2Solver = new ClientL2Solver(visualizer);

    public AeroClientMod() {
        instance = this;
    }

    public void onInitializeClient() {
        visualizer.initialize();
        irisWindBridge.initialize();
        clientL2Solver.initialize();
        AeroClientPayloadHandler.runtimeState = this::onRuntimeState;
        AeroClientPayloadHandler.flow = this::onFlowField;
        AeroClientPayloadHandler.coarseWind = this::onCoarseWindField;
        AeroClientPayloadHandler.flowAnalysis = this::onFlowAnalysis;
    }

    public static AeroWindSample sampleFlow(ClientLevel level, Vec3 position) {
        AeroClientMod active = instance;
        SamplePolicy policy = active != null && active.clientL2Solver.isExperimentalEnabled()
            ? SamplePolicy.CLIENT_LOCAL_PREFERRED
            : SamplePolicy.SERVER_AGGREGATED_PREFERRED;
        return sampleFlow(level, position, policy);
    }

    public static AeroWindSample sampleFlow(ClientLevel level, Vec3 position, SamplePolicy policy) {
        AeroClientMod active = instance;
        if (active == null || level == null) {
            return AeroWindSample.ZERO;
        }
        return active.visualizer.sampleFlow(level.dimension().location(), position, policy);
    }

    public static Vec3 sampleWind(ClientLevel level, Vec3 position) {
        return sampleFlow(level, position).velocity();
    }

    public static void notifyBlockStateChanged(ClientLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
        AeroClientMod active = instance;
        if (active == null) {
            return;
        }
        active.clientL2Solver.onBlockStateChanged(level, pos, oldState, newState);
    }

    public void onRuntimeState(AeroRuntimeStatePayload payload) {
        visualizer.onRuntimeState(new AeroVisualizer.AeroFlowState(
            payload.streamingEnabled,
            payload.renderVelocityVectors,
            payload.renderStreamlines
        ));
        irisWindBridge.onRuntimeState(payload.streamingEnabled);
        clientL2Solver.onRuntimeState(payload.streamingEnabled);
        sendClientL2Preference(clientL2Solver.isExperimentalEnabled() && payload.streamingEnabled);
    }

    public void onFlowField(AeroFlowPayload payload) {
        visualizer.onFlowField(payload);
        irisWindBridge.markDirty();
    }

    public void onCoarseWindField(AeroCoarseWindPayload payload) {
        visualizer.onCoarseWindField(payload);
        clientL2Solver.onCoarseWindField(payload);
        irisWindBridge.markDirty();
    }

    public void onFlowAnalysis(AeroFlowAnalysisPayload payload) {
        visualizer.onFlowAnalysis(payload);
    }

    private void sendClientFeedback(Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendSystemMessage(message);
        }
    }

    void clientL2Status() {
        sendClientFeedback(Component.literal(clientL2Solver.status()));
    }

    void setClientL2Experimental(boolean enabled) {
        clientL2Solver.setExperimentalEnabled(enabled);
        if (enabled) {
            visualizer.clearRemoteFlowFields();
        }
        sendClientL2Preference(enabled);
        sendClientFeedback(Component.literal("Client L2 local solve " + (enabled ? "enabled" : "disabled")));
    }

    void renderStatus() {
        sendClientFeedback(renderStatusText());
    }

    Component renderStatusText() {
        return Component.literal(
            "Render vectors=" + visualizer.renderVelocityVectorsEnabled()
                + " streamlines=" + visualizer.renderStreamlinesEnabled()
        );
    }

    void setRenderVelocityVectors(boolean enabled) {
        visualizer.setRenderVelocityVectors(enabled);
        sendClientFeedback(Component.literal("Render vectors " + (enabled ? "enabled" : "disabled")));
    }

    void setRenderStreamlines(boolean enabled) {
        visualizer.setRenderStreamlines(enabled);
        sendClientFeedback(Component.literal("Render streamlines " + (enabled ? "enabled" : "disabled")));
    }

    void sendClientL2Preference(boolean enabled) {
           AeroMod.CHANNEL.send(PacketDistributor.SERVER.noArg(), new AeroClientL2PreferencePayload(enabled));
    }
}
