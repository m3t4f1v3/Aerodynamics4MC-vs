package com.aerodynamics4mc.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aerodynamics4mc.FanBlock;
import com.aerodynamics4mc.ModBlocks;
import com.aerodynamics4mc.api.AeroWindSamplingRules;
import com.aerodynamics4mc.api.AeroWindSample;
import com.aerodynamics4mc.net.AeroCoarseWindPayload;
import com.aerodynamics4mc.runtime.NativeSimulationBridge;

import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class ClientL2Solver {
    private static final Logger LOGGER = LoggerFactory.getLogger("aerodynamics4mc/ClientL2Solver");

    private static final int BRICK_SIZE = configuredBrickSize();
    private static final int CELL_COUNT = BRICK_SIZE * BRICK_SIZE * BRICK_SIZE;
    private static final int FLOW_CHANNELS = NativeSimulationBridge.FLOW_STATE_CHANNELS;
    private static final int PACKED_CHANNELS = NativeSimulationBridge.PACKED_ATLAS_CHANNELS;
    private static final int FACE_COUNT = Direction.values().length;
    private static final int STATIC_REFRESH_TICKS = -1;
    private static final int LOCAL_PUBLISH_INTERVAL_TICKS = configuredInt(
        "a4mc.clientL2.publishIntervalTicks",
        "AERO_LBM_CLIENT_L2_PUBLISH_INTERVAL_TICKS",
        1,
        1,
        200
    );
    private static final int LOCAL_PUBLISH_SAMPLE_STRIDE = configuredInt(
        "a4mc.clientL2.publishSampleStride",
        "AERO_LBM_CLIENT_L2_PUBLISH_SAMPLE_STRIDE",
        BRICK_SIZE >= 128 ? 4 : 1,
        1,
        16
    );
    private static final int SOLVE_INTERVAL_TICKS = configuredInt(
        "a4mc.clientL2.solveIntervalTicks",
        "AERO_LBM_CLIENT_L2_SOLVE_INTERVAL_TICKS",
        1,
        1,
        200
    );
    private static final int BOUNDARY_REFERENCE_REFRESH_MIN_TICKS = 40;
    private static final int FAST_SUSPEND_COOLDOWN_TICKS = 10;
    private static final int STATIC_BUILD_CELLS_PER_TICK = configuredInt(
        "a4mc.clientL2.staticBuildCellsPerTick",
        "AERO_LBM_CLIENT_L2_STATIC_BUILD_CELLS_PER_TICK",
        BRICK_SIZE >= 128 ? 65536 : 1024,
        1,
        CELL_COUNT
    );
    private static final int COARSE_SEED_CELLS_PER_TICK = configuredInt(
        "a4mc.clientL2.coarseSeedCellsPerTick",
        "AERO_LBM_CLIENT_L2_COARSE_SEED_CELLS_PER_TICK",
        BRICK_SIZE >= 128 ? 131072 : 4096,
        1,
        CELL_COUNT
    );
    private static final int BOUNDARY_REFERENCE_CELLS_PER_TICK = configuredInt(
        "a4mc.clientL2.boundaryReferenceCellsPerTick",
        "AERO_LBM_CLIENT_L2_BOUNDARY_REFERENCE_CELLS_PER_TICK",
        BRICK_SIZE >= 128 ? 16384 : 4096,
        1,
        CELL_COUNT
    );
    private static final int STATIC_CACHE_MAX_BRICKS = configuredInt(
        "a4mc.clientL2.staticCacheMaxBricks",
        "AERO_LBM_CLIENT_L2_STATIC_CACHE_MAX_BRICKS",
        BRICK_SIZE >= 128 ? 2 : 32,
        0,
        64
    );
    private static final int COUPLING_BAND_CELLS = 8;
    private static final int MAX_CLIENT_ACTIVE_BRICKS = configuredInt(
        "a4mc.clientL2.maxActiveBricks",
        "AERO_LBM_CLIENT_L2_MAX_ACTIVE_BRICKS",
        BRICK_SIZE >= 128 ? 1 : 2,
        1,
        8
    );
    private static final int MAX_STEPS_PER_CLIENT_TICK = configuredInt(
        "a4mc.clientL2.maxStepsPerClientTick",
        "AERO_LBM_CLIENT_L2_MAX_STEPS_PER_CLIENT_TICK",
        1,
        1,
        16
    );
    private static final float DT_SECONDS = 0.05f;
    private static final float DX_METERS = 1.0f;
    private static final float NATIVE_VELOCITY_SCALE = DX_METERS / DT_SECONDS;
    private static final float ATLAS_VELOCITY_RANGE = 5.6f;
    private static final float ATLAS_PRESSURE_RANGE = 0.03f;
    private static final float ZERO_DYNAMIC_MAX_SPEED_EPS_MPS = 0.02f;
    private static final float COARSE_RESEED_MIN_SPEED_MPS = 0.05f;
    private static final float FAST_RESUME_HORIZONTAL_SPEED_MPS = 6.0f;
    private static final float HEAT_COUPLING_TO_ADJACENT_AIR = 0.85f;
    private static final float THERMAL_EMITTER_POWER_LAVA_W = 3200.0f;
    private static final float THERMAL_EMITTER_POWER_MAGMA_W = 1200.0f;
    private static final float THERMAL_EMITTER_POWER_CAMPFIRE_W = 1800.0f;
    private static final float THERMAL_EMITTER_POWER_SOUL_CAMPFIRE_W = 1200.0f;
    private static final float THERMAL_EMITTER_POWER_FIRE_W = 2200.0f;
    private static final float THERMAL_EMITTER_POWER_SOUL_FIRE_W = 1500.0f;
    private static final float THERMAL_EMITTER_POWER_TORCH_W = 80.0f;
    private static final float THERMAL_EMITTER_POWER_SOUL_TORCH_W = 50.0f;
    private static final float THERMAL_EMITTER_POWER_LANTERN_W = 60.0f;
    private static final float THERMAL_EMITTER_POWER_SOUL_LANTERN_W = 40.0f;
    private static final int FAN_FORCE_LENGTH_CELLS = 5;
    private static final int FAN_FORCE_RADIUS_CELLS = 1;
    private static final int HEAT_PLUME_HEIGHT_CELLS = 4;
    private static final byte SURFACE_KIND_FAN_X_NEG = 32;
    private static final byte SURFACE_KIND_FAN_X_POS = 33;
    private static final byte SURFACE_KIND_FAN_Y_NEG = 34;
    private static final byte SURFACE_KIND_FAN_Y_POS = 35;
    private static final byte SURFACE_KIND_FAN_Z_NEG = 36;
    private static final byte SURFACE_KIND_FAN_Z_POS = 37;
    private static final int WORKER_QUEUE_CAPACITY = 128;
    private static final boolean CLIENT_L2_DEFAULT_ENABLED = true;

    private final AeroVisualizer visualizer;
    private final ClientL2Worker worker = new ClientL2Worker();
    private final byte[] obstacle = new byte[CELL_COUNT];
    private final byte[] surfaceKind = new byte[CELL_COUNT];
    private final short[] openFaceMask = new short[CELL_COUNT];
    private final float[] emitterPower = new float[CELL_COUNT];
    private final byte[] faceSkyExposure = new byte[CELL_COUNT * FACE_COUNT];
    private final byte[] faceDirectExposure = new byte[CELL_COUNT * FACE_COUNT];
    private final float[] flowState = new float[CELL_COUNT * FLOW_CHANNELS];
    private final float[] airTemperature = new float[CELL_COUNT];
    private final float[] surfaceTemperature = new float[CELL_COUNT];
    private final BlockPos.MutableBlockPos staticCursor = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos staticNeighbor = new BlockPos.MutableBlockPos();
    private final LinkedHashMap<StaticBrickCacheKey, StaticBrickSnapshot> staticBrickCache =
        new LinkedHashMap<>(STATIC_CACHE_MAX_BRICKS, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<StaticBrickCacheKey, StaticBrickSnapshot> eldest) {
                return size() > STATIC_CACHE_MAX_BRICKS;
            }
        };
    private final int[] activeBrickX = new int[MAX_CLIENT_ACTIVE_BRICKS];
    private final int[] activeBrickY = new int[MAX_CLIENT_ACTIVE_BRICKS];
    private final int[] activeBrickZ = new int[MAX_CLIENT_ACTIVE_BRICKS];
    private final boolean[] activeBrickReady = new boolean[MAX_CLIENT_ACTIVE_BRICKS];
    private final boolean[] activeBrickRefreshPending = new boolean[MAX_CLIENT_ACTIVE_BRICKS];
    private final boolean[] activeBrickBoundaryRefreshPending = new boolean[MAX_CLIENT_ACTIVE_BRICKS];
    private int[] activeHintCoords = new int[NativeSimulationBridge.BRICK_HINT_COORDS_PER_BRICK];

    private long levelKey;
    private BlockPos activeOrigin;
    private ResourceLocation activeDimension;
    private boolean streamingEnabled;
    private boolean experimentalEnabled = CLIENT_L2_DEFAULT_ENABLED;
    private boolean activeHintUploaded;
    private boolean clientSolveDisabled;
    private int activeBrickCount;
    private int prepareCursor;
    private int refreshCursor;
    private int publishCursor;
    private int stagedActiveIndex = -1;
    private int stagedBrickX;
    private int stagedBrickY;
    private int stagedBrickZ;
    private int stagedStaticCursor;
    private int stagedSeedCursor;
    private boolean stagedStaticUploaded;
    private boolean stagedDynamicUploaded;
    private boolean stagedStaticFromCache;
    private BlockPos stagedOrigin;
    private ResourceLocation stagedDimension;
    private int boundaryRefreshActiveIndex = -1;
    private int boundaryRefreshBrickX;
    private int boundaryRefreshBrickY;
    private int boundaryRefreshBrickZ;
    private int boundaryRefreshCursor;
    private float boundaryRefreshMaxCoarseSpeed;
    private BlockPos boundaryRefreshOrigin;
    private ResourceLocation boundaryRefreshDimension;
    private int ticksSinceStaticRefresh = STATIC_REFRESH_TICKS;
    private long lastServerTick = Long.MIN_VALUE;
    private long lastProcessedClientGameTime = Long.MIN_VALUE;
    private long lastSolveClientGameTime = Long.MIN_VALUE;
    private long lastPublishedClientGameTime = Long.MIN_VALUE;
    private long lastBoundaryRefreshClientGameTime = Long.MIN_VALUE;
    private long fastSuspendUntilGameTime = Long.MIN_VALUE;
    private long lastDiagnosticGameTime = Long.MIN_VALUE;
    private int lastStaticPatchCount;
    private int lastFanPatchCellCount;
    private int lastHeatPatchCellCount;

    ClientL2Solver(AeroVisualizer visualizer) {
        this.visualizer = visualizer;
    }

    private static int configuredBrickSize() {
        int requested = configuredInt(
            "a4mc.clientL2.brickSize",
            "AERO_LBM_CLIENT_L2_BRICK_SIZE",
            32,
            16,
            128
        );
        int aligned = Math.max(16, Math.min(128, (requested / 16) * 16));
        if (aligned != requested) {
            LOGGER.warn("Client L2 brick size {} is not chunk-aligned; using {}", requested, aligned);
        }
        return aligned;
    }

    private static int configuredInt(String propertyName, String envName, int defaultValue, int min, int max) {
        String value = System.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            value = System.getenv(envName);
        }
        if (value == null || value.isBlank()) {
            return Mth.clamp(defaultValue, min, max);
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            int clamped = Mth.clamp(parsed, min, max);
            if (clamped != parsed) {
                LOGGER.warn(
                    "Client L2 config {}={} outside [{}, {}]; using {}",
                    propertyName,
                    parsed,
                    min,
                    max,
                    clamped
                );
            }
            return clamped;
        } catch (NumberFormatException ignored) {
            LOGGER.warn("Client L2 config {}={} is not an integer; using {}", propertyName, value, defaultValue);
            return Mth.clamp(defaultValue, min, max);
        }
    }

    void initialize() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onClientTickEvent(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        onClientTick(Minecraft.getInstance());
    }

    @SubscribeEvent
    public void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        close();
    }

    void onRuntimeState(boolean streamingEnabled) {
        this.streamingEnabled = streamingEnabled;
        if (!streamingEnabled || !experimentalEnabled) {
            resetActiveBrick();
            worker.reset();
        }
    }

    void onCoarseWindField(AeroCoarseWindPayload payload) {
        if (!streamingEnabled || !experimentalEnabled || payload == null) {
            return;
        }
        long serverTick = payload.serverTick;
        if (serverTick < 0L) {
            return;
        }
        if (lastServerTick == Long.MIN_VALUE || serverTick != lastServerTick) {
            markBoundaryRefreshPending();
        }
        lastServerTick = serverTick;
    }

    void onBlockStateChanged(ClientLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
        if (level == null || pos == null || oldState == newState || (oldState != null && oldState.equals(newState))) {
            return;
        }
        ResourceLocation dimensionId = level.dimension().location();
        invalidateStaticCacheForPatchFootprint(dimensionId, pos, oldState, newState);
        if (!experimentalEnabled || !streamingEnabled || clientSolveDisabled || levelKey == 0L) {
            return;
        }
        if (activeDimension == null || !activeDimension.equals(dimensionId) || activeBrickCount <= 0) {
            return;
        }
        if (!blockPatchTouchesActiveBrick(pos)) {
            return;
        }
        NativeSimulationBridge.LevelDelta[] deltas = buildStaticPatchDeltas(level, pos, oldState, newState);
        if (deltas.length == 0) {
            return;
        }
        int fanPatchCells = 0;
        int heatPatchCells = 0;
        for (NativeSimulationBridge.LevelDelta delta : deltas) {
            int surfaceKind = (delta.data1() >> 8) & 0xFF;
            if (isFanSurfaceKind(surfaceKind)) {
                fanPatchCells++;
            }
            if (delta.value0() > 0.0f) {
                heatPatchCells++;
            }
            refreshLocalStaticCellIfActive(level, new BlockPos(delta.x(), delta.y(), delta.z()));
        }
        lastStaticPatchCount = deltas.length;
        lastFanPatchCellCount = fanPatchCells;
        lastHeatPatchCellCount = heatPatchCells;
        worker.submitLevelDeltas(levelKey, deltas);
    }

    private void onClientTick(Minecraft client) {
        drainWorkerAtlases();
        if (!experimentalEnabled || !streamingEnabled || client.level == null || client.player == null) {
            return;
        }
        if (clientSolveDisabled) {
            return;
        }
        ClientLevel level = client.level;
        long clientGameTime = level.getGameTime();
        if (lastProcessedClientGameTime == clientGameTime) {
            return;
        }
        lastProcessedClientGameTime = clientGameTime;

        float horizontalSpeed = AeroWindSamplingRules.horizontalSpeedMetersPerSecond(client.player.getDeltaMovement());
        if (shouldSuspendForFastMovement(horizontalSpeed, clientGameTime)) {
            suspendForFastMovement(clientGameTime);
            return;
        }
        if (!worker.isNativeLoaded()) {
            maybeLog(client, "native library not loaded: " + worker.loadError());
            return;
        }

        ResourceLocation dimensionId = level.dimension().location();
        BlockPos playerBlockPos = client.player.blockPosition();
        BlockPos origin = brickOrigin(playerBlockPos);
        int brickX = Math.floorDiv(origin.getX(), BRICK_SIZE);
        int brickY = Math.floorDiv(origin.getY(), BRICK_SIZE);
        int brickZ = Math.floorDiv(origin.getZ(), BRICK_SIZE);
        int localX = playerBlockPos.getX() - origin.getX();
        int localY = playerBlockPos.getY() - origin.getY();
        int localZ = playerBlockPos.getZ() - origin.getZ();
        boolean originChanged = activeOrigin == null
            || !activeOrigin.equals(origin)
            || activeDimension == null
            || !activeDimension.equals(dimensionId);
        boolean activeSetChanged = originChanged || !activeSetMatches(brickX, brickY, brickZ, localX, localY, localZ);
        if (activeSetChanged) {
            activeOrigin = origin;
            activeDimension = dimensionId;
            levelKey = levelKey(dimensionId);
            activeHintUploaded = false;
            buildActiveBrickSet(brickX, brickY, brickZ, localX, localY, localZ);
            cancelStagedPreparation();
            cancelBoundaryReferenceRefresh();
            lastPublishedClientGameTime = Long.MIN_VALUE;
            lastSolveClientGameTime = Long.MIN_VALUE;
            lastBoundaryRefreshClientGameTime = Long.MIN_VALUE;
            visualizer.clearLocalFlowFields();
            ticksSinceStaticRefresh = 0;
        }

        if (!activeHintUploaded) {
            worker.submitActiveHints(levelKey, activeHintCoords);
            activeHintUploaded = true;
        }

        prepareActiveBricks(client, level, dimensionId);
        if (!hasReadyActiveBrick()) {
            return;
        }
        if (refreshActiveBrickStatic(client, level)) {
            return;
        }
        if (refreshActiveBrickBoundaryReference(client, dimensionId, clientGameTime)) {
            return;
        }
        if (lastSolveClientGameTime != Long.MIN_VALUE
            && clientGameTime - lastSolveClientGameTime < SOLVE_INTERVAL_TICKS) {
            return;
        }
        boolean publish = lastPublishedClientGameTime == Long.MIN_VALUE
            || clientGameTime - lastPublishedClientGameTime >= LOCAL_PUBLISH_INTERVAL_TICKS;
        worker.requestStep(levelKey, publishTargets(dimensionId, publish), MAX_STEPS_PER_CLIENT_TICK);
        lastSolveClientGameTime = clientGameTime;
        if (publish) {
            lastPublishedClientGameTime = clientGameTime;
        }
    }

    private void drainWorkerAtlases() {
        LocalAtlasSnapshot snapshot;
        while ((snapshot = worker.pollAtlas()) != null) {
            visualizer.onLocalFlowField(
                snapshot.dimensionId(),
                snapshot.origin(),
                snapshot.sampleStride(),
                snapshot.packedFlow()
            );
        }
    }

    private PublishTarget[] publishTargets(ResourceLocation dimensionId, boolean publish) {
        if (!publish || activeBrickCount <= 0) {
            return new PublishTarget[0];
        }
        PublishTarget[] targets = new PublishTarget[activeBrickCount];
        int count = 0;
        for (int attempts = 0; attempts < activeBrickCount; attempts++) {
            int index = (publishCursor + attempts) % activeBrickCount;
            if (!activeBrickReady[index]) {
                continue;
            }
            int brickX = activeBrickX[index];
            int brickY = activeBrickY[index];
            int brickZ = activeBrickZ[index];
            targets[count++] = new PublishTarget(dimensionId, brickOrigin(brickX, brickY, brickZ), brickX, brickY, brickZ);
        }
        publishCursor = activeBrickCount <= 0 ? 0 : (publishCursor + 1) % activeBrickCount;
        return java.util.Arrays.copyOf(targets, count);
    }

    private boolean shouldSuspendForFastMovement(float horizontalSpeedMetersPerSecond, long clientGameTime) {
        if (horizontalSpeedMetersPerSecond > AeroWindSamplingRules.FAST_PLAYER_HORIZONTAL_SPEED_THRESHOLD_MPS) {
            return true;
        }
        if (fastSuspendUntilGameTime != Long.MIN_VALUE && clientGameTime < fastSuspendUntilGameTime) {
            return horizontalSpeedMetersPerSecond > FAST_RESUME_HORIZONTAL_SPEED_MPS;
        }
        fastSuspendUntilGameTime = Long.MIN_VALUE;
        return false;
    }

    private void suspendForFastMovement(long clientGameTime) {
        fastSuspendUntilGameTime = clientGameTime + FAST_SUSPEND_COOLDOWN_TICKS;
        if (activeBrickCount > 0 || activeOrigin != null) {
            resetActiveBrick();
        } else {
            visualizer.clearLocalFlowFields();
        }
    }

    private boolean activeSetMatches(
        int coreBrickX,
        int coreBrickY,
        int coreBrickZ,
        int localX,
        int localY,
        int localZ
    ) {
        int expectedIndex = 0;
        if (!activeBrickMatches(expectedIndex++, coreBrickX, coreBrickY, coreBrickZ)) {
            return false;
        }
        int[] neighborOffset = MAX_CLIENT_ACTIVE_BRICKS > 1
            ? nearestBoundaryNeighborOffset(localX, localY, localZ)
            : null;
        if (neighborOffset != null
            && !activeBrickMatches(
                expectedIndex++,
                coreBrickX + neighborOffset[0],
                coreBrickY + neighborOffset[1],
                coreBrickZ + neighborOffset[2]
            )) {
            return false;
        }
        return activeBrickCount == expectedIndex;
    }

    private int[] nearestBoundaryNeighborOffset(int localX, int localY, int localZ) {
        int bestDistance = COUPLING_BAND_CELLS;
        int bestX = 0;
        int bestY = 0;
        int bestZ = 0;
        if (localX < bestDistance) {
            bestDistance = localX;
            bestX = -1;
            bestY = 0;
            bestZ = 0;
        }
        int distance = BRICK_SIZE - 1 - localX;
        if (distance < bestDistance) {
            bestDistance = distance;
            bestX = 1;
            bestY = 0;
            bestZ = 0;
        }
        if (localY < bestDistance) {
            bestDistance = localY;
            bestX = 0;
            bestY = -1;
            bestZ = 0;
        }
        distance = BRICK_SIZE - 1 - localY;
        if (distance < bestDistance) {
            bestDistance = distance;
            bestX = 0;
            bestY = 1;
            bestZ = 0;
        }
        if (localZ < bestDistance) {
            bestDistance = localZ;
            bestX = 0;
            bestY = 0;
            bestZ = -1;
        }
        distance = BRICK_SIZE - 1 - localZ;
        if (distance < bestDistance) {
            bestDistance = distance;
            bestX = 0;
            bestY = 0;
            bestZ = 1;
        }
        return bestDistance < COUPLING_BAND_CELLS ? new int[] {bestX, bestY, bestZ} : null;
    }

    private boolean activeBrickMatches(int index, int brickX, int brickY, int brickZ) {
        return index < activeBrickCount
            && activeBrickX[index] == brickX
            && activeBrickY[index] == brickY
            && activeBrickZ[index] == brickZ;
    }

    private void buildActiveBrickSet(
        int coreBrickX,
        int coreBrickY,
        int coreBrickZ,
        int localX,
        int localY,
        int localZ
    ) {
        int oldActiveBrickCount = activeBrickCount;
        int[] oldActiveBrickX = java.util.Arrays.copyOf(activeBrickX, oldActiveBrickCount);
        int[] oldActiveBrickY = java.util.Arrays.copyOf(activeBrickY, oldActiveBrickCount);
        int[] oldActiveBrickZ = java.util.Arrays.copyOf(activeBrickZ, oldActiveBrickCount);
        boolean[] oldActiveBrickReady = java.util.Arrays.copyOf(activeBrickReady, oldActiveBrickCount);
        activeBrickCount = 0;
        prepareCursor = 0;
        refreshCursor = 0;
        publishCursor = 0;
        activeHintCoords = new int[MAX_CLIENT_ACTIVE_BRICKS * NativeSimulationBridge.BRICK_HINT_COORDS_PER_BRICK];
        java.util.Arrays.fill(activeBrickReady, false);
        java.util.Arrays.fill(activeBrickRefreshPending, false);
        java.util.Arrays.fill(activeBrickBoundaryRefreshPending, false);
        addActiveBrick(
            coreBrickX,
            coreBrickY,
            coreBrickZ,
            oldActiveBrickX,
            oldActiveBrickY,
            oldActiveBrickZ,
            oldActiveBrickReady
        );
        int[] neighborOffset = MAX_CLIENT_ACTIVE_BRICKS > 1
            ? nearestBoundaryNeighborOffset(localX, localY, localZ)
            : null;
        if (neighborOffset != null) {
            addActiveBrick(
                coreBrickX + neighborOffset[0],
                coreBrickY + neighborOffset[1],
                coreBrickZ + neighborOffset[2],
                oldActiveBrickX,
                oldActiveBrickY,
                oldActiveBrickZ,
                oldActiveBrickReady
            );
        }
        activeHintCoords = java.util.Arrays.copyOf(
            activeHintCoords,
            activeBrickCount * NativeSimulationBridge.BRICK_HINT_COORDS_PER_BRICK
        );
    }

    private void addActiveBrick(
        int brickX,
        int brickY,
        int brickZ,
        int[] oldActiveBrickX,
        int[] oldActiveBrickY,
        int[] oldActiveBrickZ,
        boolean[] oldActiveBrickReady
    ) {
        if (activeBrickCount >= MAX_CLIENT_ACTIVE_BRICKS) {
            return;
        }
        int index = activeBrickCount++;
        activeBrickX[index] = brickX;
        activeBrickY[index] = brickY;
        activeBrickZ[index] = brickZ;
        activeBrickReady[index] = wasActiveBrickReady(
            brickX,
            brickY,
            brickZ,
            oldActiveBrickX,
            oldActiveBrickY,
            oldActiveBrickZ,
            oldActiveBrickReady
        );
        activeBrickBoundaryRefreshPending[index] = true;
        int hintBase = index * NativeSimulationBridge.BRICK_HINT_COORDS_PER_BRICK;
        activeHintCoords[hintBase] = brickX;
        activeHintCoords[hintBase + 1] = brickY;
        activeHintCoords[hintBase + 2] = brickZ;
    }

    private boolean wasActiveBrickReady(
        int brickX,
        int brickY,
        int brickZ,
        int[] oldActiveBrickX,
        int[] oldActiveBrickY,
        int[] oldActiveBrickZ,
        boolean[] oldActiveBrickReady
    ) {
        for (int index = 0; index < oldActiveBrickReady.length; index++) {
            if (oldActiveBrickReady[index]
                && oldActiveBrickX[index] == brickX
                && oldActiveBrickY[index] == brickY
                && oldActiveBrickZ[index] == brickZ) {
                return true;
            }
        }
        return false;
    }

    private boolean prepareActiveBricks(Minecraft client, ClientLevel level, ResourceLocation dimensionId) {
        if (activeBrickCount <= 0) {
            return false;
        }
        for (int attempts = 0; attempts < activeBrickCount; attempts++) {
            int index = (prepareCursor + attempts) % activeBrickCount;
            if (activeBrickReady[index]) {
                continue;
            }
            BrickPreparationResult result = uploadAndSeedActiveBrick(client, level, dimensionId, index);
            if (result == BrickPreparationResult.IN_PROGRESS) {
                return false;
            }
            if (result == BrickPreparationResult.FAILED) {
                return false;
            }
            activeBrickReady[index] = true;
            activeBrickBoundaryRefreshPending[index] = false;
            prepareCursor = (index + 1) % activeBrickCount;
            return false;
        }
        return true;
    }

    private boolean hasReadyActiveBrick() {
        for (int index = 0; index < activeBrickCount; index++) {
            if (activeBrickReady[index]) {
                return true;
            }
        }
        return false;
    }

    private BrickPreparationResult uploadAndSeedActiveBrick(
        Minecraft client,
        ClientLevel level,
        ResourceLocation dimensionId,
        int activeIndex
    ) {
        int brickX = activeBrickX[activeIndex];
        int brickY = activeBrickY[activeIndex];
        int brickZ = activeBrickZ[activeIndex];
        BlockPos origin = brickOrigin(brickX, brickY, brickZ);
        if (!stagedPreparationMatches(activeIndex, dimensionId, brickX, brickY, brickZ)) {
            beginStagedPreparation(activeIndex, dimensionId, origin, brickX, brickY, brickZ);
        }
        if (!buildStagedStaticCells(level)) {
            return BrickPreparationResult.IN_PROGRESS;
        }
        cacheStagedStaticBrickIfNeeded();
        stagedStaticUploaded = true;
        if (!buildStagedCoarseSeedCells(dimensionId)) {
            return BrickPreparationResult.IN_PROGRESS;
        }
        if (!stagedDynamicUploaded) {
            worker.submitBrickSeed(new BrickSeedCommand(
                levelKey,
                brickX,
                brickY,
                brickZ,
                java.util.Arrays.copyOf(obstacle, obstacle.length),
                java.util.Arrays.copyOf(surfaceKind, surfaceKind.length),
                java.util.Arrays.copyOf(openFaceMask, openFaceMask.length),
                java.util.Arrays.copyOf(emitterPower, emitterPower.length),
                java.util.Arrays.copyOf(faceSkyExposure, faceSkyExposure.length),
                java.util.Arrays.copyOf(faceDirectExposure, faceDirectExposure.length),
                java.util.Arrays.copyOf(flowState, flowState.length),
                java.util.Arrays.copyOf(airTemperature, airTemperature.length),
                java.util.Arrays.copyOf(surfaceTemperature, surfaceTemperature.length)
            ));
            stagedDynamicUploaded = true;
            return BrickPreparationResult.IN_PROGRESS;
        }
        cancelStagedPreparation();
        return BrickPreparationResult.COMPLETED;
    }

    private boolean stagedPreparationMatches(
        int activeIndex,
        ResourceLocation dimensionId,
        int brickX,
        int brickY,
        int brickZ
    ) {
        return stagedActiveIndex == activeIndex
            && stagedDimension != null
            && stagedDimension.equals(dimensionId)
            && stagedBrickX == brickX
            && stagedBrickY == brickY
            && stagedBrickZ == brickZ;
    }

    private void beginStagedPreparation(
        int activeIndex,
        ResourceLocation dimensionId,
        BlockPos origin,
        int brickX,
        int brickY,
        int brickZ
    ) {
        stagedActiveIndex = activeIndex;
        stagedDimension = dimensionId;
        stagedOrigin = origin;
        stagedBrickX = brickX;
        stagedBrickY = brickY;
        stagedBrickZ = brickZ;
        stagedStaticCursor = 0;
        stagedSeedCursor = 0;
        stagedStaticUploaded = false;
        stagedDynamicUploaded = false;
        stagedStaticFromCache = false;
        java.util.Arrays.fill(obstacle, (byte) 0);
        java.util.Arrays.fill(surfaceKind, (byte) 0);
        java.util.Arrays.fill(openFaceMask, (short) 0);
        java.util.Arrays.fill(emitterPower, 0.0f);
        java.util.Arrays.fill(faceSkyExposure, (byte) 0);
        java.util.Arrays.fill(faceDirectExposure, (byte) 0);
        java.util.Arrays.fill(flowState, 0.0f);
        java.util.Arrays.fill(airTemperature, 0.0f);
        java.util.Arrays.fill(surfaceTemperature, 0.0f);
        StaticBrickSnapshot cached = staticBrickCache.get(new StaticBrickCacheKey(dimensionId, brickX, brickY, brickZ));
        if (cached != null) {
            cached.copyInto(obstacle, surfaceKind, openFaceMask, emitterPower);
            stagedStaticCursor = CELL_COUNT;
            stagedStaticFromCache = true;
        }
    }

    private boolean buildStagedStaticCells(ClientLevel level) {
        if (stagedOrigin == null) {
            return false;
        }
        int end = Math.min(CELL_COUNT, stagedStaticCursor + STATIC_BUILD_CELLS_PER_TICK);
        while (stagedStaticCursor < end) {
            int cell = stagedStaticCursor++;
            int x = cell / (BRICK_SIZE * BRICK_SIZE);
            int rem = cell - x * BRICK_SIZE * BRICK_SIZE;
            int y = rem / BRICK_SIZE;
            int z = rem - y * BRICK_SIZE;
            populateStaticCell(level, stagedOrigin, x, y, z);
        }
        return stagedStaticCursor >= CELL_COUNT;
    }

    private void cacheStagedStaticBrickIfNeeded() {
        if (stagedStaticFromCache || stagedDimension == null || stagedOrigin == null) {
            return;
        }
        staticBrickCache.put(
            new StaticBrickCacheKey(stagedDimension, stagedBrickX, stagedBrickY, stagedBrickZ),
            StaticBrickSnapshot.copyFrom(obstacle, surfaceKind, openFaceMask, emitterPower)
        );
        stagedStaticFromCache = true;
    }

    private boolean buildStagedCoarseSeedCells(ResourceLocation dimensionId) {
        if (stagedOrigin == null) {
            return false;
        }
        int built = 0;
        while (stagedSeedCursor < CELL_COUNT && built < COARSE_SEED_CELLS_PER_TICK) {
            int cell = stagedSeedCursor;
            int x = cell / (BRICK_SIZE * BRICK_SIZE);
            int rem = cell - x * BRICK_SIZE * BRICK_SIZE;
            int y = rem / BRICK_SIZE;
            int z = rem - y * BRICK_SIZE;
            if (obstacle[cell] == 0) {
                Vec3 pos = new Vec3(
                    stagedOrigin.getX() + x + 0.5,
                    stagedOrigin.getY() + y + 0.5,
                    stagedOrigin.getZ() + z + 0.5
                );
                AeroWindSample coarse = visualizer.sampleServerCoarseFlow(dimensionId, pos);
                if (!coarse.hasFlow()) {
                    return false;
                }
                int base = cell * FLOW_CHANNELS;
                flowState[base] = coarse.velocityX() / NATIVE_VELOCITY_SCALE;
                flowState[base + 1] = coarse.velocityY() / NATIVE_VELOCITY_SCALE;
                flowState[base + 2] = coarse.velocityZ() / NATIVE_VELOCITY_SCALE;
                flowState[base + 3] = coarse.pressure();
            }
            stagedSeedCursor++;
            built++;
        }
        return stagedSeedCursor >= CELL_COUNT;
    }

    private void cancelStagedPreparation() {
        stagedActiveIndex = -1;
        stagedOrigin = null;
        stagedDimension = null;
        stagedBrickX = 0;
        stagedBrickY = 0;
        stagedBrickZ = 0;
        stagedStaticCursor = 0;
        stagedSeedCursor = 0;
        stagedStaticUploaded = false;
        stagedDynamicUploaded = false;
        stagedStaticFromCache = false;
    }

    private boolean refreshActiveBrickStatic(Minecraft client, ClientLevel level) {
        if (STATIC_REFRESH_TICKS <= 0) {
            java.util.Arrays.fill(activeBrickRefreshPending, false);
            return false;
        }
        if (!hasRefreshPending()) {
            if (ticksSinceStaticRefresh++ < STATIC_REFRESH_TICKS) {
                return false;
            }
            java.util.Arrays.fill(activeBrickRefreshPending, 0, activeBrickCount, true);
            refreshCursor = 0;
            ticksSinceStaticRefresh = 0;
        }
        for (int attempts = 0; attempts < activeBrickCount; attempts++) {
            int index = (refreshCursor + attempts) % activeBrickCount;
            if (!activeBrickRefreshPending[index]) {
                continue;
            }
            int brickX = activeBrickX[index];
            int brickY = activeBrickY[index];
            int brickZ = activeBrickZ[index];
            uploadStaticBrick(level, brickOrigin(brickX, brickY, brickZ), brickX, brickY, brickZ);
            activeBrickRefreshPending[index] = false;
            refreshCursor = (index + 1) % activeBrickCount;
            return true;
        }
        return false;
    }

    private boolean hasRefreshPending() {
        for (int index = 0; index < activeBrickCount; index++) {
            if (activeBrickRefreshPending[index]) {
                return true;
            }
        }
        return false;
    }

    private void markBoundaryRefreshPending() {
        for (int index = 0; index < activeBrickCount; index++) {
            if (activeBrickReady[index]) {
                activeBrickBoundaryRefreshPending[index] = true;
            }
        }
    }

    private boolean refreshActiveBrickBoundaryReference(
        Minecraft client,
        ResourceLocation dimensionId,
        long clientGameTime
    ) {
        if (!hasBoundaryRefreshPending() && boundaryRefreshActiveIndex < 0) {
            return false;
        }
        if (stagedActiveIndex >= 0) {
            return false;
        }
        if (boundaryRefreshActiveIndex < 0
            && lastBoundaryRefreshClientGameTime != Long.MIN_VALUE
            && clientGameTime - lastBoundaryRefreshClientGameTime < BOUNDARY_REFERENCE_REFRESH_MIN_TICKS) {
            return false;
        }
        for (int attempts = 0; attempts < activeBrickCount; attempts++) {
            int index = boundaryRefreshActiveIndex >= 0
                ? boundaryRefreshActiveIndex
                : (refreshCursor + attempts) % activeBrickCount;
            if (!activeBrickBoundaryRefreshPending[index]) {
                if (boundaryRefreshActiveIndex >= 0) {
                    cancelBoundaryReferenceRefresh();
                }
                continue;
            }
            if (!activeBrickReady[index]) {
                activeBrickBoundaryRefreshPending[index] = false;
                if (boundaryRefreshActiveIndex >= 0) {
                    cancelBoundaryReferenceRefresh();
                }
                continue;
            }
            int brickX = activeBrickX[index];
            int brickY = activeBrickY[index];
            int brickZ = activeBrickZ[index];
            BlockPos origin = brickOrigin(brickX, brickY, brickZ);
            if (!boundaryReferenceRefreshMatches(index, dimensionId, brickX, brickY, brickZ)) {
                beginBoundaryReferenceRefresh(index, dimensionId, origin, brickX, brickY, brickZ);
            }
            BoundaryReferenceBuildResult result = buildBoundaryReferenceCells(dimensionId);
            if (result == BoundaryReferenceBuildResult.WAITING_FOR_COARSE) {
                maybeLog(client, "client L2 boundary refresh waiting for coarse field");
                return false;
            }
            if (result == BoundaryReferenceBuildResult.IN_PROGRESS) {
                return false;
            }
            worker.submitBoundaryReference(new BoundaryReferenceCommand(
                levelKey,
                brickX,
                brickY,
                brickZ,
                java.util.Arrays.copyOf(flowState, flowState.length),
                java.util.Arrays.copyOf(airTemperature, airTemperature.length),
                java.util.Arrays.copyOf(surfaceTemperature, surfaceTemperature.length),
                boundaryRefreshMaxCoarseSpeed
            ));
            activeBrickBoundaryRefreshPending[index] = false;
            refreshCursor = (index + 1) % activeBrickCount;
            lastBoundaryRefreshClientGameTime = clientGameTime;
            cancelBoundaryReferenceRefresh();
            return false;
        }
        return false;
    }

    private boolean boundaryReferenceRefreshMatches(
        int activeIndex,
        ResourceLocation dimensionId,
        int brickX,
        int brickY,
        int brickZ
    ) {
        return boundaryRefreshActiveIndex == activeIndex
            && boundaryRefreshDimension != null
            && boundaryRefreshDimension.equals(dimensionId)
            && boundaryRefreshBrickX == brickX
            && boundaryRefreshBrickY == brickY
            && boundaryRefreshBrickZ == brickZ;
    }

    private void beginBoundaryReferenceRefresh(
        int activeIndex,
        ResourceLocation dimensionId,
        BlockPos origin,
        int brickX,
        int brickY,
        int brickZ
    ) {
        boundaryRefreshActiveIndex = activeIndex;
        boundaryRefreshDimension = dimensionId;
        boundaryRefreshOrigin = origin;
        boundaryRefreshBrickX = brickX;
        boundaryRefreshBrickY = brickY;
        boundaryRefreshBrickZ = brickZ;
        boundaryRefreshCursor = 0;
        boundaryRefreshMaxCoarseSpeed = 0.0f;
        java.util.Arrays.fill(airTemperature, 0.0f);
        java.util.Arrays.fill(surfaceTemperature, 0.0f);
    }

    private BoundaryReferenceBuildResult buildBoundaryReferenceCells(ResourceLocation dimensionId) {
        if (boundaryRefreshOrigin == null) {
            return BoundaryReferenceBuildResult.WAITING_FOR_COARSE;
        }
        int built = 0;
        while (boundaryRefreshCursor < CELL_COUNT && built < BOUNDARY_REFERENCE_CELLS_PER_TICK) {
            int cell = boundaryRefreshCursor;
            int x = cell / (BRICK_SIZE * BRICK_SIZE);
            int rem = cell - x * BRICK_SIZE * BRICK_SIZE;
            int y = rem / BRICK_SIZE;
            int z = rem - y * BRICK_SIZE;
            int base = cell * FLOW_CHANNELS;
            if (obstacle[cell] == 0) {
                Vec3 pos = new Vec3(
                    boundaryRefreshOrigin.getX() + x + 0.5,
                    boundaryRefreshOrigin.getY() + y + 0.5,
                    boundaryRefreshOrigin.getZ() + z + 0.5
                );
                AeroWindSample coarse = visualizer.sampleServerCoarseFlow(dimensionId, pos);
                if (!coarse.hasFlow()) {
                    return BoundaryReferenceBuildResult.WAITING_FOR_COARSE;
                }
                flowState[base] = coarse.velocityX() / NATIVE_VELOCITY_SCALE;
                flowState[base + 1] = coarse.velocityY() / NATIVE_VELOCITY_SCALE;
                flowState[base + 2] = coarse.velocityZ() / NATIVE_VELOCITY_SCALE;
                flowState[base + 3] = coarse.pressure();
                float speed = (float) coarse.velocity().length();
                if (Float.isFinite(speed) && speed > boundaryRefreshMaxCoarseSpeed) {
                    boundaryRefreshMaxCoarseSpeed = speed;
                }
            } else {
                flowState[base] = 0.0f;
                flowState[base + 1] = 0.0f;
                flowState[base + 2] = 0.0f;
                flowState[base + 3] = 0.0f;
            }
            boundaryRefreshCursor++;
            built++;
        }
        return boundaryRefreshCursor >= CELL_COUNT
            ? BoundaryReferenceBuildResult.COMPLETED
            : BoundaryReferenceBuildResult.IN_PROGRESS;
    }

    private void cancelBoundaryReferenceRefresh() {
        boundaryRefreshActiveIndex = -1;
        boundaryRefreshDimension = null;
        boundaryRefreshOrigin = null;
        boundaryRefreshBrickX = 0;
        boundaryRefreshBrickY = 0;
        boundaryRefreshBrickZ = 0;
        boundaryRefreshCursor = 0;
        boundaryRefreshMaxCoarseSpeed = 0.0f;
    }

    private boolean hasBoundaryRefreshPending() {
        for (int index = 0; index < activeBrickCount; index++) {
            if (activeBrickBoundaryRefreshPending[index]) {
                return true;
            }
        }
        return false;
    }

    private boolean uploadStaticBrick(ClientLevel level, BlockPos origin, int brickX, int brickY, int brickZ) {
        StaticBrickCacheKey key = activeDimension == null ? null : new StaticBrickCacheKey(activeDimension, brickX, brickY, brickZ);
        StaticBrickSnapshot cached = key == null ? null : staticBrickCache.get(key);
        if (cached != null) {
            cached.copyInto(obstacle, surfaceKind, openFaceMask, emitterPower);
            java.util.Arrays.fill(faceSkyExposure, (byte) 0);
            java.util.Arrays.fill(faceDirectExposure, (byte) 0);
            return true;
        }
        populateStaticBrickArrays(level, origin);
        if (key != null) {
            staticBrickCache.put(key, StaticBrickSnapshot.copyFrom(obstacle, surfaceKind, openFaceMask, emitterPower));
        }
        return true;
    }

    private NativeSimulationBridge.LevelDelta[] buildStaticPatchDeltas(
        ClientLevel level,
        BlockPos center,
        BlockState oldState,
        BlockState newState
    ) {
        java.util.LinkedHashSet<BlockPos> positions = new java.util.LinkedHashSet<>();
        addStaticPatchPosition(positions, center);
        for (Direction direction : Direction.values()) {
            addStaticPatchPosition(positions, center.relative(direction));
        }
        addFanOcclusionPatchPositions(positions, center);
        addHeatOcclusionPatchPositions(positions, center);
        addForcingSourcePatchPositions(positions, center, oldState);
        addForcingSourcePatchPositions(positions, center, newState);

        NativeSimulationBridge.LevelDelta[] deltas = new NativeSimulationBridge.LevelDelta[positions.size()];
        int count = 0;
        for (BlockPos pos : positions) {
            deltas[count++] = buildStaticCellPatchDelta(level, pos);
        }
        return count == deltas.length ? deltas : java.util.Arrays.copyOf(deltas, count);
    }

    private void addStaticPatchPosition(java.util.LinkedHashSet<BlockPos> positions, BlockPos pos) {
        positions.add(pos.immutable());
    }

    private void addFanOcclusionPatchPositions(java.util.LinkedHashSet<BlockPos> positions, BlockPos center) {
        for (Direction direction : Direction.values()) {
            for (int distance = 1; distance <= FAN_FORCE_LENGTH_CELLS; distance++) {
                addFanDiskPatchPositions(positions, offset(center, direction, distance), direction);
            }
        }
    }

    private void addHeatOcclusionPatchPositions(java.util.LinkedHashSet<BlockPos> positions, BlockPos center) {
        for (int distance = 1; distance <= HEAT_PLUME_HEIGHT_CELLS; distance++) {
            addStaticPatchPosition(positions, offset(center, Direction.UP, distance));
        }
    }

    private void addForcingSourcePatchPositions(
        java.util.LinkedHashSet<BlockPos> positions,
        BlockPos center,
        BlockState state
    ) {
        if (state == null) {
            return;
        }
        if (state.is(ModBlocks.FAN_BLOCK.get())) {
            Direction direction = state.getOptionalValue(FanBlock.FACING).orElse(Direction.NORTH);
            addFanFootprintPatchPositions(positions, center, direction);
        }
        if (sampleEmitterThermalPowerWatts(state) > 0.0f) {
            for (int distance = 0; distance <= HEAT_PLUME_HEIGHT_CELLS; distance++) {
                addStaticPatchPosition(positions, offset(center, Direction.UP, distance));
            }
        }
    }

    private BlockPos offset(BlockPos pos, Direction direction, int distance) {
        return new BlockPos(
            pos.getX() + direction.getStepX() * distance,
            pos.getY() + direction.getStepY() * distance,
            pos.getZ() + direction.getStepZ() * distance
        );
    }

    private void addFanFootprintPatchPositions(
        java.util.LinkedHashSet<BlockPos> positions,
        BlockPos fanPos,
        Direction direction
    ) {
        for (int distance = 1; distance <= FAN_FORCE_LENGTH_CELLS; distance++) {
            addFanDiskPatchPositions(positions, offset(fanPos, direction, distance), direction);
        }
    }

    private void addFanDiskPatchPositions(
        java.util.LinkedHashSet<BlockPos> positions,
        BlockPos center,
        Direction direction
    ) {
        Direction.Axis axis = direction.getAxis();
        for (int a = -FAN_FORCE_RADIUS_CELLS; a <= FAN_FORCE_RADIUS_CELLS; a++) {
            for (int b = -FAN_FORCE_RADIUS_CELLS; b <= FAN_FORCE_RADIUS_CELLS; b++) {
                addStaticPatchPosition(positions, offsetPerpendicular(center, axis, a, b));
            }
        }
    }

    private BlockPos offsetPerpendicular(BlockPos pos, Direction.Axis axis, int a, int b) {
        return switch (axis) {
            case X -> new BlockPos(pos.getX(), pos.getY() + a, pos.getZ() + b);
            case Y -> new BlockPos(pos.getX() + a, pos.getY(), pos.getZ() + b);
            case Z -> new BlockPos(pos.getX() + a, pos.getY() + b, pos.getZ());
        };
    }

    private boolean blockPatchTouchesActiveBrick(BlockPos center) {
        if (blockInActiveBrick(center)) {
            return true;
        }
        for (Direction direction : Direction.values()) {
            if (blockInActiveBrick(center.relative(direction))) {
                return true;
            }
            for (int distance = 1; distance <= FAN_FORCE_LENGTH_CELLS; distance++) {
                BlockPos fanCenter = offset(center, direction, distance);
                Direction.Axis axis = direction.getAxis();
                for (int a = -FAN_FORCE_RADIUS_CELLS; a <= FAN_FORCE_RADIUS_CELLS; a++) {
                    for (int b = -FAN_FORCE_RADIUS_CELLS; b <= FAN_FORCE_RADIUS_CELLS; b++) {
                        if (blockInActiveBrick(offsetPerpendicular(fanCenter, axis, a, b))) {
                            return true;
                        }
                    }
                }
            }
        }
        for (int distance = 2; distance <= HEAT_PLUME_HEIGHT_CELLS; distance++) {
            if (blockInActiveBrick(offset(center, Direction.UP, distance))) {
                return true;
            }
        }
        return false;
    }

    private boolean blockInActiveBrick(BlockPos pos) {
        int brickX = Math.floorDiv(pos.getX(), BRICK_SIZE);
        int brickY = Math.floorDiv(pos.getY(), BRICK_SIZE);
        int brickZ = Math.floorDiv(pos.getZ(), BRICK_SIZE);
        for (int index = 0; index < activeBrickCount; index++) {
            if (activeBrickX[index] == brickX && activeBrickY[index] == brickY && activeBrickZ[index] == brickZ) {
                return true;
            }
        }
        return false;
    }

    private NativeSimulationBridge.LevelDelta buildStaticCellPatchDelta(ClientLevel level, BlockPos pos) {
        StaticCellSample sample = sampleStaticCell(level, pos);
        int packedState = (sample.solid() ? 1 : 0)
            | ((Byte.toUnsignedInt(sample.surfaceKind()) & 0xFF) << 8);
        return new NativeSimulationBridge.LevelDelta(
            NativeSimulationBridge.Level_DELTA_BRICK_STATIC_CELL_PATCH,
            pos.getX(),
            pos.getY(),
            pos.getZ(),
            (int) levelKey,
            packedState,
            Short.toUnsignedInt(sample.openFaceMask()),
            0,
            sample.emitterPowerWatts(),
            0.0f,
            0.0f,
            0.0f
        );
    }

    private void refreshLocalStaticCellIfActive(ClientLevel level, BlockPos pos) {
        for (int index = 0; index < activeBrickCount; index++) {
            int brickX = Math.floorDiv(pos.getX(), BRICK_SIZE);
            int brickY = Math.floorDiv(pos.getY(), BRICK_SIZE);
            int brickZ = Math.floorDiv(pos.getZ(), BRICK_SIZE);
            if (activeBrickX[index] != brickX || activeBrickY[index] != brickY || activeBrickZ[index] != brickZ) {
                continue;
            }
            BlockPos origin = brickOrigin(brickX, brickY, brickZ);
            int localX = pos.getX() - origin.getX();
            int localY = pos.getY() - origin.getY();
            int localZ = pos.getZ() - origin.getZ();
            if (localX >= 0 && localY >= 0 && localZ >= 0
                && localX < BRICK_SIZE && localY < BRICK_SIZE && localZ < BRICK_SIZE) {
                populateStaticCell(level, origin, localX, localY, localZ);
            }
        }
    }

    private void invalidateStaticCacheForPatchFootprint(
        ResourceLocation dimensionId,
        BlockPos center,
        BlockState oldState,
        BlockState newState
    ) {
        java.util.LinkedHashSet<BlockPos> positions = new java.util.LinkedHashSet<>();
        addStaticPatchPosition(positions, center);
        for (Direction direction : Direction.values()) {
            addStaticPatchPosition(positions, center.relative(direction));
        }
        addFanOcclusionPatchPositions(positions, center);
        addHeatOcclusionPatchPositions(positions, center);
        addForcingSourcePatchPositions(positions, center, oldState);
        addForcingSourcePatchPositions(positions, center, newState);
        for (BlockPos pos : positions) {
            invalidateStaticCacheForBlock(dimensionId, pos);
        }
    }

    private void invalidateStaticCacheForBlock(ResourceLocation dimensionId, BlockPos pos) {
        staticBrickCache.remove(new StaticBrickCacheKey(
            dimensionId,
            Math.floorDiv(pos.getX(), BRICK_SIZE),
            Math.floorDiv(pos.getY(), BRICK_SIZE),
            Math.floorDiv(pos.getZ(), BRICK_SIZE)
        ));
    }

    private void markActiveBrickStaticRefreshPending(BlockPos pos) {
        int brickX = Math.floorDiv(pos.getX(), BRICK_SIZE);
        int brickY = Math.floorDiv(pos.getY(), BRICK_SIZE);
        int brickZ = Math.floorDiv(pos.getZ(), BRICK_SIZE);
        for (int index = 0; index < activeBrickCount; index++) {
            if (activeBrickX[index] == brickX && activeBrickY[index] == brickY && activeBrickZ[index] == brickZ) {
                activeBrickRefreshPending[index] = true;
                activeBrickBoundaryRefreshPending[index] = true;
                ticksSinceStaticRefresh = 0;
            }
        }
    }

    private void populateStaticBrickArrays(ClientLevel level, BlockPos origin) {
        java.util.Arrays.fill(obstacle, (byte) 0);
        java.util.Arrays.fill(surfaceKind, (byte) 0);
        java.util.Arrays.fill(openFaceMask, (short) 0);
        java.util.Arrays.fill(emitterPower, 0.0f);
        java.util.Arrays.fill(faceSkyExposure, (byte) 0);
        java.util.Arrays.fill(faceDirectExposure, (byte) 0);

        for (int x = 0; x < BRICK_SIZE; x++) {
            for (int y = 0; y < BRICK_SIZE; y++) {
                for (int z = 0; z < BRICK_SIZE; z++) {
                    populateStaticCell(level, origin, x, y, z);
                }
            }
        }
    }

    private void populateStaticCell(ClientLevel level, BlockPos origin, int x, int y, int z) {
        staticCursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
        int cell = cellIndex(x, y, z);
        StaticCellSample sample = sampleStaticCell(level, staticCursor);
        obstacle[cell] = sample.solid() ? (byte) 1 : (byte) 0;
        surfaceKind[cell] = sample.surfaceKind();
        openFaceMask[cell] = sample.openFaceMask();
        emitterPower[cell] = sample.emitterPowerWatts();
    }

    private StaticCellSample sampleStaticCell(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        boolean solid = isSolidObstacle(level, pos, state);
        short mask = 0;
        if (!solid) {
            for (Direction direction : Direction.values()) {
                staticNeighbor.set(
                    pos.getX() + direction.getStepX(),
                    pos.getY() + direction.getStepY(),
                    pos.getZ() + direction.getStepZ()
                );
                if (!isSolidObstacle(level, staticNeighbor, level.getBlockState(staticNeighbor))) {
                    mask = (short) (mask | (1 << direction.ordinal()));
                }
            }
        }
        byte kind = solid ? (byte) 0 : fanSurfaceKindForCell(level, pos);
        float emitter = solid ? 0.0f : emitterPowerForCell(level, pos, state);
        return new StaticCellSample(solid, kind, mask, emitter);
    }

    private byte fanSurfaceKindForCell(ClientLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            Direction.Axis axis = direction.getAxis();
            for (int distance = 1; distance <= FAN_FORCE_LENGTH_CELLS; distance++) {
                BlockPos axialOrigin = offset(pos, direction.getOpposite(), distance);
                for (int a = -FAN_FORCE_RADIUS_CELLS; a <= FAN_FORCE_RADIUS_CELLS; a++) {
                    for (int b = -FAN_FORCE_RADIUS_CELLS; b <= FAN_FORCE_RADIUS_CELLS; b++) {
                        BlockPos fanPos = offsetPerpendicular(axialOrigin, axis, -a, -b);
                        BlockState fanState = level.getBlockState(fanPos);
                        if (!fanState.is(ModBlocks.FAN_BLOCK.get())
                            || fanState.getOptionalValue(FanBlock.FACING).orElse(Direction.NORTH) != direction) {
                            continue;
                        }
                        if (!fanPathClear(level, fanPos, direction, distance, a, b)) {
                            continue;
                        }
                        return fanSurfaceKind(direction);
                    }
                }
            }
        }
        return 0;
    }

    private boolean fanPathClear(ClientLevel level, BlockPos fanPos, Direction direction, int distance, int a, int b) {
        BlockPos laneStart = offsetPerpendicular(fanPos, direction.getAxis(), a, b);
        for (int step = 1; step < distance; step++) {
            staticNeighbor.set(
                laneStart.getX() + direction.getStepX() * step,
                laneStart.getY() + direction.getStepY() * step,
                laneStart.getZ() + direction.getStepZ() * step
            );
            if (isSolidObstacle(level, staticNeighbor, level.getBlockState(staticNeighbor))) {
                return false;
            }
        }
        return true;
    }

    private byte fanSurfaceKind(Direction direction) {
        return switch (direction) {
            case WEST -> SURFACE_KIND_FAN_X_NEG;
            case EAST -> SURFACE_KIND_FAN_X_POS;
            case DOWN -> SURFACE_KIND_FAN_Y_NEG;
            case UP -> SURFACE_KIND_FAN_Y_POS;
            case NORTH -> SURFACE_KIND_FAN_Z_NEG;
            case SOUTH -> SURFACE_KIND_FAN_Z_POS;
        };
    }

    private boolean isFanSurfaceKind(int surfaceKind) {
        return surfaceKind >= Byte.toUnsignedInt(SURFACE_KIND_FAN_X_NEG)
            && surfaceKind <= Byte.toUnsignedInt(SURFACE_KIND_FAN_Z_POS);
    }

    private float emitterPowerForCell(ClientLevel level, BlockPos pos, BlockState state) {
        float directPower = sampleEmitterThermalPowerWatts(state);
        if (directPower > 0.0f) {
            return directPower;
        }
        float coupledPower = 0.0f;
        for (int distance = 1; distance <= HEAT_PLUME_HEIGHT_CELLS; distance++) {
            int sourceY = pos.getY() - distance;
            staticNeighbor.set(pos.getX(), sourceY, pos.getZ());
            float belowPower = sampleEmitterThermalPowerWatts(level.getBlockState(staticNeighbor));
            if (belowPower <= 0.0f || !heatPathClear(level, pos.getX(), sourceY, pos.getZ(), pos.getY())) {
                continue;
            }
            float falloff = HEAT_COUPLING_TO_ADJACENT_AIR / (distance * distance);
            coupledPower += belowPower * falloff;
        }
        return coupledPower;
    }

    private boolean heatPathClear(ClientLevel level, int sourceX, int sourceY, int sourceZ, int targetY) {
        for (int y = sourceY + 1; y < targetY; y++) {
            staticNeighbor.set(sourceX, y, sourceZ);
            if (isSolidObstacle(level, staticNeighbor, level.getBlockState(staticNeighbor))) {
                return false;
            }
        }
        return true;
    }

    private float sampleEmitterThermalPowerWatts(BlockState state) {
        float powerWatts = 0.0f;
        if (state.is(Blocks.LAVA) || state.is(Blocks.LAVA_CAULDRON)) {
            powerWatts += THERMAL_EMITTER_POWER_LAVA_W;
        }
        if (state.is(Blocks.MAGMA_BLOCK)) {
            powerWatts += THERMAL_EMITTER_POWER_MAGMA_W;
        }
        if (state.is(Blocks.CAMPFIRE)) {
            powerWatts += state.getOptionalValue(BlockStateProperties.LIT).orElse(false) ? THERMAL_EMITTER_POWER_CAMPFIRE_W : 0.0f;
        }
        if (state.is(Blocks.SOUL_CAMPFIRE)) {
            powerWatts += state.getOptionalValue(BlockStateProperties.LIT).orElse(false) ? THERMAL_EMITTER_POWER_SOUL_CAMPFIRE_W : 0.0f;
        }
        if (state.is(Blocks.FIRE)) {
            powerWatts += THERMAL_EMITTER_POWER_FIRE_W;
        }
        if (state.is(Blocks.SOUL_FIRE)) {
            powerWatts += THERMAL_EMITTER_POWER_SOUL_FIRE_W;
        }
        if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)) {
            powerWatts += THERMAL_EMITTER_POWER_TORCH_W;
        }
        if (state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH)) {
            powerWatts += THERMAL_EMITTER_POWER_SOUL_TORCH_W;
        }
        if (state.is(Blocks.LANTERN)) {
            powerWatts += THERMAL_EMITTER_POWER_LANTERN_W;
        }
        if (state.is(Blocks.SOUL_LANTERN)) {
            powerWatts += THERMAL_EMITTER_POWER_SOUL_LANTERN_W;
        }
        return Math.max(powerWatts, 0.0f);
    }

    private CoarseSeedStats fillFlowStateFromCoarse(ResourceLocation dimensionId, BlockPos origin) {
        java.util.Arrays.fill(flowState, 0.0f);
        java.util.Arrays.fill(airTemperature, 0.0f);
        java.util.Arrays.fill(surfaceTemperature, 0.0f);
        float maxCoarseSpeed = 0.0f;
        for (int x = 0; x < BRICK_SIZE; x++) {
            for (int y = 0; y < BRICK_SIZE; y++) {
                for (int z = 0; z < BRICK_SIZE; z++) {
                    int cell = cellIndex(x, y, z);
                    if (obstacle[cell] != 0) {
                        continue;
                    }
                    Vec3 pos = new Vec3(origin.getX() + x + 0.5, origin.getY() + y + 0.5, origin.getZ() + z + 0.5);
                    AeroWindSample coarse = visualizer.sampleServerCoarseFlow(dimensionId, pos);
                    if (!coarse.hasFlow()) {
                        return null;
                    }
                    int base = cell * FLOW_CHANNELS;
                    flowState[base] = coarse.velocityX() / NATIVE_VELOCITY_SCALE;
                    flowState[base + 1] = coarse.velocityY() / NATIVE_VELOCITY_SCALE;
                    flowState[base + 2] = coarse.velocityZ() / NATIVE_VELOCITY_SCALE;
                    flowState[base + 3] = coarse.pressure();
                    float speed = (float) coarse.velocity().length();
                    if (Float.isFinite(speed) && speed > maxCoarseSpeed) {
                        maxCoarseSpeed = speed;
                    }
                }
            }
        }
        return new CoarseSeedStats(maxCoarseSpeed);
    }

    private boolean isSolidObstacle(ClientLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || state.is(ModBlocks.DUCT_BLOCK.get())) {
            return false;
        }
        return !state.getCollisionShape(level, pos).isEmpty();
    }

    private static short quantizeSignedToShort(float value, float range) {
        if (!(range > 0.0f) || !Float.isFinite(value)) {
            return 0;
        }
        float normalized = Mth.clamp(value / range, -1.0f, 1.0f);
        return (short) Math.round(normalized * 32767.0f);
    }

    private static float maxFlowSpeedMetersPerSecond(float[] state) {
        float maxSpeed = 0.0f;
        for (int base = 0; base + 2 < state.length; base += FLOW_CHANNELS) {
            float vx = state[base] * NATIVE_VELOCITY_SCALE;
            float vy = state[base + 1] * NATIVE_VELOCITY_SCALE;
            float vz = state[base + 2] * NATIVE_VELOCITY_SCALE;
            float speed = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (Float.isFinite(speed) && speed > maxSpeed) {
                maxSpeed = speed;
            }
        }
        return maxSpeed;
    }

    private BlockPos brickOrigin(BlockPos pos) {
        return new BlockPos(
            Math.floorDiv(pos.getX(), BRICK_SIZE) * BRICK_SIZE,
            Math.floorDiv(pos.getY(), BRICK_SIZE) * BRICK_SIZE,
            Math.floorDiv(pos.getZ(), BRICK_SIZE) * BRICK_SIZE
        );
    }

    private BlockPos brickOrigin(int brickX, int brickY, int brickZ) {
        return new BlockPos(
            brickX * BRICK_SIZE,
            brickY * BRICK_SIZE,
            brickZ * BRICK_SIZE
        );
    }

    private int cellIndex(int x, int y, int z) {
        return (x * BRICK_SIZE + y) * BRICK_SIZE + z;
    }

    private long levelKey(ResourceLocation dimensionId) {
        long value = dimensionId.hashCode();
        return value == 0L ? 1L : value;
    }

    private record CoarseSeedStats(float maxCoarseSpeedMetersPerSecond) {
    }

    private record StaticBrickCacheKey(ResourceLocation dimensionId, int brickX, int brickY, int brickZ) {
    }

    private record StaticCellSample(boolean solid, byte surfaceKind, short openFaceMask, float emitterPowerWatts) {
    }

    private record StaticBrickSnapshot(byte[] obstacle, byte[] surfaceKind, short[] openFaceMask, float[] emitterPower) {
        static StaticBrickSnapshot copyFrom(
            byte[] obstacle,
            byte[] surfaceKind,
            short[] openFaceMask,
            float[] emitterPower
        ) {
            return new StaticBrickSnapshot(
                java.util.Arrays.copyOf(obstacle, obstacle.length),
                java.util.Arrays.copyOf(surfaceKind, surfaceKind.length),
                java.util.Arrays.copyOf(openFaceMask, openFaceMask.length),
                java.util.Arrays.copyOf(emitterPower, emitterPower.length)
            );
        }

        void copyInto(
            byte[] outObstacle,
            byte[] outSurfaceKind,
            short[] outOpenFaceMask,
            float[] outEmitterPower
        ) {
            System.arraycopy(obstacle, 0, outObstacle, 0, Math.min(obstacle.length, outObstacle.length));
            System.arraycopy(surfaceKind, 0, outSurfaceKind, 0, Math.min(surfaceKind.length, outSurfaceKind.length));
            System.arraycopy(openFaceMask, 0, outOpenFaceMask, 0, Math.min(openFaceMask.length, outOpenFaceMask.length));
            System.arraycopy(emitterPower, 0, outEmitterPower, 0, Math.min(emitterPower.length, outEmitterPower.length));
        }
    }

    private enum BrickPreparationResult {
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }

    private enum BoundaryReferenceBuildResult {
        IN_PROGRESS,
        COMPLETED,
        WAITING_FOR_COARSE
    }

    private interface WorkerCommand {
    }

    private record ActiveHintsCommand(long levelKey, int[] activeHintCoords) implements WorkerCommand {
    }

    private record LevelDeltasCommand(long levelKey, NativeSimulationBridge.LevelDelta[] deltas) implements WorkerCommand {
    }

    private record BrickSeedCommand(
        long levelKey,
        int brickX,
        int brickY,
        int brickZ,
        byte[] obstacle,
        byte[] surfaceKind,
        short[] openFaceMask,
        float[] emitterPower,
        byte[] faceSkyExposure,
        byte[] faceDirectExposure,
        float[] flowState,
        float[] airTemperature,
        float[] surfaceTemperature
    ) implements WorkerCommand {
    }

    private record BoundaryReferenceCommand(
        long levelKey,
        int brickX,
        int brickY,
        int brickZ,
        float[] flowState,
        float[] airTemperature,
        float[] surfaceTemperature,
        float maxCoarseSpeedMetersPerSecond
    ) implements WorkerCommand {
    }

    private record StepCommand(long levelKey, PublishTarget[] publishTargets, int stepCount) implements WorkerCommand {
    }

    private record ResetCommand() implements WorkerCommand {
    }

    private record CloseCommand() implements WorkerCommand {
    }

    private record PublishTarget(ResourceLocation dimensionId, BlockPos origin, int brickX, int brickY, int brickZ) {
    }

    private record LocalAtlasSnapshot(ResourceLocation dimensionId, BlockPos origin, int sampleStride, short[] packedFlow) {
    }

    private final class ClientL2Worker {
        private final NativeSimulationBridge bridge = new NativeSimulationBridge();
        private final BlockingQueue<WorkerCommand> commands = new ArrayBlockingQueue<>(WORKER_QUEUE_CAPACITY);
        private final ConcurrentLinkedQueue<LocalAtlasSnapshot> atlases = new ConcurrentLinkedQueue<>();
        private final float[] workerFlowState = new float[CELL_COUNT * FLOW_CHANNELS];
        private final float[] workerAirTemperature = new float[CELL_COUNT];
        private final float[] workerSurfaceTemperature = new float[CELL_COUNT];
        private volatile boolean running;
        private volatile String lastError = "-";
        private volatile String lastRuntimeInfo = "-";
        private volatile NativeSimulationBridge.BrickLevelRuntimeStatus lastNativeStatus;
        private volatile long processedCommands;
        private volatile long droppedCommands;
        private volatile long publishedAtlases;
        private volatile long lastStepNanos;
        private volatile long lastPublishNanos;
        private long serviceKey;
        private Thread thread;

        boolean isNativeLoaded() {
            return bridge.isLoaded();
        }

        String loadError() {
            return bridge.getLoadError();
        }

        void submitActiveHints(long levelKey, int[] activeHintCoords) {
            offer(new ActiveHintsCommand(levelKey, java.util.Arrays.copyOf(activeHintCoords, activeHintCoords.length)));
        }

        void submitLevelDeltas(long levelKey, NativeSimulationBridge.LevelDelta[] deltas) {
            offer(new LevelDeltasCommand(levelKey, java.util.Arrays.copyOf(deltas, deltas.length)));
        }

        void submitBrickSeed(BrickSeedCommand command) {
            offer(command);
        }

        void submitBoundaryReference(BoundaryReferenceCommand command) {
            offer(command);
        }

        void requestStep(long levelKey, PublishTarget[] publishTargets, int stepCount) {
            offer(new StepCommand(levelKey, publishTargets, stepCount));
        }

        LocalAtlasSnapshot pollAtlas() {
            return atlases.poll();
        }

        void reset() {
            commands.clear();
            atlases.clear();
            if (running) {
                offer(new ResetCommand());
            }
        }

        void close() {
            commands.clear();
            atlases.clear();
            if (!running) {
                releaseService();
                return;
            }
            offer(new CloseCommand());
        }

        String status() {
            return "running=" + running
                + ",queue=" + commands.size()
                + ",atlases=" + atlases.size()
                + ",processed=" + processedCommands
                + ",dropped=" + droppedCommands
                + ",published=" + publishedAtlases
                + ",lastStepMs=" + formatMillis(lastStepNanos)
                + ",lastPublishMs=" + formatMillis(lastPublishNanos)
                + ",native=" + formatNativeStatus(lastNativeStatus)
                + ",runtime=" + lastRuntimeInfo
                + ",error=" + lastError;
        }

        private void offer(WorkerCommand command) {
            if (!bridge.isLoaded()) {
                lastError = bridge.getLoadError();
                return;
            }
            startIfNeeded();
            if (!commands.offer(command)) {
                commands.poll();
                droppedCommands++;
                commands.offer(command);
            }
        }

        private void startIfNeeded() {
            if (running) {
                return;
            }
            running = true;
            thread = new Thread(this::runLoop, "a4mc-client-l2-worker");
            thread.setDaemon(true);
            thread.start();
        }

        private void runLoop() {
            try {
                while (running) {
                    WorkerCommand command = commands.take();
                    processedCommands++;
                    if (command instanceof CloseCommand) {
                        running = false;
                        break;
                    }
                    if (command instanceof ResetCommand) {
                        handleReset();
                    } else if (command instanceof ActiveHintsCommand activeHints) {
                        handleActiveHints(activeHints);
                    } else if (command instanceof LevelDeltasCommand LevelDeltas) {
                        handleLevelDeltas(LevelDeltas);
                    } else if (command instanceof BrickSeedCommand brickSeed) {
                        handleBrickSeed(brickSeed);
                    } else if (command instanceof BoundaryReferenceCommand boundaryReference) {
                        handleBoundaryReference(boundaryReference);
                    } else if (command instanceof StepCommand step) {
                        handleStep(step);
                    }
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (Throwable t) {
                lastError = t.getClass().getSimpleName() + ": " + t.getMessage();
                LOGGER.warn("Client L2 worker stopped after unexpected error", t);
            } finally {
                releaseService();
                running = false;
            }
        }

        private void handleReset() {
            releaseService();
            atlases.clear();
            lastNativeStatus = null;
            lastRuntimeInfo = "-";
            lastError = "-";
        }

        private boolean ensureRuntime(long levelKey) {
            if (serviceKey == 0L) {
                serviceKey = bridge.createService();
            }
            if (serviceKey == 0L) {
                lastError = "failed to create native service";
                return false;
            }
            if (!bridge.ensureBrickLevelRuntime(serviceKey, levelKey, BRICK_SIZE, DX_METERS, DT_SECONDS)) {
                lastError = "ensureBrickLevelRuntime failed: " + bridge.lastError();
                return false;
            }
            return true;
        }

        private void handleActiveHints(ActiveHintsCommand command) {
            if (!ensureRuntime(command.levelKey())) {
                return;
            }
            if (!bridge.setBrickLevelExactActiveHints(serviceKey, command.levelKey(), BRICK_SIZE, command.activeHintCoords())) {
                lastError = "setBrickLevelExactActiveHints failed: " + bridge.lastError();
                return;
            }
            updateNativeStatus(command.levelKey());
        }

        private void handleLevelDeltas(LevelDeltasCommand command) {
            if (!ensureRuntime(command.levelKey())) {
                return;
            }
            if (!bridge.submitLevelDeltas(serviceKey, command.deltas())) {
                lastError = "submitLevelDeltas failed: " + bridge.lastError();
                return;
            }
            updateNativeStatus(command.levelKey());
        }

        private void handleBrickSeed(BrickSeedCommand command) {
            if (!ensureRuntime(command.levelKey())) {
                return;
            }
            if (!bridge.uploadBrickLevelStaticBrick(
                serviceKey,
                command.levelKey(),
                BRICK_SIZE,
                command.brickX(),
                command.brickY(),
                command.brickZ(),
                command.obstacle(),
                command.surfaceKind(),
                command.openFaceMask(),
                command.emitterPower(),
                command.faceSkyExposure(),
                command.faceDirectExposure()
            )) {
                lastError = "uploadBrickLevelStaticBrick failed: " + bridge.lastError();
                return;
            }
            if (!bridge.uploadBrickLevelDynamicBrick(
                serviceKey,
                command.levelKey(),
                BRICK_SIZE,
                command.brickX(),
                command.brickY(),
                command.brickZ(),
                command.flowState(),
                command.airTemperature(),
                command.surfaceTemperature()
            )) {
                lastError = "uploadBrickLevelDynamicBrick failed: " + bridge.lastError();
                return;
            }
            if (!bridge.uploadBrickLevelBoundaryReferenceBrick(
                serviceKey,
                command.levelKey(),
                BRICK_SIZE,
                command.brickX(),
                command.brickY(),
                command.brickZ(),
                command.flowState(),
                command.airTemperature(),
                command.surfaceTemperature()
            )) {
                lastError = "uploadBrickLevelBoundaryReferenceBrick failed: " + bridge.lastError();
                return;
            }
            updateNativeStatus(command.levelKey());
        }

        private void handleBoundaryReference(BoundaryReferenceCommand command) {
            if (!ensureRuntime(command.levelKey())) {
                return;
            }
            if (!bridge.uploadBrickLevelBoundaryReferenceBrick(
                serviceKey,
                command.levelKey(),
                BRICK_SIZE,
                command.brickX(),
                command.brickY(),
                command.brickZ(),
                command.flowState(),
                command.airTemperature(),
                command.surfaceTemperature()
            )) {
                lastError = "boundary reference refresh failed: " + bridge.lastError();
                return;
            }
            if (command.maxCoarseSpeedMetersPerSecond() >= COARSE_RESEED_MIN_SPEED_MPS
                && shouldReseedZeroDynamicBrick(command)) {
                bridge.uploadBrickLevelDynamicBrick(
                    serviceKey,
                    command.levelKey(),
                    BRICK_SIZE,
                    command.brickX(),
                    command.brickY(),
                    command.brickZ(),
                    command.flowState(),
                    command.airTemperature(),
                    command.surfaceTemperature()
                );
            }
            updateNativeStatus(command.levelKey());
        }

        private boolean shouldReseedZeroDynamicBrick(BoundaryReferenceCommand command) {
            if (!bridge.copyBrickLevelDynamicBrick(
                serviceKey,
                command.levelKey(),
                BRICK_SIZE,
                command.brickX(),
                command.brickY(),
                command.brickZ(),
                workerFlowState,
                workerAirTemperature,
                workerSurfaceTemperature
            )) {
                return true;
            }
            return maxFlowSpeedMetersPerSecond(workerFlowState) < ZERO_DYNAMIC_MAX_SPEED_EPS_MPS;
        }

        private void handleStep(StepCommand command) {
            if (!ensureRuntime(command.levelKey())) {
                return;
            }
            long start = System.nanoTime();
            if (!bridge.stepBrickLevelRuntime(serviceKey, command.levelKey(), Math.max(1, command.stepCount()))) {
                lastError = "stepBrickLevelRuntime failed: " + bridge.lastError();
                return;
            }
            lastStepNanos = System.nanoTime() - start;
            if (command.publishTargets().length > 0) {
                publishTargets(command.levelKey(), command.publishTargets());
            }
            updateNativeStatus(command.levelKey());
        }

        private void publishTargets(long levelKey, PublishTarget[] targets) {
            long start = System.nanoTime();
            for (PublishTarget target : targets) {
                int sampleStride = LOCAL_PUBLISH_SAMPLE_STRIDE;
                short[] packedFlow = new short[packedValueCount(BRICK_SIZE, sampleStride)];
                if (!bridge.copyBrickLevelPackedFlowAtlas(
                    serviceKey,
                    levelKey,
                    BRICK_SIZE,
                    target.brickX(),
                    target.brickY(),
                    target.brickZ(),
                    sampleStride,
                    packedFlow
                )) {
                    if (!bridge.copyBrickLevelDynamicBrick(
                        serviceKey,
                        levelKey,
                        BRICK_SIZE,
                        target.brickX(),
                        target.brickY(),
                        target.brickZ(),
                        workerFlowState,
                        workerAirTemperature,
                        workerSurfaceTemperature
                    )) {
                        lastError = "copyBrickLevelDynamicBrick failed: " + bridge.lastError();
                        continue;
                    }
                    packFlowFromWorkerState(sampleStride, packedFlow);
                }
                atlases.offer(new LocalAtlasSnapshot(target.dimensionId(), target.origin(), sampleStride, packedFlow));
                publishedAtlases++;
            }
            lastPublishNanos = System.nanoTime() - start;
        }

        private int packedValueCount(int brickSize, int sampleStride) {
            int atlasResolution = (brickSize + sampleStride - 1) / sampleStride;
            return atlasResolution * atlasResolution * atlasResolution * PACKED_CHANNELS;
        }

        private void packFlowFromWorkerState(int sampleStride, short[] packedFlow) {
            int atlasResolution = (BRICK_SIZE + sampleStride - 1) / sampleStride;
            int dstBase = 0;
            for (int x = 0; x < atlasResolution; x++) {
                int gx = Math.min(BRICK_SIZE - 1, x * sampleStride);
                for (int y = 0; y < atlasResolution; y++) {
                    int gy = Math.min(BRICK_SIZE - 1, y * sampleStride);
                    for (int z = 0; z < atlasResolution; z++) {
                        int gz = Math.min(BRICK_SIZE - 1, z * sampleStride);
                        int srcBase = cellIndex(gx, gy, gz) * FLOW_CHANNELS;
                        packedFlow[dstBase] = quantizeSignedToShort(
                            workerFlowState[srcBase] * NATIVE_VELOCITY_SCALE,
                            ATLAS_VELOCITY_RANGE
                        );
                        packedFlow[dstBase + 1] = quantizeSignedToShort(
                            workerFlowState[srcBase + 1] * NATIVE_VELOCITY_SCALE,
                            ATLAS_VELOCITY_RANGE
                        );
                        packedFlow[dstBase + 2] = quantizeSignedToShort(
                            workerFlowState[srcBase + 2] * NATIVE_VELOCITY_SCALE,
                            ATLAS_VELOCITY_RANGE
                        );
                        packedFlow[dstBase + 3] = quantizeSignedToShort(workerFlowState[srcBase + 3], ATLAS_PRESSURE_RANGE);
                        dstBase += PACKED_CHANNELS;
                    }
                }
            }
        }

        private void updateNativeStatus(long levelKey) {
            if (serviceKey == 0L) {
                lastNativeStatus = null;
                lastRuntimeInfo = "-";
                return;
            }
            lastNativeStatus = bridge.getBrickLevelRuntimeStatus(serviceKey, levelKey);
            lastRuntimeInfo = bridge.runtimeInfo();
        }

        private void releaseService() {
            if (serviceKey != 0L) {
                bridge.releaseService(serviceKey);
                serviceKey = 0L;
            }
        }
    }

    private void resetActiveBrick() {
        activeOrigin = null;
        activeDimension = null;
        activeHintUploaded = false;
        activeBrickCount = 0;
        prepareCursor = 0;
        refreshCursor = 0;
        publishCursor = 0;
        java.util.Arrays.fill(activeBrickReady, false);
        java.util.Arrays.fill(activeBrickRefreshPending, false);
        java.util.Arrays.fill(activeBrickBoundaryRefreshPending, false);
        cancelStagedPreparation();
        cancelBoundaryReferenceRefresh();
        lastServerTick = Long.MIN_VALUE;
        lastProcessedClientGameTime = Long.MIN_VALUE;
        lastSolveClientGameTime = Long.MIN_VALUE;
        lastPublishedClientGameTime = Long.MIN_VALUE;
        lastBoundaryRefreshClientGameTime = Long.MIN_VALUE;
        visualizer.clearLocalFlowFields();
    }

    private void close() {
        resetActiveBrick();
        clientSolveDisabled = false;
        staticBrickCache.clear();
        worker.close();
        fastSuspendUntilGameTime = Long.MIN_VALUE;
    }

    String status() {
        if (!experimentalEnabled) {
            return "client L2 localSolve=off";
        }
        return "client L2 localSolve=on streaming=" + streamingEnabled
            + " disabled=" + clientSolveDisabled
            + " brickSize=" + BRICK_SIZE
            + " cells=" + CELL_COUNT
            + " activeBricks=" + activeBrickCount
            + " worker=" + worker.status()
            + " staticCache=" + staticBrickCache.size()
            + "/" + STATIC_CACHE_MAX_BRICKS
            + " staticPatches=" + lastStaticPatchCount
            + " fanPatchCells=" + lastFanPatchCellCount
            + " heatPatchCells=" + lastHeatPatchCellCount
            + " solveInterval=" + SOLVE_INTERVAL_TICKS
            + " publishInterval=" + LOCAL_PUBLISH_INTERVAL_TICKS
            + " publishStride=" + LOCAL_PUBLISH_SAMPLE_STRIDE
            + " maxActive=" + MAX_CLIENT_ACTIVE_BRICKS
            + " prepBudget=" + STATIC_BUILD_CELLS_PER_TICK
            + " seedBudget=" + COARSE_SEED_CELLS_PER_TICK
            + " boundaryBudget=" + BOUNDARY_REFERENCE_CELLS_PER_TICK
            + " prep=" + stagedPreparationStatus()
            + " boundaryPrep=" + boundaryReferenceRefreshStatus()
            + " fastSuspendUntil=" + fastSuspendUntilGameTime
            + " lastServerTick=" + lastServerTick;
    }

    private String formatNativeStatus(NativeSimulationBridge.BrickLevelRuntimeStatus status) {
        if (status == null) {
            return "none";
        }
        return "known=" + status.knownBrickCount()
            + ",hints=" + status.activeHintCount()
            + ",active=" + status.activeBrickCount()
            + ",dirty=" + status.geometryDirtyCount()
            + ",forcingDirty=" + status.forcingDirtyCount()
            + ",reinit=" + status.pendingReinitCount()
            + ",epoch=" + status.epoch();
    }

    private static String formatMillis(long nanos) {
        return String.format("%.3f", nanos / 1_000_000.0);
    }

    private String stagedPreparationStatus() {
        if (stagedActiveIndex < 0) {
            return "idle";
        }
        return stagedBrickX + "," + stagedBrickY + "," + stagedBrickZ
            + ":static=" + stagedStaticCursor + "/" + CELL_COUNT
            + ":seed=" + stagedSeedCursor + "/" + CELL_COUNT
            + ":staticUploaded=" + stagedStaticUploaded
            + ":dynamicUploaded=" + stagedDynamicUploaded;
    }

    private String boundaryReferenceRefreshStatus() {
        if (boundaryRefreshActiveIndex < 0) {
            return "idle";
        }
        return boundaryRefreshBrickX + "," + boundaryRefreshBrickY + "," + boundaryRefreshBrickZ
            + ":cells=" + boundaryRefreshCursor + "/" + CELL_COUNT
            + ":maxCoarse=" + String.format("%.3f", boundaryRefreshMaxCoarseSpeed);
    }

    void setExperimentalEnabled(boolean enabled) {
        if (experimentalEnabled == enabled) {
            return;
        }
        experimentalEnabled = enabled;
        clientSolveDisabled = false;
        fastSuspendUntilGameTime = Long.MIN_VALUE;
        resetActiveBrick();
        if (!enabled) {
            worker.reset();
        }
        LOGGER.info("Client L2 local solve {}", enabled ? "enabled" : "disabled");
    }

    boolean isExperimentalEnabled() {
        return experimentalEnabled;
    }

    private void disableClientSolve(Minecraft client, String reason) {
        clientSolveDisabled = true;
        resetActiveBrick();
        maybeLog(client, "disabled client L2: " + reason);
    }

    private void maybeLog(Minecraft client, String message) {
        if (client.level == null) {
            return;
        }
        long now = client.level.getGameTime();
        if (lastDiagnosticGameTime == Long.MIN_VALUE || now - lastDiagnosticGameTime >= 100) {
            LOGGER.info("Client L2 idle: {}", message);
            lastDiagnosticGameTime = now;
        }
    }
}
