package com.aerodynamics4mc.api;

import com.aerodynamics4mc.client.AeroClientMod;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class AeroClientWindApi {
    private AeroClientWindApi() {
    }

    public static AeroWindSample sample(ClientLevel level, Vec3 position) {
        return AeroClientMod.sampleFlow(level, new Vec3(position.x, position.y, position.z));
    }

    public static AeroWindSample sample(ClientLevel level, Vec3 position, SamplePolicy policy) {
        return AeroClientMod.sampleFlow(level, new Vec3(position.x, position.y, position.z), policy);
    }

    public static AeroWindSample sample(ClientLevel level, BlockPos position) {
        return sample(level, center(position));
    }

    public static AeroWindSample sample(ClientLevel level, BlockPos position, SamplePolicy policy) {
        return sample(level, center(position), policy);
    }

    public static Vec3 sampleMeanVelocity(ClientLevel level, Vec3 position) {
        return sample(level, new Vec3(position.x, position.y, position.z)).meanVelocity();
    }

    public static Vec3 sampleEffectiveVelocity(ClientLevel level, Vec3 position) {
        return sample(level, new Vec3(position.x, position.y, position.z)).effectiveVelocity();
    }

    private static Vec3 center(BlockPos position) {
        if (position == null) {
            return Vec3.ZERO;
        }
           return new Vec3(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5);
    }
}
