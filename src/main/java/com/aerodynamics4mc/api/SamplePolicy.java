package com.aerodynamics4mc.api;

public enum SamplePolicy {
    VISUAL_LOCAL_FIRST(true, true, true),
    GAMEPLAY_SERVER_ONLY(false, true, true),
    SERVER_AGGREGATED_PREFERRED(false, true, true),
    DIAGNOSTIC_ALL_SOURCES(true, true, true);

    private final boolean allowClientLocalL2;
    private final boolean allowServerAggregatedL2;
    private final boolean allowServerCoarse;

    SamplePolicy(boolean allowClientLocalL2, boolean allowServerAggregatedL2, boolean allowServerCoarse) {
        this.allowClientLocalL2 = allowClientLocalL2;
        this.allowServerAggregatedL2 = allowServerAggregatedL2;
        this.allowServerCoarse = allowServerCoarse;
    }

    public boolean allowClientLocalL2() {
        return allowClientLocalL2;
    }

    public boolean allowServerAggregatedL2() {
        return allowServerAggregatedL2;
    }

    public boolean allowServerCoarse() {
        return allowServerCoarse;
    }
}
