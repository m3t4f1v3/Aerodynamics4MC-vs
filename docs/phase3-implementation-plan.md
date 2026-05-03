# Phase 3 Implementation Plan

## Current State Analysis

### What We Have Now (Post-Phase 2)
1. **Dual extraction paths**:
   - `pollPackedFlowAtlas()` - tries region first, falls back to brick runtime
   - `sampleRegionPointLocked()` - tries brick runtime first, falls back to region
   - `snapshotFullRegionFlow()` - region-only (diagnostic)
   - `buildAnalysisFlowPayload()` - region-only (diagnostic)

2. **RegionRecord structure** (line 7692):
   - Holds metadata: `busy`, `serviceReady`, `attached`, `staticUploaded`
   - Holds geometry: `sections`, `sectionVersions`, `uploadedSectionVersions`
   - Holds forcing: `fans`, `fansDirty`, `forcingDirty`
   - Holds boundary cache: `cachedBoundarySample`, `cachedBoundaryField`
   - Holds feedback: `nestedFeedbackLayout`
   - **Does NOT hold dynamic flow state** (already removed in Phase 2)

3. **Key insight**: `RegionRecord` is now primarily a **metadata and geometry cache**, not a flow-state container.

## Phase 3 Goal

> Replace the current per-region maps with a native brick store:
> - remove canonical `dynamic_regions`
> - replace region packet templates with brick-centered storage
> - keep atlas/probe extraction as the Java-facing publication layer

**Interpretation**: 
- "dynamic_regions" refers to the conceptual model, not a specific field (already removed)
- "region packet templates" = the region-based extraction APIs
- Goal: Make brick runtime the **only** authoritative path, remove region fallback

## Migration Strategy

### Step 1: Make Brick Runtime the Primary Path (Conservative)
**Goal**: Ensure brick runtime can handle all extraction needs before removing region support.

**Actions**:
1. Verify brick runtime atlas extraction works for all strides
2. Verify brick runtime probe sampling works for all cases
3. Add brick runtime support for diagnostic exports (L2 capture, analysis flow)
4. Keep region-based code as deprecated fallback

**Validation**: All existing functionality works through brick runtime.

### Step 2: Deprecate Region-Based Extraction
**Goal**: Mark region-based extraction as legacy, route all hot paths through brick runtime.

**Actions**:
1. Change `pollPackedFlowForNetwork()` to brick-only (remove region fallback)
2. Change `sampleRegionPointLocked()` to brick-only (remove region fallback)
3. Update diagnostic exports to use brick runtime
4. Add deprecation warnings to region-based APIs

**Validation**: No hot paths use region-based extraction.

### Step 3: Simplify RegionRecord to Metadata-Only
**Goal**: RegionRecord becomes a pure metadata/geometry cache, not tied to solver state.

**Actions**:
1. Remove `serviceReady`, `serviceActive` flags (brick runtime owns this)
2. Keep `sections`, `fans`, `boundary cache` (still useful for geometry/forcing)
3. Rename `RegionRecord` → `WindowMetadata` to clarify purpose
4. Move window metadata to a separate structure from brick runtime

**Validation**: RegionRecord no longer implies solver state ownership.

### Step 4: Introduce Brick-Centered Publication Layer
**Goal**: Replace window-keyed atlases with brick-keyed atlases.

**Actions**:
1. Change `PublishedFrame` from `Map<WindowKey, short[]>` to brick-based structure
2. Update client-side atlas consumption to work with brick coordinates
3. Keep window-to-brick mapping for backward compatibility

**Validation**: Client visualization works with brick-based atlases.

## What NOT to Do in Phase 3

1. **Don't remove WorldMirror yet** - Phase 4 handles geometry path
2. **Don't change window scheduler yet** - Phase 5 handles scheduling
3. **Don't add persistence yet** - Phase 6 handles persistence
4. **Don't break existing diagnostics** - Keep L2 capture, analysis flow working

## Risk Assessment

### Low Risk
- Making brick runtime the primary extraction path (already works as fallback)
- Deprecating region-based extraction (no functional change)

### Medium Risk
- Changing PublishedFrame structure (affects client-server protocol)
- Removing region-based diagnostic exports (need brick equivalents)

### High Risk
- Removing RegionRecord entirely (lots of code depends on it)
- Changing window-based coordinate system (pervasive)

## Recommended Approach

**Conservative Phase 3a**: Make brick runtime primary, keep region as deprecated fallback
- Low risk, high value
- Validates brick runtime completeness
- Allows gradual migration

**Aggressive Phase 3b**: Remove region-based extraction entirely
- Medium risk, clean architecture
- Only after 3a is stable
- Requires client protocol changes

## Implementation Order

1. ✅ **Verify brick runtime completeness** (check all extraction paths work)
2. **Add brick runtime diagnostic support** (L2 capture, analysis flow)
3. **Make brick runtime primary** (remove region fallback from hot paths)
4. **Deprecate region-based APIs** (add warnings, mark for removal)
5. **Simplify RegionRecord** (metadata-only, rename to WindowMetadata)
6. **Introduce brick-based publication** (optional, can defer to Phase 5)

## Success Criteria

Phase 3 is complete when:
- ✅ All extraction goes through brick runtime (no region fallback in hot paths)
- ✅ RegionRecord is metadata-only (no solver state ownership)
- ✅ Diagnostics work through brick runtime
- ✅ Performance is equal or better than Phase 2
- ✅ All existing features still work

## Next Phase Preview

**Phase 4**: Route world updates directly into native bricks
- Replace `WorldMirror -> section rebuild -> static patch` path
- Block updates queue directly into brick runtime
- WorldMirror becomes optional debug helper
