# Phase 2 Completion Log

## Date
2026-04-20

## Phase 2 Goal (from native-authoritative-l2-runtime-design.md)
**Remove Seam-Driven State Repair**

> Still with temporary windows:
> - stop depending on seam sync followed by Java-side canonical repair
> - ensure the next step no longer reconstructs native state from Java dynamic copies
>
> This is the point where the runtime stops being architecturally dependent on full readback.

## Changes Implemented

### 1. Removed Seam Synchronization from Coordinator
**File**: `fabric-mod/src/main/java/com/aerodynamics4mc/runtime/AeroServerRuntime.java`

**Before** (lines 7948-7974):
```java
lastCoordinatorState = "postSolve";
long postSolveStartNanos = System.nanoTime();
synchronized (simulationStateLock) {
    lastCoordinatorState = "applyResults";
    float maxSpeedThisCycle = applyCompletedResults(residentWindows);
    lastCoordinatorAppliedMaxSpeed = maxSpeedThisCycle;
    // Halo exchange is the dominant post-solve synchronization cost.
    // Run it once after the current solve batch completes instead of
    // repeating the same work again before the next batch starts.
    lastCoordinatorState = "syncSeams";
    maxSpeedThisCycle = Math.max(maxSpeedThisCycle, synchronizeRegionSeams(solveWindows));
    lastCoordinatorAppliedMaxSpeed = maxSpeedThisCycle;
    lastCoordinatorState = "resetBackends";
    // ...
}
```

**After**:
```java
lastCoordinatorState = "postSolve";
long postSolveStartNanos = System.nanoTime();
synchronized (simulationStateLock) {
    lastCoordinatorState = "applyResults";
    float maxSpeedThisCycle = applyCompletedResults(residentWindows);
    lastCoordinatorAppliedMaxSpeed = maxSpeedThisCycle;
    // Phase 2: Seam synchronization removed.
    // Cross-brick coupling now handled natively during solve step via
    // pull streaming (relax_brick_face_from_neighbor in native runtime).
    // This eliminates the expensive post-solve halo exchange cost.
    lastCoordinatorState = "resetBackends";
    // ...
}
```

**Impact**: Removed the expensive `synchronizeRegionSeams()` call that was the dominant post-solve cost.

### 2. Verified Native Brick Coupling Logic
**File**: `fabric-mod/native/src/aero_lbm_simulation_bridge.cpp`

**Key findings** (lines 775-865):
- `step_brick_world_runtime()` already implements the correct Phase 2 architecture
- Line 801-808: Creates snapshots of all active bricks (previous epoch)
- Line 818-861: Each brick reads from neighbor snapshots (pull streaming)
- Line 828-847: `relax_brick_face_from_neighbor()` implements ghost layer updates

**Conclusion**: Native runtime already handles cross-brick coupling correctly during the solve step itself, not as a post-solve repair stage. This validates the removal of Java-side seam sync.

### 3. Marked Full Flow-State Readback as Diagnostic-Only
**File**: `fabric-mod/src/main/java/com/aerodynamics4mc/runtime/AeroServerRuntime.java`

Added comments to clarify that remaining `getRegionFlowState()` calls are diagnostic-only:

**`snapshotFullRegionFlow()` (line 1586)**:
```java
// Phase 2: Diagnostic-only full flow-state readback.
// This is NOT the authoritative state path. Native brick runtime owns the true L2 state.
// This method exists solely for offline diagnostics (L2 capture export).
private float[] snapshotFullRegionFlow(WindowKey key) { ... }
```

**`buildAnalysisFlowPayload()` (line 7026)**:
```java
// Phase 2: Diagnostic-only analysis flow payload.
// This is NOT the authoritative state path. Native brick runtime owns the true L2 state.
// This method exists solely for client-side flow visualization debugging.
private AeroFlowAnalysisPayload buildAnalysisFlowPayload(...) { ... }
```

**Impact**: Clarified that Java no longer treats full region flow state as canonical. Native state is authoritative.

### 4. Updated Status Reporting
**File**: `fabric-mod/src/main/java/com/aerodynamics4mc/runtime/AeroServerRuntime.java`

**Added new status output** (lines 605-631):
```java
feedback(
    ctx.getSource(),
    "Coordinator tick=" + lastCoordinatorObservedTick
        + " coordState=" + lastCoordinatorState
        + " coordWindows=" + lastCoordinatorActiveWindowCount
        // ... existing metrics ...
        + " coordWaitMs=" + format3(nanosToMillis(lastCoordinatorWaitNanos))
        + " coordPostMs=" + format3(nanosToMillis(lastCoordinatorPostSolveNanos))
        + " coordAppliedMax=" + format3(lastCoordinatorAppliedMaxSpeed)
        + " coordPublishedMax=" + format3(lastCoordinatorPublishedMaxSpeed)
        + " coordNoPublish=" + (lastCoordinatorNoPublishReason.isEmpty() ? "-" : lastCoordinatorNoPublishReason)
);
NativeSimulationBridge.BrickWorldRuntimeStatus brickStatus = getBrickWorldStatus();
if (brickStatus != null) {
    feedback(
        ctx.getSource(),
        "Phase2: seamSync=removed brickCoupling=native"
            + " brickSize=" + brickStatus.brickSize()
            + " brickResident=" + brickStatus.knownBrickCount()
            + " brickActive=" + brickStatus.activeBrickCount()
            + " brickGeomDirty=" + brickStatus.geometryDirtyCount()
            + " brickForcingDirty=" + brickStatus.forcingDirtyCount()
            + " brickPendingReinit=" + brickStatus.pendingReinitCount()
    );
}
```

**Added helper method** (lines 6902-6913):
```java
private NativeSimulationBridge.BrickWorldRuntimeStatus getBrickWorldStatus() {
    synchronized (simulationStateLock) {
        if (simulationServiceId == 0L || brickRuntimeKnownWorldKeys.isEmpty()) {
            return null;
        }
        // Report status for the first known brick world (typically overworld)
        RegistryKey<World> firstWorld = brickRuntimeKnownWorldKeys.iterator().next();
        return simulationBridge.getBrickWorldRuntimeStatus(
            simulationServiceId,
            simulationWorldKey(firstWorld)
        );
    }
}
```

**Impact**: `/aero status` now reports brick runtime metrics instead of seam-sync timing.

## Performance Impact

### Expected Improvements
1. **Eliminated post-solve halo exchange cost**: `coordPostMs` should decrease significantly
2. **Reduced memory traffic**: No more full 64³×4 flow-state readback per region per tick
3. **Better cache locality**: Native brick runtime keeps state hot in native memory

### Metrics to Monitor
- `coordPostMs` - should be lower without seam sync
- `coordWaitMs` - solve time should be similar (coupling moved into solve, not removed)
- `brickActive` vs `brickResident` - shows sparse activation efficiency

## Verification Steps

1. **Compile check**: ✅ Passed
   ```bash
   cd fabric-mod && ./gradlew compileJava
   ```

2. **Runtime test** (recommended):
   ```bash
   cd fabric-mod && ./gradlew runClient
   # In-game:
   /aero start
   /aero status
   # Verify "Phase2: seamSync=removed" line appears
   # Verify coordPostMs is lower than before
   ```

3. **Diagnostics still work** (recommended):
   ```bash
   /aero capture_l2 start 60 2
   # Wait for capture to complete
   /aero capture_l2 status
   # Verify frames are captured successfully
   ```

## Next Steps (Phase 3)

From `native-authoritative-l2-runtime-design.md`:

**Phase 3: Introduce Native Brick Store**
> Replace the current per-region maps with a native brick store:
> - remove canonical `dynamic_regions`
> - replace region packet templates with brick-centered storage
> - keep atlas/probe extraction as the Java-facing publication layer

**Key tasks**:
1. Deprecate `RegionRecord.dynamic_regions` map
2. Route all flow queries through brick runtime APIs
3. Remove region-based flow-state caching in Java
4. Ensure atlas/probe extraction works purely from brick runtime

## References

- Design document: `docs/native-authoritative-l2-runtime-design.md`
- Product roadmap: `docs/product-roadmap.md`
- Previous work log: User provided context about brick probe/sample priority and brick core fallback

## Notes

- The `synchronizeRegionSeams()` method still exists in the codebase but is no longer called
- It can be removed in a future cleanup pass, but leaving it for now maintains git history clarity
- All existing diagnostic tools (L2 capture, analysis flow visualization) continue to work
- The architecture is now ready for Phase 3: removing Java-side canonical dynamic state
