package com.aerodynamics4mc.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aerodynamics4mc.ModBlocks;
import com.aerodynamics4mc.api.AeroWindSample;
import com.aerodynamics4mc.net.AeroCoarseWindPayload;
import com.aerodynamics4mc.runtime.NativeSimulationBridge;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

final class ClientL2Solver {
    private static final Logger LOGGER = LoggerFactory.getLogger("aerodynamics4mc/ClientL2Solver");

    private static final int BRICK_SIZE = 32;
    private static final int CELL_COUNT = BRICK_SIZE * BRICK_SIZE * BRICK_SIZE;
    private static final int FLOW_CHANNELS = NativeSimulationBridge.FLOW_STATE_CHANNELS;
    private static final int PACKED_CHANNELS = NativeSimulationBridge.PACKED_ATLAS_CHANNELS;
    private static final int FACE_COUNT = Direction.values().length;
    private static final int STATIC_REFRESH_TICKS = 20;
    private static final int MAX_PENDING_SERVER_STEPS = 8;
    private static final int MAX_STEPS_PER_CLIENT_TICK = 1;
    private static final float DT_SECONDS = 0.05f;
    private static final float DX_METERS = 1.0f;
    private static final float NATIVE_VELOCITY_SCALE = DX_METERS / DT_SECONDS;
    private static final float ATLAS_VELOCITY_RANGE = 5.6f;
    private static final float ATLAS_PRESSURE_RANGE = 0.03f;

    private final AeroVisualizer visualizer;
    private final NativeSimulationBridge bridge = new NativeSimulationBridge();
    private final byte[] obstacle = new byte[CELL_COUNT];
    private final byte[] surfaceKind = new byte[CELL_COUNT];
    private final short[] openFaceMask = new short[CELL_COUNT];
    private final float[] emitterPower = new float[CELL_COUNT];
    private final byte[] faceSkyExposure = new byte[CELL_COUNT * FACE_COUNT];
    private final byte[] faceDirectExposure = new byte[CELL_COUNT * FACE_COUNT];
    private final float[] flowState = new float[CELL_COUNT * FLOW_CHANNELS];
    private final float[] airTemperature = new float[CELL_COUNT];
    private final float[] surfaceTemperature = new float[CELL_COUNT];
    private final short[] packedFlow = new short[CELL_COUNT * PACKED_CHANNELS];

    private long serviceKey;
    private long worldKey;
    private BlockPos activeOrigin;
    private Identifier activeDimension;
    private boolean streamingEnabled;
    private boolean brickSeeded;
    private boolean activeHintUploaded;
    private int ticksSinceStaticRefresh = STATIC_REFRESH_TICKS;
    private int pendingServerSteps;
    private long lastServerTick = Long.MIN_VALUE;
    private long lastProcessedClientGameTime = Long.MIN_VALUE;
    private long lastDiagnosticGameTime = Long.MIN_VALUE;

    ClientL2Solver(AeroVisualizer visualizer) {
        this.visualizer = visualizer;
    }

    void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> close());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> close());
    }

    void onRuntimeState(boolean streamingEnabled) {
        this.streamingEnabled = streamingEnabled;
        if (!streamingEnabled) {
            resetActiveBrick();
        }
    }

    void onCoarseWindField(AeroCoarseWindPayload payload) {
        if (!streamingEnabled || payload == null) {
            return;
        }
        long serverTick = payload.serverTick();
        if (serverTick < 0L) {
            return;
        }
        if (lastServerTick == Long.MIN_VALUE || serverTick < lastServerTick) {
            pendingServerSteps = Math.max(pendingServerSteps, 1);
        } else {
            long delta = serverTick - lastServerTick;
            if (delta > 0L) {
                int granted = (int) Math.min(delta, MAX_PENDING_SERVER_STEPS);
                pendingServerSteps = Math.min(MAX_PENDING_SERVER_STEPS, pendingServerSteps + granted);
            }
        }
        lastServerTick = serverTick;
    }

    private void onClientTick(MinecraftClient client) {
        if (!streamingEnabled || client.world == null || client.player == null) {
            return;
        }
        if (!bridge.isLoaded()) {
            maybeLog(client, "native library not loaded: " + bridge.getLoadError());
            return;
        }
        ensureService();
        if (serviceKey == 0L) {
            maybeLog(client, "failed to create native service");
            return;
        }

        ClientWorld world = client.world;
        long clientGameTime = world.getTime();
        if (lastProcessedClientGameTime == clientGameTime) {
            return;
        }
        lastProcessedClientGameTime = clientGameTime;

        Identifier dimensionId = world.getRegistryKey().getValue();
        BlockPos origin = brickOrigin(client.player.getBlockPos());
        boolean originChanged = activeOrigin == null
            || !activeOrigin.equals(origin)
            || activeDimension == null
            || !activeDimension.equals(dimensionId);
        if (originChanged) {
            activeOrigin = origin;
            activeDimension = dimensionId;
            worldKey = worldKey(dimensionId);
            brickSeeded = false;
            activeHintUploaded = false;
            visualizer.clearLocalFlowFields();
            ticksSinceStaticRefresh = STATIC_REFRESH_TICKS;
        }

        int brickX = Math.floorDiv(origin.getX(), BRICK_SIZE);
        int brickY = Math.floorDiv(origin.getY(), BRICK_SIZE);
        int brickZ = Math.floorDiv(origin.getZ(), BRICK_SIZE);
        if (!bridge.ensureBrickWorldRuntime(serviceKey, worldKey, BRICK_SIZE, DX_METERS, DT_SECONDS)) {
            maybeLog(client, "ensureBrickWorldRuntime failed: " + bridge.lastError());
            return;
        }
        if (!activeHintUploaded) {
            if (!bridge.setBrickWorldExactActiveHints(serviceKey, worldKey, BRICK_SIZE, new int[] {brickX, brickY, brickZ})) {
                maybeLog(client, "setBrickWorldExactActiveHints failed: " + bridge.lastError());
                return;
            }
            activeHintUploaded = true;
        }

        if (ticksSinceStaticRefresh++ >= STATIC_REFRESH_TICKS) {
            uploadStaticBrick(world, origin, brickX, brickY, brickZ);
            ticksSinceStaticRefresh = 0;
        }
        if (!brickSeeded) {
            seedDynamicBrick(world, dimensionId, origin, brickX, brickY, brickZ);
            brickSeeded = true;
        }
        if (pendingServerSteps <= 0) {
            return;
        }
        int stepsToRun = Math.min(pendingServerSteps, MAX_STEPS_PER_CLIENT_TICK);
        for (int i = 0; i < stepsToRun; i++) {
            if (!bridge.stepBrickWorldRuntime(serviceKey, worldKey, 1)) {
                maybeLog(client, "stepBrickWorldRuntime failed: " + bridge.lastError());
                return;
            }
            pendingServerSteps--;
        }
        publishLocalAtlas(dimensionId, origin, brickX, brickY, brickZ);
    }

    private void ensureService() {
        if (serviceKey == 0L) {
            serviceKey = bridge.createService();
        }
    }

    private void uploadStaticBrick(ClientWorld world, BlockPos origin, int brickX, int brickY, int brickZ) {
        java.util.Arrays.fill(obstacle, (byte) 0);
        java.util.Arrays.fill(surfaceKind, (byte) 0);
        java.util.Arrays.fill(openFaceMask, (short) 0);
        java.util.Arrays.fill(emitterPower, 0.0f);
        java.util.Arrays.fill(faceSkyExposure, (byte) 0);
        java.util.Arrays.fill(faceDirectExposure, (byte) 0);

        BlockPos.Mutable cursor = new BlockPos.Mutable();
        BlockPos.Mutable neighbor = new BlockPos.Mutable();
        for (int x = 0; x < BRICK_SIZE; x++) {
            for (int y = 0; y < BRICK_SIZE; y++) {
                for (int z = 0; z < BRICK_SIZE; z++) {
                    cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    int cell = cellIndex(x, y, z);
                    BlockState state = world.getBlockState(cursor);
                    boolean solid = isSolidObstacle(world, cursor, state);
                    obstacle[cell] = solid ? (byte) 1 : (byte) 0;
                    if (solid) {
                        continue;
                    }
                    short mask = 0;
                    for (Direction direction : Direction.values()) {
                        neighbor.set(
                            cursor.getX() + direction.getOffsetX(),
                            cursor.getY() + direction.getOffsetY(),
                            cursor.getZ() + direction.getOffsetZ()
                        );
                        if (!isSolidObstacle(world, neighbor, world.getBlockState(neighbor))) {
                            mask = (short) (mask | (1 << direction.ordinal()));
                        }
                    }
                    openFaceMask[cell] = mask;
                }
            }
        }
        if (!bridge.uploadBrickWorldStaticBrick(
            serviceKey,
            worldKey,
            BRICK_SIZE,
            brickX,
            brickY,
            brickZ,
            obstacle,
            surfaceKind,
            openFaceMask,
            emitterPower,
            faceSkyExposure,
            faceDirectExposure
        )) {
            LOGGER.debug("client L2 static upload failed: {}", bridge.lastError());
        }
    }

    private void seedDynamicBrick(ClientWorld world, Identifier dimensionId, BlockPos origin, int brickX, int brickY, int brickZ) {
        java.util.Arrays.fill(flowState, 0.0f);
        java.util.Arrays.fill(airTemperature, 0.0f);
        java.util.Arrays.fill(surfaceTemperature, 0.0f);
        for (int x = 0; x < BRICK_SIZE; x++) {
            for (int y = 0; y < BRICK_SIZE; y++) {
                for (int z = 0; z < BRICK_SIZE; z++) {
                    int cell = cellIndex(x, y, z);
                    if (obstacle[cell] != 0) {
                        continue;
                    }
                    Vec3d pos = new Vec3d(origin.getX() + x + 0.5, origin.getY() + y + 0.5, origin.getZ() + z + 0.5);
                    AeroWindSample coarse = visualizer.sampleServerCoarseFlow(dimensionId, pos);
                    int base = cell * FLOW_CHANNELS;
                    flowState[base] = coarse.velocityX() / NATIVE_VELOCITY_SCALE;
                    flowState[base + 1] = coarse.velocityY() / NATIVE_VELOCITY_SCALE;
                    flowState[base + 2] = coarse.velocityZ() / NATIVE_VELOCITY_SCALE;
                    flowState[base + 3] = coarse.pressure();
                }
            }
        }
        bridge.uploadBrickWorldDynamicBrick(
            serviceKey,
            worldKey,
            BRICK_SIZE,
            brickX,
            brickY,
            brickZ,
            flowState,
            airTemperature,
            surfaceTemperature
        );
        bridge.uploadBrickWorldBoundaryReferenceBrick(
            serviceKey,
            worldKey,
            BRICK_SIZE,
            brickX,
            brickY,
            brickZ,
            flowState,
            airTemperature,
            surfaceTemperature
        );
    }

    private void publishLocalAtlas(Identifier dimensionId, BlockPos origin, int brickX, int brickY, int brickZ) {
        if (!bridge.copyBrickWorldDynamicBrick(
            serviceKey,
            worldKey,
            BRICK_SIZE,
            brickX,
            brickY,
            brickZ,
            flowState,
            airTemperature,
            surfaceTemperature
        )) {
            return;
        }
        for (int cell = 0; cell < CELL_COUNT; cell++) {
            int srcBase = cell * FLOW_CHANNELS;
            int dstBase = cell * PACKED_CHANNELS;
            packedFlow[dstBase] = quantizeSignedToShort(flowState[srcBase] * NATIVE_VELOCITY_SCALE, ATLAS_VELOCITY_RANGE);
            packedFlow[dstBase + 1] = quantizeSignedToShort(flowState[srcBase + 1] * NATIVE_VELOCITY_SCALE, ATLAS_VELOCITY_RANGE);
            packedFlow[dstBase + 2] = quantizeSignedToShort(flowState[srcBase + 2] * NATIVE_VELOCITY_SCALE, ATLAS_VELOCITY_RANGE);
            packedFlow[dstBase + 3] = quantizeSignedToShort(flowState[srcBase + 3], ATLAS_PRESSURE_RANGE);
        }
        visualizer.onLocalFlowField(dimensionId, origin, packedFlow);
    }

    private boolean isSolidObstacle(ClientWorld world, BlockPos pos, BlockState state) {
        if (state.isAir() || state.isOf(ModBlocks.DUCT_BLOCK)) {
            return false;
        }
        return !state.getCollisionShape(world, pos).isEmpty();
    }

    private short quantizeSignedToShort(float value, float range) {
        if (!(range > 0.0f) || !Float.isFinite(value)) {
            return 0;
        }
        float normalized = MathHelper.clamp(value / range, -1.0f, 1.0f);
        return (short) Math.round(normalized * 32767.0f);
    }

    private BlockPos brickOrigin(BlockPos pos) {
        return new BlockPos(
            Math.floorDiv(pos.getX(), BRICK_SIZE) * BRICK_SIZE,
            Math.floorDiv(pos.getY(), BRICK_SIZE) * BRICK_SIZE,
            Math.floorDiv(pos.getZ(), BRICK_SIZE) * BRICK_SIZE
        );
    }

    private int cellIndex(int x, int y, int z) {
        return (x * BRICK_SIZE + y) * BRICK_SIZE + z;
    }

    private long worldKey(Identifier dimensionId) {
        long value = 1469598103934665603L;
        String text = dimensionId.toString();
        for (int i = 0; i < text.length(); i++) {
            value = (value ^ text.charAt(i)) * 1099511628211L;
        }
        return value == 0L ? 1L : value;
    }

    private void resetActiveBrick() {
        activeOrigin = null;
        activeDimension = null;
        brickSeeded = false;
        activeHintUploaded = false;
        pendingServerSteps = 0;
        lastServerTick = Long.MIN_VALUE;
        lastProcessedClientGameTime = Long.MIN_VALUE;
        visualizer.clearLocalFlowFields();
    }

    private void close() {
        resetActiveBrick();
        if (serviceKey != 0L) {
            bridge.releaseService(serviceKey);
            serviceKey = 0L;
        }
    }

    private void maybeLog(MinecraftClient client, String message) {
        if (client.world == null) {
            return;
        }
        long now = client.world.getTime();
        if (lastDiagnosticGameTime == Long.MIN_VALUE || now - lastDiagnosticGameTime >= 100) {
            LOGGER.info("Client L2 idle: {}", message);
            lastDiagnosticGameTime = now;
        }
    }
}
