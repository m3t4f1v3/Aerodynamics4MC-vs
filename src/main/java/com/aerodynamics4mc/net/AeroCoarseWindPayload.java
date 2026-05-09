package com.aerodynamics4mc.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;

public class AeroCoarseWindPayload {
    private static final int MAX_PACKED_SHORTS = 1_048_576;

    public final ResourceLocation dimensionId;
    public final BlockPos origin;
    public final int cellSize;
    public final int sizeX;
    public final int sizeY;
    public final int sizeZ;
    public final long serverTick;
    public final short[] packedFlow;
    public final short[] packedAtmosphere;

    public AeroCoarseWindPayload(ResourceLocation dimensionId, BlockPos origin, int cellSize, int sizeX, int sizeY, int sizeZ, long serverTick, short[] packedFlow, short[] packedAtmosphere) {
        this.dimensionId = dimensionId; this.origin = origin; this.cellSize = cellSize; this.sizeX = sizeX; this.sizeY = sizeY; this.sizeZ = sizeZ; this.serverTick = serverTick; this.packedFlow = packedFlow; this.packedAtmosphere = packedAtmosphere;
    }

    public AeroCoarseWindPayload(FriendlyByteBuf buf) {
        this(buf.readResourceLocation(), buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readLong(), readShortArray(buf), readShortArray(buf));
    }

    private static short[] readShortArray(FriendlyByteBuf buf) { int length = buf.readVarInt(); if (length < 0 || length > MAX_PACKED_SHORTS) throw new IllegalArgumentException("Invalid array length: " + length); short[] data = new short[length]; for (int i = 0; i < length; i++) data[i] = buf.readShort(); return data; }

    public void toBytes(FriendlyByteBuf buf) { buf.writeResourceLocation(dimensionId); buf.writeBlockPos(origin); buf.writeVarInt(cellSize); buf.writeVarInt(sizeX); buf.writeVarInt(sizeY); buf.writeVarInt(sizeZ); buf.writeLong(serverTick); buf.writeVarInt(packedFlow.length); for (short value : packedFlow) buf.writeShort(value); buf.writeVarInt(packedAtmosphere.length); for (short value : packedAtmosphere) buf.writeShort(value); }
}