package com.aerodynamics4mc.net;

import java.util.function.Consumer;

public final class AeroClientPayloadHandler {
    public static volatile Consumer<AeroRuntimeStatePayload> runtimeState = msg -> {};
    public static volatile Consumer<AeroFlowPayload> flow = msg -> {};
    public static volatile Consumer<AeroCoarseWindPayload> coarseWind = msg -> {};
    public static volatile Consumer<AeroFlowAnalysisPayload> flowAnalysis = msg -> {};

    private AeroClientPayloadHandler() {
    }
}
