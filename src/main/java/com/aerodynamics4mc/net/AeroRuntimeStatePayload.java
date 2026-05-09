package com.aerodynamics4mc.net;

import net.minecraft.network.FriendlyByteBuf;

public class AeroRuntimeStatePayload {
    public final boolean streamingEnabled;
    public final boolean renderVelocityVectors;
    public final boolean renderStreamlines;

    public AeroRuntimeStatePayload(boolean streamingEnabled, boolean renderVelocityVectors, boolean renderStreamlines) {
        this.streamingEnabled = streamingEnabled;
        this.renderVelocityVectors = renderVelocityVectors;
        this.renderStreamlines = renderStreamlines;
    }

    public AeroRuntimeStatePayload(FriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(streamingEnabled);
        buf.writeBoolean(renderVelocityVectors);
        buf.writeBoolean(renderStreamlines);
    }
}
