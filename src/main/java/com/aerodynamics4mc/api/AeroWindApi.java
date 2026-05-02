package com.aerodynamics4mc.api;

import com.aerodynamics4mc.runtime.AeroServerRuntime;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class AeroWindApi {
    private AeroWindApi() {
    }

    public static AeroWindSample sample(ServerWorld world, Vec3d position) {
        return AeroServerRuntime.sampleFlow(world, position);
    }

    public static AeroWindSample sample(ServerWorld world, Vec3d position, SamplePolicy policy) {
        return AeroServerRuntime.sampleFlow(world, position, policy);
    }

    public static AeroWindSample sample(ServerWorld world, BlockPos position) {
        return AeroServerRuntime.sampleFlow(world, position);
    }

    public static AeroWindSample sample(ServerWorld world, BlockPos position, SamplePolicy policy) {
        return AeroServerRuntime.sampleFlow(world, position, policy);
    }

    public static AeroWindSample sample(ServerPlayerEntity player, Vec3d position) {
        return AeroServerRuntime.sampleFlow(player, position);
    }

    public static AeroWindSample sample(ServerPlayerEntity player, Vec3d position, SamplePolicy policy) {
        return AeroServerRuntime.sampleFlow(player, position, policy);
    }

    public static AeroWindSample sample(ServerPlayerEntity player, BlockPos position) {
        return AeroServerRuntime.sampleFlow(player, position);
    }

    public static AeroWindSample sample(ServerPlayerEntity player, BlockPos position, SamplePolicy policy) {
        return AeroServerRuntime.sampleFlow(player, position, policy);
    }

    public static Vec3d sampleMeanVelocity(ServerWorld world, Vec3d position) {
        return sample(world, position).meanVelocity();
    }

    public static Vec3d sampleEffectiveVelocity(ServerWorld world, Vec3d position) {
        return sample(world, position).effectiveVelocity();
    }

    public static Vec3d sampleMeanVelocity(ServerPlayerEntity player, Vec3d position) {
        return sample(player, position).meanVelocity();
    }

    public static Vec3d sampleEffectiveVelocity(ServerPlayerEntity player, Vec3d position) {
        return sample(player, position).effectiveVelocity();
    }
}
