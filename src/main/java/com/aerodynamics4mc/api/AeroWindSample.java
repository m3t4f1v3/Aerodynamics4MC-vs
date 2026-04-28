package com.aerodynamics4mc.api;

import net.minecraft.util.math.Vec3d;

public record AeroWindSample(
    float velocityX,
    float velocityY,
    float velocityZ,
    float pressure,
    Level level,
    Authority authority,
    long l1Epoch,
    long worldDeltaEpoch,
    long l2Epoch,
    float confidence
) {
    public static final long UNKNOWN_EPOCH = -1L;
    public static final AeroWindSample ZERO = new AeroWindSample(
        0.0f,
        0.0f,
        0.0f,
        0.0f,
        Level.NONE,
        Authority.NONE,
        UNKNOWN_EPOCH,
        UNKNOWN_EPOCH,
        UNKNOWN_EPOCH,
        0.0f
    );

    public static AeroWindSample serverAuthoritative(
        float velocityX,
        float velocityY,
        float velocityZ,
        float pressure,
        Level level,
        long l1Epoch,
        long worldDeltaEpoch,
        long l2Epoch
    ) {
        return new AeroWindSample(
            velocityX,
            velocityY,
            velocityZ,
            pressure,
            level,
            Authority.SERVER_AUTHORITATIVE,
            l1Epoch,
            worldDeltaEpoch,
            l2Epoch,
            1.0f
        );
    }

    public static AeroWindSample serverAggregatedL2(
        float velocityX,
        float velocityY,
        float velocityZ,
        float pressure,
        long l1Epoch,
        long worldDeltaEpoch,
        long l2Epoch,
        float confidence
    ) {
        return new AeroWindSample(
            velocityX,
            velocityY,
            velocityZ,
            pressure,
            Level.L2,
            Authority.SERVER_AGGREGATED,
            l1Epoch,
            worldDeltaEpoch,
            l2Epoch,
            confidence
        );
    }

    public static AeroWindSample clientLocalL2(
        float velocityX,
        float velocityY,
        float velocityZ,
        float pressure,
        long l1Epoch,
        long worldDeltaEpoch,
        long l2Epoch
    ) {
        return new AeroWindSample(
            velocityX,
            velocityY,
            velocityZ,
            pressure,
            Level.L2,
            Authority.CLIENT_LOCAL,
            l1Epoch,
            worldDeltaEpoch,
            l2Epoch,
            1.0f
        );
    }

    public Vec3d velocity() {
        return new Vec3d(velocityX, velocityY, velocityZ);
    }

    public boolean hasFlow() {
        return level != Level.NONE && authority != Authority.NONE;
    }

    public boolean isServerTrusted() {
        return authority == Authority.SERVER_AUTHORITATIVE || authority == Authority.SERVER_AGGREGATED;
    }

    public Source source() {
        if (level == Level.L0) {
            return Source.L0_BACKGROUND;
        }
        if (level == Level.L1) {
            return Source.L1_COARSE;
        }
        if (level == Level.L2) {
            return Source.L2_BRICK;
        }
        return Source.NONE;
    }

    public enum Level {
        NONE,
        L0,
        L1,
        L2
    }

    public enum Authority {
        NONE,
        SERVER_AUTHORITATIVE,
        SERVER_AGGREGATED,
        CLIENT_LOCAL,
        CLIENT_REMOTE,
        UNTRUSTED
    }

    @Deprecated
    public enum Source {
        NONE,
        L0_BACKGROUND,
        L1_COARSE,
        L2_BRICK
    }
}
