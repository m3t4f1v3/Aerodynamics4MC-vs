package com.aerodynamics4mc.net;

import net.minecraft.network.FriendlyByteBuf;

public class AeroClientL2PreferencePayload {
    public final boolean localL2Enabled;

    public AeroClientL2PreferencePayload(boolean localL2Enabled) {
        this.localL2Enabled = localL2Enabled;
    }

    public AeroClientL2PreferencePayload(FriendlyByteBuf buf) {
        this(buf.readBoolean());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(localL2Enabled);
    }
}
