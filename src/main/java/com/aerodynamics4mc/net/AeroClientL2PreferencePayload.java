package com.aerodynamics4mc.net;

import com.aerodynamics4mc.ModBlocks;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record AeroClientL2PreferencePayload(boolean localL2Enabled) implements CustomPayload {
    public static final CustomPayload.Id<AeroClientL2PreferencePayload> ID =
        new CustomPayload.Id<>(Identifier.of(ModBlocks.MOD_ID, "client_l2_preference"));
    public static final PacketCodec<RegistryByteBuf, AeroClientL2PreferencePayload> CODEC =
        PacketCodec.of(AeroClientL2PreferencePayload::write, AeroClientL2PreferencePayload::new);

    private AeroClientL2PreferencePayload(RegistryByteBuf buf) {
        this(buf.readBoolean());
    }

    private void write(RegistryByteBuf buf) {
        buf.writeBoolean(localL2Enabled);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
