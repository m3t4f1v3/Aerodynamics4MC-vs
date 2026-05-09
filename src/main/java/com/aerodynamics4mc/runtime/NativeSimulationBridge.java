package com.aerodynamics4mc.runtime;

import java.nio.ByteBuffer;

public final class NativeSimulationBridge {
    public static final int FLOW_STATE_CHANNELS = 4;
    public static final int PACKED_ATLAS_CHANNELS = 4;
    public static final int PLAYER_PROBE_CHANNELS = 6;
    public static final int TORNADO_DESCRIPTOR_FLOATS = 17;
    public static final int NESTED_FEEDBACK_MAX_BINS = 8;
    public static final int NESTED_FEEDBACK_LAYOUT_INTS_PER_BIN = 9;
    public static final int NESTED_FEEDBACK_VALUES_PER_BIN = 10;
    public static final int NESTED_FEEDBACK_STATUS_FIELDS = 6;
    public static final int BRICK_HINT_COORDS_PER_BRICK = 3;
    public static final int BRICK_RUNTIME_STATUS_FIELDS = 8;
    private static final int FACE_COUNT = 6;
    public static final int Level_DELTA_BLOCK_CHANGED = 1;
    public static final int Level_DELTA_CHUNK_LOADED = 2;
    public static final int Level_DELTA_CHUNK_UNLOADED = 3;
    public static final int Level_DELTA_BLOCK_ENTITY_LOADED = 4;
    public static final int Level_DELTA_BLOCK_ENTITY_UNLOADED = 5;
    public static final int Level_DELTA_Level_UNLOADED = 6;
    public static final int Level_DELTA_FOCUS_CHANGED = 7;
    public static final int Level_DELTA_BRICK_STATIC_CELL_PATCH = 8;
    private static final int Level_DELTA_INTS_PER_ENTRY = 8;
    private static final int Level_DELTA_FLOATS_PER_ENTRY = 4;

    private static final String LIB_NAME = "aero_lbm";
    private static final boolean LOADED;
    private static final String LOAD_ERROR;
    private static volatile boolean nestedFeedbackStatusSupported = true;
    private static volatile boolean exactActiveHintsSupported = true;
    private static volatile boolean packedBrickAtlasSupported = true;

    static {
        boolean loaded = false;
        String error = "";
        try {
            NativeLibraryLoader.loadBundled(LIB_NAME);
            loaded = true;
        } catch (Throwable t) {
            error = t.getClass().getSimpleName() + ": " + t.getMessage();
        }
        LOADED = loaded;
        LOAD_ERROR = error;
    }

    public boolean isLoaded() {
        return LOADED;
    }

    public String getLoadError() {
        return LOAD_ERROR;
    }

    public long createService() {
        if (!LOADED) {
            return 0L;
        }
        return nativeCreateService();
    }

    public void releaseService(long serviceKey) {
        if (!LOADED || serviceKey == 0L) {
            return;
        }
        nativeReleaseService(serviceKey);
    }

    public boolean setFocus(long serviceKey, int blockX, int blockY, int blockZ, int radiusBlocks) {
        return LOADED && serviceKey != 0L
            && nativeSetFocus(serviceKey, blockX, blockY, blockZ, radiusBlocks);
    }

    public boolean submitLevelDeltas(long serviceKey, LevelDelta[] deltas) {
        if (!LOADED || serviceKey == 0L || deltas == null) {
            return false;
        }
        if (deltas.length == 0) {
            return nativeSubmitLevelDeltas(serviceKey, new int[0], new float[0]);
        }
        int[] ints = new int[deltas.length * Level_DELTA_INTS_PER_ENTRY];
        float[] floats = new float[deltas.length * Level_DELTA_FLOATS_PER_ENTRY];
        for (int i = 0; i < deltas.length; i++) {
            LevelDelta delta = deltas[i];
            int intBase = i * Level_DELTA_INTS_PER_ENTRY;
            ints[intBase] = delta.type();
            ints[intBase + 1] = delta.x();
            ints[intBase + 2] = delta.y();
            ints[intBase + 3] = delta.z();
            ints[intBase + 4] = delta.data0();
            ints[intBase + 5] = delta.data1();
            ints[intBase + 6] = delta.data2();
            ints[intBase + 7] = delta.data3();

            int floatBase = i * Level_DELTA_FLOATS_PER_ENTRY;
            floats[floatBase] = delta.value0();
            floats[floatBase + 1] = delta.value1();
            floats[floatBase + 2] = delta.value2();
            floats[floatBase + 3] = delta.value3();
        }
        return nativeSubmitLevelDeltas(serviceKey, ints, floats);
    }

    public boolean submitLevelDelta(long serviceKey, LevelDelta delta) {
        return submitLevelDeltas(serviceKey, new LevelDelta[] {delta});
    }

    public boolean ensureBrickLevelRuntime(
        long serviceKey,
        long levelKey,
        int brickSize,
        float dxMeters,
        float dtSeconds
    ) {
        return LOADED && serviceKey != 0L && levelKey != 0L
            && brickSize > 0
            && Float.isFinite(dxMeters) && dxMeters > 0.0f
            && Float.isFinite(dtSeconds) && dtSeconds > 0.0f
            && nativeEnsureBrickLevelRuntime(serviceKey, levelKey, brickSize, dxMeters, dtSeconds);
    }

    public boolean setBrickLevelActiveHints(
        long serviceKey,
        long levelKey,
        int brickSize,
        int[] brickCoords
    ) {
        if (!LOADED || serviceKey == 0L || levelKey == 0L || brickSize <= 0 || brickCoords == null) {
            return false;
        }
        if ((brickCoords.length % BRICK_HINT_COORDS_PER_BRICK) != 0) {
            return false;
        }
        return nativeSetBrickLevelActiveHints(
            serviceKey,
            levelKey,
            brickSize,
            brickCoords,
            brickCoords.length / BRICK_HINT_COORDS_PER_BRICK
        );
    }

    public boolean setBrickLevelExactActiveHints(
        long serviceKey,
        long levelKey,
        int brickSize,
        int[] brickCoords
    ) {
        if (!LOADED || serviceKey == 0L || levelKey == 0L || brickSize <= 0 || brickCoords == null) {
            return false;
        }
        if ((brickCoords.length % BRICK_HINT_COORDS_PER_BRICK) != 0) {
            return false;
        }
        int brickCount = brickCoords.length / BRICK_HINT_COORDS_PER_BRICK;
        if (!exactActiveHintsSupported) {
            return false;
        }
        try {
            return nativeSetBrickLevelExactActiveHints(serviceKey, levelKey, brickSize, brickCoords, brickCount);
        } catch (UnsatisfiedLinkError error) {
            exactActiveHintsSupported = false;
            return false;
        }
    }

    public BrickLevelRuntimeStatus getBrickLevelRuntimeStatus(long serviceKey, long levelKey) {
        if (!LOADED || serviceKey == 0L || levelKey == 0L) {
            return null;
        }
        int[] status = new int[BRICK_RUNTIME_STATUS_FIELDS];
        if (!nativeGetBrickLevelRuntimeStatus(serviceKey, levelKey, status)) {
            return null;
        }
        return new BrickLevelRuntimeStatus(
            status[0],
            status[1],
            status[2],
            status[3],
            status[4],
            status[5],
            status[6],
            status[7]
        );
    }

    public boolean stepBrickLevelRuntime(long serviceKey, long levelKey, int stepCount) {
        return LOADED && serviceKey != 0L && levelKey != 0L
            && stepCount > 0
            && nativeStepBrickLevelRuntime(serviceKey, levelKey, stepCount);
    }

    public int[] getBrickLevelResidentBrickCoords(long serviceKey, long levelKey) {
        if (!LOADED || serviceKey == 0L || levelKey == 0L) {
            return null;
        }
        return nativeGetBrickLevelResidentBrickCoords(serviceKey, levelKey);
    }

    public int[] getBrickLevelActiveBrickCoords(long serviceKey, long levelKey) {
        if (!LOADED || serviceKey == 0L || levelKey == 0L) {
            return null;
        }
        return nativeGetBrickLevelActiveBrickCoords(serviceKey, levelKey);
    }

    public boolean uploadBrickLevelStaticBrick(
        long serviceKey,
        long levelKey,
        int brickSize,
        int brickX,
        int brickY,
        int brickZ,
        byte[] obstacle,
        byte[] surfaceKind,
        short[] openFaceMask,
        float[] emitterPowerWatts,
        byte[] faceSkyExposure,
        byte[] faceDirectExposure
    ) {
        if (!LOADED || serviceKey == 0L || levelKey == 0L || brickSize <= 0) {
            return false;
        }
        int cells = checkedCellCount(brickSize, brickSize, brickSize);
        if (cells <= 0
            || obstacle == null
            || surfaceKind == null
            || openFaceMask == null
            || emitterPowerWatts == null
            || faceSkyExposure == null
            || faceDirectExposure == null
            || obstacle.length != cells
            || surfaceKind.length != cells
            || openFaceMask.length != cells
            || emitterPowerWatts.length != cells
            || faceSkyExposure.length != cells * FACE_COUNT
            || faceDirectExposure.length != cells * FACE_COUNT) {
            return false;
        }
        return nativeUploadBrickLevelStaticBrick(
            serviceKey,
            levelKey,
            brickSize,
            brickX,
            brickY,
            brickZ,
            obstacle,
            surfaceKind,
            openFaceMask,
            emitterPowerWatts,
            faceSkyExposure,
            faceDirectExposure
        );
    }

    public boolean queueBrickLevelStaticBrickUpload(
        long serviceKey,
        long levelKey,
        int brickSize,
        int brickX,
        int brickY,
        int brickZ,
        byte[] obstacle,
        byte[] surfaceKind,
        short[] openFaceMask,
        float[] emitterPowerWatts,
        byte[] faceSkyExposure,
        byte[] faceDirectExposure
    ) {
        if (!LOADED || serviceKey == 0L || levelKey == 0L || brickSize <= 0) {
            return false;
        }
        int cells = checkedCellCount(brickSize, brickSize, brickSize);
        if (cells <= 0
            || obstacle == null
            || surfaceKind == null
            || openFaceMask == null
            || emitterPowerWatts == null
            || faceSkyExposure == null
            || faceDirectExposure == null
            || obstacle.length != cells
            || surfaceKind.length != cells
            || openFaceMask.length != cells
            || emitterPowerWatts.length != cells
            || faceSkyExposure.length != cells * FACE_COUNT
            || faceDirectExposure.length != cells * FACE_COUNT) {
            return false;
        }
        return nativeQueueBrickLevelStaticBrickUpload(
            serviceKey,
            levelKey,
            brickSize,
            brickX,
            brickY,
            brickZ,
            obstacle,
            surfaceKind,
            openFaceMask,
            emitterPowerWatts,
            faceSkyExposure,
            faceDirectExposure
        );
    }

    public boolean syncRegionCoreToBrickLevel(
        long serviceKey,
        long regionKey,
        long levelKey,
        int regionNx,
        int regionNy,
        int regionNz,
        int coreOffsetX,
        int coreOffsetY,
        int coreOffsetZ,
        int coreNx,
        int coreNy,
        int coreNz,
        int brickX,
        int brickY,
        int brickZ
    ) {
        return LOADED
            && serviceKey != 0L
            && regionKey != 0L
            && levelKey != 0L
            && nativeSyncRegionCoreToBrickLevel(
                serviceKey,
                regionKey,
                levelKey,
                regionNx,
                regionNy,
                regionNz,
                coreOffsetX,
                coreOffsetY,
                coreOffsetZ,
                coreNx,
                coreNy,
                coreNz,
                brickX,
                brickY,
                brickZ
            );
    }

    public boolean copyBrickLevelDynamicBrick(
        long serviceKey,
        long levelKey,
        int brickSize,
        int brickX,
        int brickY,
        int brickZ,
        float[] outFlowState,
        float[] outAirTemperature,
        float[] outSurfaceTemperature
    ) {
        if (!LOADED || serviceKey == 0L || levelKey == 0L || brickSize <= 0) {
            return false;
        }
        int cells = checkedCellCount(brickSize, brickSize, brickSize);
        if (cells <= 0
            || outFlowState == null
            || outAirTemperature == null
            || outSurfaceTemperature == null
            || outFlowState.length != cells * FLOW_STATE_CHANNELS
            || outAirTemperature.length != cells
            || outSurfaceTemperature.length != cells) {
            return false;
        }
        return nativeCopyBrickLevelDynamicBrick(
            serviceKey,
            levelKey,
            brickSize,
            brickX,
            brickY,
            brickZ,
            outFlowState,
            outAirTemperature,
            outSurfaceTemperature
        );
    }

    public boolean copyBrickLevelPackedFlowAtlas(
        long serviceKey,
        long levelKey,
        int brickSize,
        int brickX,
        int brickY,
        int brickZ,
        int sampleStride,
        short[] outPackedFlow
    ) {
        if (!LOADED || !packedBrickAtlasSupported || serviceKey == 0L || levelKey == 0L || brickSize <= 0) {
            return false;
        }
        if (sampleStride <= 0 || outPackedFlow == null) {
            return false;
        }
        int atlasResolution = (brickSize + sampleStride - 1) / sampleStride;
        int values = checkedCellCount(atlasResolution, atlasResolution, atlasResolution);
        if (values <= 0 || outPackedFlow.length != values * PACKED_ATLAS_CHANNELS) {
            return false;
        }
        try {
            return nativeCopyBrickLevelPackedFlowAtlas(
                serviceKey,
                levelKey,
                brickSize,
                brickX,
                brickY,
                brickZ,
                sampleStride,
                outPackedFlow
            );
        } catch (UnsatisfiedLinkError error) {
            packedBrickAtlasSupported = false;
            return false;
        }
    }

    public boolean uploadBrickLevelDynamicBrick(
        long serviceKey,
        long levelKey,
        int brickSize,
        int brickX,
        int brickY,
        int brickZ,
        float[] flowState,
        float[] airTemperature,
        float[] surfaceTemperature
    ) {
        if (!LOADED || serviceKey == 0L || levelKey == 0L || brickSize <= 0) {
            return false;
        }
        int cells = checkedCellCount(brickSize, brickSize, brickSize);
        if (cells <= 0
            || flowState == null
            || airTemperature == null
            || surfaceTemperature == null
            || flowState.length != cells * FLOW_STATE_CHANNELS
            || airTemperature.length != cells
            || surfaceTemperature.length != cells) {
            return false;
        }
        return nativeUploadBrickLevelDynamicBrick(
            serviceKey,
            levelKey,
            brickSize,
            brickX,
            brickY,
            brickZ,
            flowState,
            airTemperature,
            surfaceTemperature
        );
    }

    public boolean uploadBrickLevelBoundaryReferenceBrick(
        long serviceKey,
        long levelKey,
        int brickSize,
        int brickX,
        int brickY,
        int brickZ,
        float[] flowState,
        float[] airTemperature,
        float[] surfaceTemperature
    ) {
        if (!LOADED || serviceKey == 0L || levelKey == 0L || brickSize <= 0) {
            return false;
        }
        int cells = checkedCellCount(brickSize, brickSize, brickSize);
        if (cells <= 0
            || flowState == null
            || airTemperature == null
            || surfaceTemperature == null
            || flowState.length != cells * FLOW_STATE_CHANNELS
            || airTemperature.length != cells
            || surfaceTemperature.length != cells) {
            return false;
        }
        return nativeUploadBrickLevelBoundaryReferenceBrick(
            serviceKey,
            levelKey,
            brickSize,
            brickX,
            brickY,
            brickZ,
            flowState,
            airTemperature,
            surfaceTemperature
        );
    }

    public boolean uploadStaticRegion(
        long serviceKey,
        long regionKey,
        int nx,
        int ny,
        int nz,
        byte[] obstacle,
        byte[] surfaceKind,
        short[] openFaceMask,
        float[] emitterPowerWatts,
        byte[] faceSkyExposure,
        byte[] faceDirectExposure
    ) {
        if (!LOADED || serviceKey == 0L || regionKey == 0L) {
            return false;
        }
        int cells = checkedCellCount(nx, ny, nz);
        if (cells <= 0
            || obstacle == null
            || surfaceKind == null
            || openFaceMask == null
            || emitterPowerWatts == null
            || faceSkyExposure == null
            || faceDirectExposure == null
            || obstacle.length != cells
            || surfaceKind.length != cells
            || openFaceMask.length != cells
            || emitterPowerWatts.length != cells
            || faceSkyExposure.length != cells * 6
            || faceDirectExposure.length != cells * 6) {
            return false;
        }
        return nativeUploadStaticRegion(
            serviceKey,
            regionKey,
            nx,
            ny,
            nz,
            obstacle,
            surfaceKind,
            openFaceMask,
            emitterPowerWatts,
            faceSkyExposure,
            faceDirectExposure
        );
    }

    public boolean uploadStaticRegionPatch(
        long serviceKey,
        long regionKey,
        int nx,
        int ny,
        int nz,
        int offsetX,
        int offsetY,
        int offsetZ,
        int patchNx,
        int patchNy,
        int patchNz,
        byte[] obstacle,
        byte[] surfaceKind,
        short[] openFaceMask,
        float[] emitterPowerWatts,
        byte[] faceSkyExposure,
        byte[] faceDirectExposure
    ) {
        if (!LOADED || serviceKey == 0L || regionKey == 0L) {
            return false;
        }
        int cells = checkedCellCount(patchNx, patchNy, patchNz);
        if (cells <= 0
            || obstacle == null
            || surfaceKind == null
            || openFaceMask == null
            || emitterPowerWatts == null
            || faceSkyExposure == null
            || faceDirectExposure == null
            || obstacle.length != cells
            || surfaceKind.length != cells
            || openFaceMask.length != cells
            || emitterPowerWatts.length != cells
            || faceSkyExposure.length != cells * 6
            || faceDirectExposure.length != cells * 6) {
            return false;
        }
        return nativeUploadStaticRegionPatch(
            serviceKey,
            regionKey,
            nx,
            ny,
            nz,
            offsetX,
            offsetY,
            offsetZ,
            patchNx,
            patchNy,
            patchNz,
            obstacle,
            surfaceKind,
            openFaceMask,
            emitterPowerWatts,
            faceSkyExposure,
            faceDirectExposure
        );
    }

    public boolean activateRegion(long serviceKey, long regionKey, int nx, int ny, int nz) {
        if (!LOADED || serviceKey == 0L || regionKey == 0L) {
            return false;
        }
        return nativeActivateRegion(serviceKey, regionKey, nx, ny, nz);
    }

    public boolean deactivateRegion(long serviceKey, long regionKey) {
        return LOADED && serviceKey != 0L && regionKey != 0L
            && nativeDeactivateRegion(serviceKey, regionKey);
    }

    public boolean hasRegion(long serviceKey, long regionKey) {
        return LOADED && serviceKey != 0L && regionKey != 0L
            && nativeHasRegion(serviceKey, regionKey);
    }

    public boolean isRegionReady(long serviceKey, long regionKey) {
        return LOADED && serviceKey != 0L && regionKey != 0L
            && nativeIsRegionReady(serviceKey, regionKey);
    }

    public boolean ensureL2Runtime(
        long serviceKey,
        int nx,
        int ny,
        int nz,
        int inputChannels,
        int outputChannels
    ) {
        return LOADED && serviceKey != 0L
            && nativeEnsureL2Runtime(serviceKey, nx, ny, nz, inputChannels, outputChannels);
    }

    public boolean hasRegionContext(long serviceKey, long regionKey) {
        return LOADED && serviceKey != 0L && regionKey != 0L
            && nativeHasRegionContext(serviceKey, regionKey);
    }

    public boolean setRegionNestedFeedbackLayout(
        long serviceKey,
        long regionKey,
        int stepsPerFeedback,
        int[] layout
    ) {
        if (!LOADED || serviceKey == 0L || regionKey == 0L || layout == null) {
            return false;
        }
        if (stepsPerFeedback <= 0
            || layout.length == 0
            || (layout.length % NESTED_FEEDBACK_LAYOUT_INTS_PER_BIN) != 0
            || layout.length > NESTED_FEEDBACK_MAX_BINS * NESTED_FEEDBACK_LAYOUT_INTS_PER_BIN) {
            return false;
        }
        return nativeSetRegionNestedFeedbackLayout(serviceKey, regionKey, stepsPerFeedback, layout);
    }

    public boolean uploadRegionForcing(
        long serviceKey,
        long regionKey,
        int nx,
        int ny,
        int nz,
        float[] thermalSource,
        byte[] fanMask,
        float[] fanVx,
        float[] fanVy,
        float[] fanVz
    ) {
        if (!LOADED || serviceKey == 0L || regionKey == 0L) {
            return false;
        }
        int cells = checkedCellCount(nx, ny, nz);
        if (cells <= 0
            || fanMask == null
            || fanVx == null
            || fanVy == null
            || fanVz == null
            || (thermalSource != null && thermalSource.length != cells)
            || fanMask.length != cells
            || fanVx.length != cells
            || fanVy.length != cells
            || fanVz.length != cells) {
            return false;
        }
        return nativeUploadRegionForcing(
            serviceKey,
            regionKey,
            nx,
            ny,
            nz,
            thermalSource,
            fanMask,
            fanVx,
            fanVy,
            fanVz
        );
    }

    public boolean refreshRegionThermal(
        long serviceKey,
        long regionKey,
        int nx,
        int ny,
        int nz,
        float directSolarFluxWm2,
        float diffuseSolarFluxWm2,
        float ambientAirTemperatureKelvin,
        float deepGroundTemperatureKelvin,
        float skyTemperatureKelvin,
        float precipitationTemperatureKelvin,
        float precipitationStrength,
        float sunX,
        float sunY,
        float sunZ,
        float surfaceDeltaSeconds
    ) {
        if (!LOADED || serviceKey == 0L || regionKey == 0L) {
            return false;
        }
        return nativeRefreshRegionThermal(
            serviceKey,
            regionKey,
            nx,
            ny,
            nz,
            directSolarFluxWm2,
            diffuseSolarFluxWm2,
            ambientAirTemperatureKelvin,
            deepGroundTemperatureKelvin,
            skyTemperatureKelvin,
            precipitationTemperatureKelvin,
            precipitationStrength,
            sunX,
            sunY,
            sunZ,
            surfaceDeltaSeconds
        );
    }

    public float[] stepRegion(
        long serviceKey,
        long regionKey,
        ByteBuffer payload,
        int nx,
        int ny,
        int nz
    ) {
        if (!LOADED || serviceKey == 0L || regionKey == 0L || payload == null || !payload.isDirect()) {
            return null;
        }
        int cells = checkedCellCount(nx, ny, nz);
        if (cells <= 0) {
            return null;
        }
        float[] output = new float[cells * FLOW_STATE_CHANNELS];
        return nativeStepRegion(serviceKey, regionKey, payload, nx, ny, nz, output) ? output : null;
    }

    public float stepRegionStored(
        long serviceKey,
        long regionKey,
        int nx,
        int ny,
        int nz,
        float boundaryWindX,
        float boundaryWindY,
        float boundaryWindZ,
        float fallbackBoundaryAirTemperatureKelvin,
        int externalFaceMask,
        int boundaryFaceResolution,
        float[] boundaryWindFaceX,
        float[] boundaryWindFaceY,
        float[] boundaryWindFaceZ,
        float[] boundaryAirTemperatureKelvin,
        int spongeThicknessCells,
        float spongeVelocityRelaxation,
        float spongeTemperatureRelaxation,
        int tornadoDescriptorCount,
        float[] tornadoDescriptors
    ) {
        if (!LOADED || serviceKey == 0L || regionKey == 0L) {
            return Float.NaN;
        }
        int faceCells = boundaryFaceResolution <= 0 ? 0 : FACE_COUNT * boundaryFaceResolution * boundaryFaceResolution;
        if (faceCells > 0
            && (boundaryWindFaceX == null
                || boundaryWindFaceY == null
                || boundaryWindFaceZ == null
                || boundaryAirTemperatureKelvin == null
                || boundaryWindFaceX.length != faceCells
                || boundaryWindFaceY.length != faceCells
                || boundaryWindFaceZ.length != faceCells
                || boundaryAirTemperatureKelvin.length != faceCells)) {
            return Float.NaN;
        }
        if (tornadoDescriptorCount < 0
            || (tornadoDescriptorCount > 0
                && (tornadoDescriptors == null
                    || tornadoDescriptors.length != tornadoDescriptorCount * TORNADO_DESCRIPTOR_FLOATS))) {
            return Float.NaN;
        }
        float[] outMaxSpeed = new float[1];
        return nativeStepRegionStored(
            serviceKey,
            regionKey,
            nx,
            ny,
            nz,
            boundaryWindX,
            boundaryWindY,
            boundaryWindZ,
            fallbackBoundaryAirTemperatureKelvin,
            externalFaceMask,
            boundaryFaceResolution,
            boundaryWindFaceX,
            boundaryWindFaceY,
            boundaryWindFaceZ,
            boundaryAirTemperatureKelvin,
            spongeThicknessCells,
            spongeVelocityRelaxation,
            spongeTemperatureRelaxation,
            tornadoDescriptorCount,
            tornadoDescriptors,
            outMaxSpeed
        ) ? outMaxSpeed[0] : Float.NaN;
    }

    public boolean exchangeRegionHalo(
        long serviceKey,
        long firstRegionKey,
        long secondRegionKey,
        int gridSize,
        int offsetX,
        int offsetY,
        int offsetZ
    ) {
        return LOADED && serviceKey != 0L && firstRegionKey != 0L && secondRegionKey != 0L
            && nativeExchangeRegionHalo(serviceKey, firstRegionKey, secondRegionKey, gridSize, offsetX, offsetY, offsetZ);
    }

    public int exchangeRegionHaloBatch(
        long serviceKey,
        long[] regionPairs,
        int[] offsets,
        int exchangeCount
    ) {
        if (!LOADED || serviceKey == 0L) {
            return -1;
        }
        if (exchangeCount < 0
            || regionPairs == null
            || offsets == null
            || regionPairs.length < exchangeCount * 2
            || offsets.length < exchangeCount * 3) {
            return -1;
        }
        if (exchangeCount == 0) {
            return 0;
        }
        return nativeExchangeRegionHaloBatch(serviceKey, regionPairs, offsets, exchangeCount);
    }

    public float syncRegionState(long serviceKey, long regionKey) {
        if (!LOADED || serviceKey == 0L || regionKey == 0L) {
            return Float.NaN;
        }
        float[] outMaxSpeed = new float[1];
        return nativeSyncRegionState(serviceKey, regionKey, outMaxSpeed) ? outMaxSpeed[0] : Float.NaN;
    }

    public float syncRegionFlowState(long serviceKey, long regionKey) {
        if (!LOADED || serviceKey == 0L || regionKey == 0L) {
            return Float.NaN;
        }
        float[] outMaxSpeed = new float[1];
        return nativeSyncRegionFlowState(serviceKey, regionKey, outMaxSpeed) ? outMaxSpeed[0] : Float.NaN;
    }

    public boolean getRegionTemperatureState(
        long serviceKey,
        long regionKey,
        int nx,
        int ny,
        int nz,
        float[] outTemperature
    ) {
        if (!LOADED || serviceKey == 0L || regionKey == 0L || outTemperature == null) {
            return false;
        }
        int cells = checkedCellCount(nx, ny, nz);
        return cells > 0
            && outTemperature.length == cells
            && nativeGetRegionTemperatureState(serviceKey, regionKey, nx, ny, nz, outTemperature);
    }

    public boolean getRegionFlowState(
        long serviceKey,
        long regionKey,
        int nx,
        int ny,
        int nz,
        float[] outFlowState
    ) {
        if (!LOADED || serviceKey == 0L || regionKey == 0L || outFlowState == null) {
            return false;
        }
        int cells = checkedCellCount(nx, ny, nz);
        return cells > 0
            && outFlowState.length == cells * FLOW_STATE_CHANNELS
            && nativeGetRegionFlowState(serviceKey, regionKey, nx, ny, nz, outFlowState);
    }

    public boolean setRegionTemperatureState(
        long serviceKey,
        long regionKey,
        int nx,
        int ny,
        int nz,
        float[] temperature
    ) {
        if (!LOADED || serviceKey == 0L || regionKey == 0L || temperature == null) {
            return false;
        }
        int cells = checkedCellCount(nx, ny, nz);
        return cells > 0
            && temperature.length == cells
            && nativeSetRegionTemperatureState(serviceKey, regionKey, nx, ny, nz, temperature);
    }

    public boolean releaseRegionRuntime(long serviceKey, long regionKey) {
        return LOADED && serviceKey != 0L && regionKey != 0L
            && nativeReleaseRegionRuntime(serviceKey, regionKey);
    }

    public boolean importDynamicRegion(
        long serviceKey,
        long regionKey,
        int nx,
        int ny,
        int nz,
        float[] flowState,
        float[] airTemperature,
        float[] surfaceTemperature
    ) {
        if (!LOADED || serviceKey == 0L || regionKey == 0L) {
            return false;
        }
        int cells = checkedCellCount(nx, ny, nz);
        if (cells <= 0
            || flowState == null
            || airTemperature == null
            || surfaceTemperature == null
            || flowState.length != cells * FLOW_STATE_CHANNELS
            || airTemperature.length != cells
            || surfaceTemperature.length != cells) {
            return false;
        }
        return nativeImportDynamicRegion(
            serviceKey,
            regionKey,
            nx,
            ny,
            nz,
            flowState,
            airTemperature,
            surfaceTemperature
        );
    }

    public boolean exportDynamicRegion(
        long serviceKey,
        long regionKey,
        int nx,
        int ny,
        int nz,
        float[] outFlowState,
        float[] outAirTemperature,
        float[] outSurfaceTemperature
    ) {
        if (!LOADED || serviceKey == 0L || regionKey == 0L) {
            return false;
        }
        int cells = checkedCellCount(nx, ny, nz);
        if (cells <= 0
            || outFlowState == null
            || outAirTemperature == null
            || outSurfaceTemperature == null
            || outFlowState.length != cells * FLOW_STATE_CHANNELS
            || outAirTemperature.length != cells
            || outSurfaceTemperature.length != cells) {
            return false;
        }
        return nativeExportDynamicRegion(
            serviceKey,
            regionKey,
            nx,
            ny,
            nz,
            outFlowState,
            outAirTemperature,
            outSurfaceTemperature
        );
    }

    public boolean pollRegionNestedFeedback(
        long serviceKey,
        long regionKey,
        float[] outValues
    ) {
        if (!LOADED || serviceKey == 0L || regionKey == 0L || outValues == null) {
            return false;
        }
        if (outValues.length == 0
            || (outValues.length % NESTED_FEEDBACK_VALUES_PER_BIN) != 0
            || outValues.length > NESTED_FEEDBACK_MAX_BINS * NESTED_FEEDBACK_VALUES_PER_BIN) {
            return false;
        }
        return nativePollRegionNestedFeedback(serviceKey, regionKey, outValues);
    }

    public NestedFeedbackStatus getRegionNestedFeedbackStatus(long serviceKey, long regionKey) {
        if (!LOADED || !nestedFeedbackStatusSupported || serviceKey == 0L || regionKey == 0L) {
            return null;
        }
        int[] status = new int[NESTED_FEEDBACK_STATUS_FIELDS];
        final boolean ok;
        try {
            ok = nativeGetRegionNestedFeedbackStatus(serviceKey, regionKey, status);
        } catch (UnsatisfiedLinkError error) {
            nestedFeedbackStatusSupported = false;
            return null;
        }
        if (!ok) {
            return null;
        }
        return new NestedFeedbackStatus(
            status[0],
            status[1],
            status[2],
            status[3],
            status[4],
            status[5]
        );
    }

    public boolean setPackedFlowAtlas(long serviceKey, long atlasKey, short[] packedValues) {
        if (!LOADED || serviceKey == 0L || atlasKey == 0L || packedValues == null || packedValues.length == 0) {
            return false;
        }
        return nativeSetPackedFlowAtlas(serviceKey, atlasKey, packedValues);
    }

    public boolean pollPackedFlowAtlas(long serviceKey, long atlasKey, short[] outPackedValues) {
        if (!LOADED || serviceKey == 0L || atlasKey == 0L || outPackedValues == null || outPackedValues.length == 0) {
            return false;
        }
        return nativePollPackedFlowAtlas(serviceKey, atlasKey, outPackedValues);
    }

    public byte[] compressFloatGrid3d(float[] values, int nx, int ny, int nz, double tolerance) {
        if (!LOADED || values == null) {
            return null;
        }
        int cells = checkedCellCount(nx, ny, nz);
        if (cells <= 0 || values.length != cells || !(tolerance > 0.0) || Double.isNaN(tolerance)) {
            return null;
        }
        return nativeCompressFloatGrid3d(values, nx, ny, nz, tolerance);
    }

    public boolean decompressFloatGrid3d(byte[] compressed, int nx, int ny, int nz, float[] outValues) {
        if (!LOADED || compressed == null || outValues == null) {
            return false;
        }
        int cells = checkedCellCount(nx, ny, nz);
        return cells > 0
            && outValues.length == cells
            && nativeDecompressFloatGrid3d(compressed, nx, ny, nz, outValues);
    }

    public boolean sampleRegionPoint(
        long serviceKey,
        long regionKey,
        int nx,
        int ny,
        int nz,
        int sampleX,
        int sampleY,
        int sampleZ,
        float[] outProbeValues
    ) {
        if (!LOADED || serviceKey == 0L || regionKey == 0L || outProbeValues == null) {
            return false;
        }
        if (checkedCellCount(nx, ny, nz) <= 0 || outProbeValues.length != PLAYER_PROBE_CHANNELS) {
            return false;
        }
        return nativeSampleRegionPoint(
            serviceKey,
            regionKey,
            nx,
            ny,
            nz,
            sampleX,
            sampleY,
            sampleZ,
            outProbeValues
        );
    }

    public String runtimeInfo() {
        if (!LOADED) {
            return "not_loaded";
        }
        return nativeRuntimeInfo();
    }

    public String lastError() {
        if (!LOADED) {
            return "not_loaded";
        }
        return nativeLastError();
    }

    private static int checkedCellCount(int nx, int ny, int nz) {
        if (nx <= 0 || ny <= 0 || nz <= 0) {
            return -1;
        }
        long cells = (long) nx * (long) ny * (long) nz;
        return cells > Integer.MAX_VALUE ? -1 : (int) cells;
    }

    public record LevelDelta(
        int type,
        int x,
        int y,
        int z,
        int data0,
        int data1,
        int data2,
        int data3,
        float value0,
        float value1,
        float value2,
        float value3
    ) {
    }

    public record NestedFeedbackStatus(
        int configuredBinCount,
        int stepsPerFeedback,
        int stepsAccumulated,
        int readyPacketBinCount,
        int emittedPacketCount,
        int resetCount
    ) {
    }

    public record BrickLevelRuntimeStatus(
        int brickSize,
        int knownBrickCount,
        int activeHintCount,
        int activeBrickCount,
        int geometryDirtyCount,
        int forcingDirtyCount,
        int pendingReinitCount,
        int epoch
    ) {
    }

    private static native long nativeCreateService();

    private static native void nativeReleaseService(long serviceKey);

    private static native boolean nativeSetFocus(long serviceKey, int blockX, int blockY, int blockZ, int radiusBlocks);

    private static native boolean nativeSubmitLevelDeltas(long serviceKey, int[] encodedInts, float[] encodedFloats);

    private static native boolean nativeEnsureBrickLevelRuntime(
        long serviceKey,
        long levelKey,
        int brickSize,
        float dxMeters,
        float dtSeconds
    );

    private static native boolean nativeSetBrickLevelActiveHints(
        long serviceKey,
        long levelKey,
        int brickSize,
        int[] brickCoords,
        int brickCount
    );

    private static native boolean nativeSetBrickLevelExactActiveHints(
        long serviceKey,
        long levelKey,
        int brickSize,
        int[] brickCoords,
        int brickCount
    );

    private static native boolean nativeGetBrickLevelRuntimeStatus(
        long serviceKey,
        long levelKey,
        int[] outStatus
    );

    private static native boolean nativeStepBrickLevelRuntime(
        long serviceKey,
        long levelKey,
        int stepCount
    );

    private static native int[] nativeGetBrickLevelResidentBrickCoords(
        long serviceKey,
        long levelKey
    );

    private static native int[] nativeGetBrickLevelActiveBrickCoords(
        long serviceKey,
        long levelKey
    );

    private static native boolean nativeUploadBrickLevelStaticBrick(
        long serviceKey,
        long levelKey,
        int brickSize,
        int brickX,
        int brickY,
        int brickZ,
        byte[] obstacle,
        byte[] surfaceKind,
        short[] openFaceMask,
        float[] emitterPowerWatts,
        byte[] faceSkyExposure,
        byte[] faceDirectExposure
    );

    private static native boolean nativeQueueBrickLevelStaticBrickUpload(
        long serviceKey,
        long levelKey,
        int brickSize,
        int brickX,
        int brickY,
        int brickZ,
        byte[] obstacle,
        byte[] surfaceKind,
        short[] openFaceMask,
        float[] emitterPowerWatts,
        byte[] faceSkyExposure,
        byte[] faceDirectExposure
    );

    private static native boolean nativeSyncRegionCoreToBrickLevel(
        long serviceKey,
        long regionKey,
        long levelKey,
        int regionNx,
        int regionNy,
        int regionNz,
        int coreOffsetX,
        int coreOffsetY,
        int coreOffsetZ,
        int coreNx,
        int coreNy,
        int coreNz,
        int brickX,
        int brickY,
        int brickZ
    );

    private static native boolean nativeCopyBrickLevelDynamicBrick(
        long serviceKey,
        long levelKey,
        int brickSize,
        int brickX,
        int brickY,
        int brickZ,
        float[] outFlowState,
        float[] outAirTemperature,
        float[] outSurfaceTemperature
    );
    private static native boolean nativeCopyBrickLevelPackedFlowAtlas(
        long serviceKey,
        long levelKey,
        int brickSize,
        int brickX,
        int brickY,
        int brickZ,
        int sampleStride,
        short[] outPackedFlow
    );
    private static native boolean nativeUploadBrickLevelDynamicBrick(
        long serviceKey,
        long levelKey,
        int brickSize,
        int brickX,
        int brickY,
        int brickZ,
        float[] flowState,
        float[] airTemperature,
        float[] surfaceTemperature
    );
    private static native boolean nativeUploadBrickLevelBoundaryReferenceBrick(
        long serviceKey,
        long levelKey,
        int brickSize,
        int brickX,
        int brickY,
        int brickZ,
        float[] flowState,
        float[] airTemperature,
        float[] surfaceTemperature
    );

    private static native boolean nativeUploadStaticRegion(
        long serviceKey,
        long regionKey,
        int nx,
        int ny,
        int nz,
        byte[] obstacle,
        byte[] surfaceKind,
        short[] openFaceMask,
        float[] emitterPowerWatts,
        byte[] faceSkyExposure,
        byte[] faceDirectExposure
    );

    private static native boolean nativeUploadStaticRegionPatch(
        long serviceKey,
        long regionKey,
        int nx,
        int ny,
        int nz,
        int offsetX,
        int offsetY,
        int offsetZ,
        int patchNx,
        int patchNy,
        int patchNz,
        byte[] obstacle,
        byte[] surfaceKind,
        short[] openFaceMask,
        float[] emitterPowerWatts,
        byte[] faceSkyExposure,
        byte[] faceDirectExposure
    );

    private static native boolean nativeActivateRegion(long serviceKey, long regionKey, int nx, int ny, int nz);

    private static native boolean nativeDeactivateRegion(long serviceKey, long regionKey);

    private static native boolean nativeHasRegion(long serviceKey, long regionKey);

    private static native boolean nativeIsRegionReady(long serviceKey, long regionKey);

    private static native boolean nativeEnsureL2Runtime(
        long serviceKey,
        int nx,
        int ny,
        int nz,
        int inputChannels,
        int outputChannels
    );

    private static native boolean nativeHasRegionContext(long serviceKey, long regionKey);

    private static native boolean nativeSetRegionNestedFeedbackLayout(
        long serviceKey,
        long regionKey,
        int stepsPerFeedback,
        int[] layout
    );

    private static native boolean nativeUploadRegionForcing(
        long serviceKey,
        long regionKey,
        int nx,
        int ny,
        int nz,
        float[] thermalSource,
        byte[] fanMask,
        float[] fanVx,
        float[] fanVy,
        float[] fanVz
    );

    private static native boolean nativeRefreshRegionThermal(
        long serviceKey,
        long regionKey,
        int nx,
        int ny,
        int nz,
        float directSolarFluxWm2,
        float diffuseSolarFluxWm2,
        float ambientAirTemperatureKelvin,
        float deepGroundTemperatureKelvin,
        float skyTemperatureKelvin,
        float precipitationTemperatureKelvin,
        float precipitationStrength,
        float sunX,
        float sunY,
        float sunZ,
        float surfaceDeltaSeconds
    );

    private static native boolean nativeStepRegion(
        long serviceKey,
        long regionKey,
        ByteBuffer payload,
        int nx,
        int ny,
        int nz,
        float[] outputFlow
    );

    private static native boolean nativeStepRegionStored(
        long serviceKey,
        long regionKey,
        int nx,
        int ny,
        int nz,
        float boundaryWindX,
        float boundaryWindY,
        float boundaryWindZ,
        float fallbackBoundaryAirTemperatureKelvin,
        int externalFaceMask,
        int boundaryFaceResolution,
        float[] boundaryWindFaceX,
        float[] boundaryWindFaceY,
        float[] boundaryWindFaceZ,
        float[] boundaryAirTemperatureKelvin,
        int spongeThicknessCells,
        float spongeVelocityRelaxation,
        float spongeTemperatureRelaxation,
        int tornadoDescriptorCount,
        float[] tornadoDescriptors,
        float[] outMaxSpeed
    );

    private static native boolean nativeExchangeRegionHalo(
        long serviceKey,
        long firstRegionKey,
        long secondRegionKey,
        int gridSize,
        int offsetX,
        int offsetY,
        int offsetZ
    );

    private static native int nativeExchangeRegionHaloBatch(
        long serviceKey,
        long[] regionPairs,
        int[] offsets,
        int exchangeCount
    );

    private static native boolean nativeSyncRegionState(
        long serviceKey,
        long regionKey,
        float[] outMaxSpeed
    );

    private static native boolean nativeSyncRegionFlowState(
        long serviceKey,
        long regionKey,
        float[] outMaxSpeed
    );

    private static native boolean nativeGetRegionTemperatureState(
        long serviceKey,
        long regionKey,
        int nx,
        int ny,
        int nz,
        float[] outTemperature
    );

    private static native boolean nativeGetRegionFlowState(
        long serviceKey,
        long regionKey,
        int nx,
        int ny,
        int nz,
        float[] outFlowState
    );

    private static native boolean nativeSetRegionTemperatureState(
        long serviceKey,
        long regionKey,
        int nx,
        int ny,
        int nz,
        float[] temperature
    );

    private static native boolean nativeReleaseRegionRuntime(long serviceKey, long regionKey);

    private static native boolean nativeImportDynamicRegion(
        long serviceKey,
        long regionKey,
        int nx,
        int ny,
        int nz,
        float[] flowState,
        float[] airTemperature,
        float[] surfaceTemperature
    );

    private static native boolean nativeExportDynamicRegion(
        long serviceKey,
        long regionKey,
        int nx,
        int ny,
        int nz,
        float[] outFlowState,
        float[] outAirTemperature,
        float[] outSurfaceTemperature
    );

    private static native boolean nativePollRegionNestedFeedback(
        long serviceKey,
        long regionKey,
        float[] outValues
    );

    private static native boolean nativeGetRegionNestedFeedbackStatus(
        long serviceKey,
        long regionKey,
        int[] outStatus
    );

    private static native boolean nativeSetPackedFlowAtlas(long serviceKey, long atlasKey, short[] packedValues);

    private static native boolean nativePollPackedFlowAtlas(long serviceKey, long atlasKey, short[] outPackedValues);

    private static native boolean nativeSampleRegionPoint(
        long serviceKey,
        long regionKey,
        int nx,
        int ny,
        int nz,
        int sampleX,
        int sampleY,
        int sampleZ,
        float[] outProbeValues
    );

    private static native byte[] nativeCompressFloatGrid3d(
        float[] values,
        int nx,
        int ny,
        int nz,
        double tolerance
    );

    private static native boolean nativeDecompressFloatGrid3d(
        byte[] compressed,
        int nx,
        int ny,
        int nz,
        float[] outValues
    );

    private static native String nativeRuntimeInfo();

    private static native String nativeLastError();
}
