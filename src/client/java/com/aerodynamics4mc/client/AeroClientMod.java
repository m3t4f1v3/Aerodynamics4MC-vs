package com.aerodynamics4mc.client;

import com.aerodynamics4mc.api.AeroWindSample;
import com.aerodynamics4mc.api.SamplePolicy;
import com.aerodynamics4mc.net.AeroCoarseWindPayload;
import com.aerodynamics4mc.net.AeroFlowPayload;
import com.aerodynamics4mc.net.AeroFlowAnalysisPayload;
import com.aerodynamics4mc.net.AeroRuntimeStatePayload;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;

public final class AeroClientMod implements ClientModInitializer {
    private static AeroClientMod instance;
    private final AeroVisualizer visualizer = new AeroVisualizer();
    private final IrisWindBridge irisWindBridge = new IrisWindBridge(visualizer);
    private final ClientL2Solver clientL2Solver = new ClientL2Solver(visualizer);

    @Override
    public void onInitializeClient() {
        instance = this;
        ClientPlayNetworking.registerGlobalReceiver(AeroRuntimeStatePayload.ID, this::onRuntimeState);
        ClientPlayNetworking.registerGlobalReceiver(AeroFlowPayload.ID, this::onFlowField);
        ClientPlayNetworking.registerGlobalReceiver(AeroCoarseWindPayload.ID, this::onCoarseWindField);
        ClientPlayNetworking.registerGlobalReceiver(AeroFlowAnalysisPayload.ID, this::onFlowAnalysis);
        visualizer.initialize();
        irisWindBridge.initialize();
        clientL2Solver.initialize();
    }

    public static AeroWindSample sampleFlow(ClientWorld world, Vec3d position) {
        return sampleFlow(world, position, SamplePolicy.VISUAL_LOCAL_FIRST);
    }

    public static AeroWindSample sampleFlow(ClientWorld world, Vec3d position, SamplePolicy policy) {
        AeroClientMod active = instance;
        if (active == null || world == null) {
            return AeroWindSample.ZERO;
        }
        return active.visualizer.sampleFlow(world.getRegistryKey().getValue(), position, policy);
    }

    public static Vec3d sampleWind(ClientWorld world, Vec3d position) {
        return sampleFlow(world, position).velocity();
    }

    private void onRuntimeState(AeroRuntimeStatePayload payload, ClientPlayNetworking.Context context) {
        context.client().execute(() -> {
            visualizer.onRuntimeState(new AeroVisualizer.AeroFlowState(
                payload.streamingEnabled(),
                payload.renderVelocityVectors(),
                payload.renderStreamlines()
            ));
            irisWindBridge.onRuntimeState(payload.streamingEnabled());
            clientL2Solver.onRuntimeState(payload.streamingEnabled());
        });
    }

    private void onFlowField(AeroFlowPayload payload, ClientPlayNetworking.Context context) {
        context.client().execute(() -> {
            visualizer.onFlowField(payload);
            irisWindBridge.markDirty();
        });
    }

    private void onCoarseWindField(AeroCoarseWindPayload payload, ClientPlayNetworking.Context context) {
        context.client().execute(() -> {
            visualizer.onCoarseWindField(payload);
            clientL2Solver.onCoarseWindField(payload);
            irisWindBridge.markDirty();
        });
    }

    private void onFlowAnalysis(AeroFlowAnalysisPayload payload, ClientPlayNetworking.Context context) {
        context.client().execute(() -> visualizer.onFlowAnalysis(payload));
    }
}
