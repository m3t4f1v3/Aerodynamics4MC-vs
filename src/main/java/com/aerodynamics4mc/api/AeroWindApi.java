package com.aerodynamics4mc.api;

import com.aerodynamics4mc.runtime.AeroServerRuntime;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class AeroWindApi {
    private AeroWindApi() {
    }

    public static AeroWindSample sample(ServerLevel level, Vec3 position) {
        return AeroServerRuntime.sampleFlow(level, position);
    }

    public static AeroWindSample sample(ServerLevel level, Vec3 position, SamplePolicy policy) {
        return AeroServerRuntime.sampleFlow(level, position, policy);
    }

    public static AeroWindSample sample(ServerLevel level, BlockPos position) {
        return AeroServerRuntime.sampleFlow(level, position);
    }

    public static AeroWindSample sample(ServerLevel level, BlockPos position, SamplePolicy policy) {
        return AeroServerRuntime.sampleFlow(level, position, policy);
    }

    public static AeroWindSample sample(ServerPlayer player, Vec3 position) {
        return AeroServerRuntime.sampleFlow(player, position);
    }

    public static AeroWindSample sample(ServerPlayer player, Vec3 position, SamplePolicy policy) {
        return AeroServerRuntime.sampleFlow(player, position, policy);
    }

    public static AeroWindSample sample(ServerPlayer player, BlockPos position) {
        return AeroServerRuntime.sampleFlow(player, position);
    }

    public static AeroWindSample sample(ServerPlayer player, BlockPos position, SamplePolicy policy) {
        return AeroServerRuntime.sampleFlow(player, position, policy);
    }

    public static GameplayWindSample sampleGameplay(ServerLevel level, Vec3 position) {
        return AeroServerRuntime.sampleGameplay(level, position);
    }

    public static GameplayWindSample sampleGameplay(ServerLevel level, Vec3 position, SamplePolicy policy) {
        return AeroServerRuntime.sampleGameplay(level, position, policy);
    }

    public static GameplayWindSample sampleGameplay(ServerLevel level, BlockPos position) {
        return AeroServerRuntime.sampleGameplay(level, position);
    }

    public static GameplayWindSample sampleGameplay(ServerLevel level, BlockPos position, SamplePolicy policy) {
        return AeroServerRuntime.sampleGameplay(level, position, policy);
    }

    public static GameplayWindSample sampleGameplay(ServerPlayer player, Vec3 position) {
        return AeroServerRuntime.sampleGameplay(player, position);
    }

    public static GameplayWindSample sampleGameplay(ServerPlayer player, Vec3 position, SamplePolicy policy) {
        return AeroServerRuntime.sampleGameplay(player, position, policy);
    }

    public static GameplayWindSample sampleGameplay(ServerPlayer player, BlockPos position) {
        return AeroServerRuntime.sampleGameplay(player, position);
    }

    public static GameplayWindSample sampleGameplay(ServerPlayer player, BlockPos position, SamplePolicy policy) {
        return AeroServerRuntime.sampleGameplay(player, position, policy);
    }

    public static Vec3 sampleMeanVelocity(ServerLevel level, Vec3 position) {
        return sample(level, position).meanVelocity();
    }

    public static Vec3 sampleEffectiveVelocity(ServerLevel level, Vec3 position) {
        return sample(level, position).effectiveVelocity();
    }

    public static Vec3 sampleMeanVelocity(ServerPlayer player, Vec3 position) {
        return sample(player, position).meanVelocity();
    }

    public static Vec3 sampleEffectiveVelocity(ServerPlayer player, Vec3 position) {
        return sample(player, position).effectiveVelocity();
    }

    public static Vec3 sampleGameplayMeanVelocity(ServerLevel level, Vec3 position) {
        return sampleGameplay(level, position).meanVelocity();
    }

    public static Vec3 sampleGameplayEffectiveVelocity(ServerLevel level, Vec3 position) {
        return sampleGameplay(level, position).effectiveVelocity();
    }

    public static Vec3 sampleGameplayMeanVelocity(ServerPlayer player, Vec3 position) {
        return sampleGameplay(player, position).meanVelocity();
    }

    public static Vec3 sampleGameplayEffectiveVelocity(ServerPlayer player, Vec3 position) {
        return sampleGameplay(player, position).effectiveVelocity();
    }
}
