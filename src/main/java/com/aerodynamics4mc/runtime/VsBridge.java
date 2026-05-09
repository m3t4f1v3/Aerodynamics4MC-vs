package com.aerodynamics4mc.runtime;

import com.aerodynamics4mc.api.AeroWindSample;
import com.aerodynamics4mc.api.SamplePolicy;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

/**
 * Soft-dependency facade for Valkyrien Skies. This class itself has zero direct
 * references to VS types so it always loads cleanly, even when VS is absent.
 *
 * Every public method routes into {@link VsBridgeImpl} only after verifying the
 * mod and its API classes are both present at runtime. {@code VsBridgeImpl} is
 * the only class that imports VS types; resolving it triggers VS class loading,
 * which is why we never reference it outside the gate.
 */
public final class VsBridge {
    private static final String VS_MOD_ID = "valkyrienskies";
    private static volatile Boolean cachedAvailable;
    private static volatile boolean attachmentRegistered;

    private VsBridge() {
    }

    public static boolean available() {
        Boolean cached = cachedAvailable;
        if (cached != null) {
            return cached;
        }
        boolean loaded;
        try {
            loaded = ModList.get().isLoaded(VS_MOD_ID);
        } catch (Throwable ignored) {
            loaded = false;
        }
        cachedAvailable = loaded;
        return loaded;
    }

    /**
     * Register the per-ship aerodynamic-force attachment with VS Core. Idempotent.
     */
    public static void ensureAttachmentRegistered() {
        if (!available() || attachmentRegistered) {
            return;
        }
        try {
            VsBridgeImpl.registerAttachment();
            attachmentRegistered = true;
        } catch (Throwable t) {
            attachmentRegistered = true; // don't keep retrying on permanent failure
        }
    }

    /**
     * Marks the worldspace cells that are occupied by VS ship blocks as obstacles.
     * Called after the standard section build has filled the obstacle field for
     * non-ship blocks.
     *
     * @param obstacle  16^3 obstacle field, indexed by {@code y*256 + z*16 + x}
     * @param sectionSize edge length of the section ({@code 16})
     */
    public static void augmentSectionWithShipObstacles(
        ServerLevel level,
        BlockPos sectionOrigin,
        float[] obstacle,
        int sectionSize
    ) {
        if (!available()) {
            return;
        }
        try {
            VsBridgeImpl.augmentSectionWithShipObstacles(level, sectionOrigin, obstacle, sectionSize);
        } catch (Throwable ignored) {
        }
    }

    /**
     * For every loaded ship in every dimension, sample the wind at its worldspace
     * center, compute aerodynamic force/torque, and stash the result in a per-ship
     * attachment to be applied on the next physics tick.
     */
    public static void tickShipForces(MinecraftServer server, WindSampler sampler, long gameTick) {
        if (!available() || server == null || sampler == null) {
            return;
        }
        ensureAttachmentRegistered();
        try {
            VsBridgeImpl.tickShipForces(server, sampler, gameTick);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Sampler used by {@link #tickShipForces} to query the wind field at the
     * ship's worldspace position. Indirected through this interface so the
     * facade does not need to import {@link com.aerodynamics4mc.runtime.AeroServerRuntime}.
     */
    @FunctionalInterface
    public interface WindSampler {
        AeroWindSample sample(ServerLevel level, Vec3 position, SamplePolicy policy);
    }

    /** Visible for {@link VsBridgeImpl}; convenience shim around the sampler. */
    static AeroWindSample sample(WindSampler sampler, ServerLevel level, Vec3 position) {
        return sampler.sample(level, position, SamplePolicy.GAMEPLAY_SERVER_ONLY);
    }
}
