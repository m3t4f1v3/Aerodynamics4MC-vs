package com.aerodynamics4mc.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;

public class AeroFlowAnalysisPayload {
    private static final int MAX_PACKED_FLOW_SHORTS = 1_048_576;
    private static final int MAX_RESIDUAL_BYTES = 4_194_304;

    public final ResourceLocation dimensionId;
    public final BlockPos origin;
    public final int baseSampleStride;
    public final int fullResolution;
    public final float velocityTolerance;
    public final float pressureTolerance;
    public final short[] basePackedFlow;
    public final byte[] residualVx;
    public final byte[] residualVy;
    public final byte[] residualVz;
    public final byte[] residualPressure;

    public AeroFlowAnalysisPayload(ResourceLocation dimensionId, BlockPos origin, int baseSampleStride, int fullResolution, float velocityTolerance, float pressureTolerance, short[] basePackedFlow, byte[] residualVx, byte[] residualVy, byte[] residualVz, byte[] residualPressure) {
        this.dimensionId = dimensionId; this.origin = origin; this.baseSampleStride = baseSampleStride; this.fullResolution = fullResolution; this.velocityTolerance = velocityTolerance; this.pressureTolerance = pressureTolerance; this.basePackedFlow = basePackedFlow; this.residualVx = residualVx; this.residualVy = residualVy; this.residualVz = residualVz; this.residualPressure = residualPressure;
    }

    public AeroFlowAnalysisPayload(FriendlyByteBuf buf) {
        this(buf.readResourceLocation(), buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), readPackedFlow(buf), buf.readByteArray(MAX_RESIDUAL_BYTES), buf.readByteArray(MAX_RESIDUAL_BYTES), buf.readByteArray(MAX_RESIDUAL_BYTES), buf.readByteArray(MAX_RESIDUAL_BYTES));
    }

    private static short[] readPackedFlow(FriendlyByteBuf buf) { int length = buf.readVarInt(); if (length < 0 || length > MAX_PACKED_FLOW_SHORTS) throw new IllegalArgumentException("Invalid flow analysis base length: " + length); short[] data = new short[length]; for (int i = 0; i < length; i++) data[i] = buf.readShort(); return data; }

    public void toBytes(FriendlyByteBuf buf) { buf.writeResourceLocation(dimensionId); buf.writeBlockPos(origin); buf.writeVarInt(baseSampleStride); buf.writeVarInt(fullResolution); buf.writeFloat(velocityTolerance); buf.writeFloat(pressureTolerance); buf.writeVarInt(basePackedFlow.length); for (short value : basePackedFlow) buf.writeShort(value); buf.writeByteArray(residualVx); buf.writeByteArray(residualVy); buf.writeByteArray(residualVz); buf.writeByteArray(residualPressure); }
}