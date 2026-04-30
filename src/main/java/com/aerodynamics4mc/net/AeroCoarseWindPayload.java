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
    short[] packedFlow,
    short[] packedAtmosphere
) implements CustomPayload {
    private static final int MAX_PACKED_FLOW_SHORTS = 1_048_576;
    private static final int MAX_PACKED_ATMOSPHERE_SHORTS = 1_048_576;

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
            readShortArray(buf, MAX_PACKED_FLOW_SHORTS, "coarse wind payload"),
            readShortArray(buf, MAX_PACKED_ATMOSPHERE_SHORTS, "coarse atmosphere payload")
        );
    }

    private static short[] readShortArray(RegistryByteBuf buf, int maxLength, String label) {
        int length = buf.readVarInt();
        if (length < 0 || length > maxLength) {
            throw new IllegalArgumentException("Invalid " + label + " length: " + length);
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
        writeShortArray(buf, packedFlow);
        writeShortArray(buf, packedAtmosphere);
    }

    private static void writeShortArray(RegistryByteBuf buf, short[] values) {
        short[] safeValues = values == null ? new short[0] : values;
        buf.writeVarInt(safeValues.length);
        for (short v : safeValues) {
            buf.writeShort(v);
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
