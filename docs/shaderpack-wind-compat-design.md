# Shaderpack Wind Compatibility Design

## Goal

Reuse existing community shaderpacks for foliage motion instead of maintaining a full parallel terrain/foliage shader stack inside the mod.

The mod should provide real wind data.
The shaderpack should keep its own style, lighting, and foliage shaping logic.

The mod should **not** depend on redistributing modified third-party packs.

## Target Strategy

The compatibility plan is:

- Iris first
- BSL first within Iris
- define a stable A4MC runtime wind contract
- get pack-side support upstream where possible

This is intentionally narrow. Supporting every OptiFine/Oculus/Iris pack generically is not a realistic first step.

## Why Not a Fully Separate Mod Shader

A fully custom foliage shader inside the mod duplicates work already solved by shaderpacks:

- foliage material look
- pack-specific bend logic
- shadow consistency
- animation tuning
- overall style cohesion

The correct responsibility split is:

- mod supplies real wind field
- pack consumes it

## Compatibility Layers

There are two distinct layers.

### 1. Runtime bridge

The mod must expose wind data in a form the shaderpack can sample.

Contract:

- a dynamic wind field texture
- origin metadata
- grid size metadata
- cell size/range metadata

This bridge should be Iris-first and minimal.

### 2. Pack integration

The shaderpack integration should:

- include A4MC helper code or equivalent pack-side logic
- replace procedural foliage wind input with sampled wind input
- preserve the pack's own bending style and material response

The integration should not rewrite unrelated rendering logic.

For production use, the preferred path is:

- pack author cooperation,
- upstream PRs,
- or officially maintained optional support from the pack side.

Local patching can still exist as a development aid, but it should not be the primary product model.

## BSL 10.1.1 Notes

BSL 10.1.1 is not organized around the older `gbuffers_terrain.vsh/.fsh` split.

Important hook points discovered during inspection:

- `shaders/program/gbuffers_terrain.glsl`
- `shaders/lib/vertex/waving.glsl`
- `shaders/shaders.properties`

The likely patch point is `waving.glsl`, where `WavingBlocks(...)` and related motion helpers live.

That is the correct place to substitute real wind input for BSL foliage motion.

## Proposed Data Contract

Pack-side names:

- `a4mcWindField`
- `a4mc_foliage_wind.glsl`

Required inputs:

- dynamic texture containing sampled local wind
- derived pack-side mapping from world position to texture coordinates

### Current implemented runtime bridge

The current Iris-side bridge is implemented in:

- [IrisWindBridge.java](/home/firedoge/Projects/fno/aerodynamics4mc/fabric-mod/src/client/java/com/aerodynamics4mc/client/IrisWindBridge.java)

It currently publishes:

- texture id: `aerodynamics4mc:dynamic/foliage_wind`
- grid dimensions: `48 x 24 x 48`
- cell size: `4` blocks
- texture layout:
  - width = `GRID_X * GRID_Z`
  - height = `GRID_Y`
  - flattened x index = `z * GRID_X + x`

The texture is rebuilt from the client-side `L2` atlas sample, not from procedural wind.

### Current encoding

Each texel stores one sampled wind vector:

- `R`: encoded signed `wind.x`
- `G`: encoded signed `wind.y`
- `B`: encoded signed `wind.z`
- `A`: normalized magnitude

Current normalization range:

- `ENCODED_MAX_WIND_MPS = 12.0`

Signed channels are encoded as:

- `value_normalized = clamp(value / maxWind, -1, 1)`
- `encoded = value_normalized * 0.5 + 0.5`

### Current origin reconstruction rule

No extra runtime uniform is required for origin.

The wind field is anchored relative to the camera/player using quantized origin reconstruction:

- `origin = floor(cameraCoord / cellSize) * cellSize - halfExtentBlocks`

with:

- `halfExtentBlocks = (axisCells * cellSize) / 2`

That rule is stable enough for pack-side helper code to reconstruct the same sampling frame from world/camera position.

Preferred design:

- keep pack-side coordinate reconstruction simple
- avoid requiring many custom uniforms if the same mapping can be reconstructed from known constants

The important architectural point is that the **contract belongs to the mod/runtime**, not to any one patched copy of a pack.

## Fallback Behavior

Patch behavior must be safe if runtime bridge data is not available.

If A4MC live wind data is unavailable:

- the pack should fall back to its original procedural wind
- the patch must not break the shaderpack

This is mandatory for maintainability.

## Iris-First Rationale

Iris is the correct first target because:

- it is open source
- it is the practical Fabric-side shader runtime
- it is the most realistic place to establish a stable bridge

OptiFine and Oculus should be treated as later compatibility targets, not the first implementation surface.

## Product Model

The standalone mod should ship:

- the runtime bridge,
- the public A4MC shader contract,
- documentation for pack authors,
- and, if needed, a fallback internal foliage path.

The standalone mod should **not** ship:

- modified copies of third-party shaderpacks,
- or a workflow that assumes redistribution of pack-derived assets.

If local patch tooling exists, it should be treated as:

- developer tooling,
- local user-side experimentation,
- or migration aid for testing support proposals.

## Constraints

### Licensing

Do not vendor closed or ARR shaderpack contents into the repo.

Do not make redistributed patched packs part of the core mod delivery model.

The safest practical direction is:

- mod exposes runtime data,
- pack authors opt in,
- users keep using the original pack distribution channel.

### Engineering

Do not assume one universal texture slot or one universal hook point across packs.

Compatibility should be:

- family-specific
- version-aware
- conservative

## Recommended Implementation Order

1. Implement the Iris runtime bridge
2. Freeze the A4MC shader contract
3. Document BSL integration points clearly
4. Validate the contract against a local BSL test build
5. Upstream or otherwise formalize pack-side support
6. Only then consider a second pack family

## Success Criteria

This line is successful when:

- BSL under Iris keeps its own foliage style
- foliage motion direction and amplitude respond to real A4MC wind
- fallback procedural waving still works if live wind data is unavailable
- the pack-side integration remains small enough to maintain across BSL updates

## Practical Conclusion

It is not realistic to make existing third-party shaderpacks react to real wind without some pack-side change.

So the viable architecture is:

- mod-side runtime bridge,
- pack-side optional support,
- no redistributed patched packs as the main product path.
