package com.aerodynamics4mc.net;

import com.aerodynamics4mc.ModBlocks;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record AeroCoarseWindPayload(
    Identifier dimensionId,
    BlockPos origin,
    int cellSize,
    int sizeX,
    int sizeY,
    int sizeZ,
    long serverTick,
    short[] packedFlow
) implements CustomPayload {
    private static final int MAX_PACKED_FLOW_SHORTS = 1_048_576;

    public static final CustomPayload.Id<AeroCoarseWindPayload> ID =
        new CustomPayload.Id<>(Identifier.of(ModBlocks.MOD_ID, "coarse_wind"));
    public static final PacketCodec<RegistryByteBuf, AeroCoarseWindPayload> CODEC =
        PacketCodec.of(AeroCoarseWindPayload::write, AeroCoarseWindPayload::new);

    private AeroCoarseWindPayload(RegistryByteBuf buf) {
        this(
            buf.readIdentifier(),
            buf.readBlockPos(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readVarLong(),
            readPackedFlow(buf)
        );
    }

    private static short[] readPackedFlow(RegistryByteBuf buf) {
        int length = buf.readVarInt();
        if (length < 0 || length > MAX_PACKED_FLOW_SHORTS) {
            throw new IllegalArgumentException("Invalid coarse wind payload length: " + length);
        }
        short[] data = new short[length];
        for (int i = 0; i < length; i++) {
            data[i] = buf.readShort();
        }
        return data;
    }

    private void write(RegistryByteBuf buf) {
        buf.writeIdentifier(dimensionId);
        buf.writeBlockPos(origin);
        buf.writeVarInt(cellSize);
        buf.writeVarInt(sizeX);
        buf.writeVarInt(sizeY);
        buf.writeVarInt(sizeZ);
        buf.writeVarLong(serverTick);
        buf.writeVarInt(packedFlow.length);
        for (short v : packedFlow) {
            buf.writeShort(v);
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
