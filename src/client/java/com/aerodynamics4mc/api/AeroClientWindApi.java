package com.aerodynamics4mc.api;

import com.aerodynamics4mc.client.AeroClientMod;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class AeroClientWindApi {
    private AeroClientWindApi() {
    }

    public static AeroWindSample sample(ClientWorld world, Vec3d position) {
        return AeroClientMod.sampleFlow(world, position);
    }

    public static AeroWindSample sample(ClientWorld world, Vec3d position, SamplePolicy policy) {
        return AeroClientMod.sampleFlow(world, position, policy);
    }

    public static AeroWindSample sample(ClientWorld world, BlockPos position) {
        return sample(world, center(position));
    }

    public static AeroWindSample sample(ClientWorld world, BlockPos position, SamplePolicy policy) {
        return sample(world, center(position), policy);
    }

    public static Vec3d sampleMeanVelocity(ClientWorld world, Vec3d position) {
        return sample(world, position).meanVelocity();
    }

    public static Vec3d sampleEffectiveVelocity(ClientWorld world, Vec3d position) {
        return sample(world, position).effectiveVelocity();
    }

    private static Vec3d center(BlockPos position) {
        if (position == null) {
            return Vec3d.ZERO;
        }
        return new Vec3d(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5);
    }
}
