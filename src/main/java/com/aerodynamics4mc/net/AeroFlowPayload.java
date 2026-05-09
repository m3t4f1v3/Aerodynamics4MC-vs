package com.aerodynamics4mc.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;

public class AeroFlowPayload {
    private static final int MAX_PACKED_SHORTS = 1_048_576;
    private static final int MAX_BYTES = 4_194_304;

    public final ResourceLocation dimensionId;
    public final BlockPos origin;
    public final int sampleStride;
    public final short[] packedFlow;
    public final byte[] packedFlowBytes;

    public AeroFlowPayload(ResourceLocation dimensionId, BlockPos origin, int sampleStride, short[] packedFlow, byte[] packedFlowBytes) {
        this.dimensionId = dimensionId; this.origin = origin; this.sampleStride = sampleStride; this.packedFlow = packedFlow; this.packedFlowBytes = packedFlowBytes;
    }

    public AeroFlowPayload(FriendlyByteBuf buf) {
        this(buf.readResourceLocation(), buf.readBlockPos(), buf.readVarInt(), readShortArray(buf), buf.readByteArray(MAX_BYTES));
    }

    public static AeroFlowPayload fromPackedBytes(ResourceLocation dimensionId, BlockPos origin, int sampleStride, short[] packedFlow, byte[] packedFlowBytes) {
        return new AeroFlowPayload(dimensionId, origin, sampleStride, packedFlow, packedFlowBytes);
    }

    public static byte[] encodePackedFlow(short[] packedFlow) {
        short[] safePackedFlow = packedFlow == null ? new short[0] : packedFlow;
        byte[] bytes = new byte[safePackedFlow.length * Short.BYTES];
        for (int i = 0; i < safePackedFlow.length; i++) {
            short value = safePackedFlow[i];
            int base = i * Short.BYTES;
            bytes[base] = (byte) ((value >>> 8) & 0xFF);
            bytes[base + 1] = (byte) (value & 0xFF);
        }
        return bytes;
    }

    private static short[] readShortArray(FriendlyByteBuf buf) { int length = buf.readVarInt(); if (length < 0 || length > MAX_PACKED_SHORTS) throw new IllegalArgumentException("Invalid array length: " + length); short[] data = new short[length]; for (int i = 0; i < length; i++) data[i] = buf.readShort(); return data; }

    public void toBytes(FriendlyByteBuf buf) { buf.writeResourceLocation(dimensionId); buf.writeBlockPos(origin); buf.writeVarInt(sampleStride); buf.writeVarInt(packedFlow.length); for (short value : packedFlow) buf.writeShort(value); buf.writeByteArray(packedFlowBytes); }
}