# World-Scale Weather Design

## Scope

This document describes the weather stack that currently exists in `fabric-mod`, and the intended extension points above it.

The stack is layered:

- `WorldScaleDriver`
- `L0 / BackgroundMetGrid`
- `L1 / MesoscaleGrid`
- `L2 / active local solver windows`

The goal is not full atmospheric science fidelity. The goal is a coherent multiscale weather system that:

- evolves over time,
- can be nested into the local solver,
- remains debuggable,
- can support visible weather events such as cyclones, convective cores, and rare tornadoes.

## Current Spatial and Temporal Scales

### L0

- cell size: `256 x 256` blocks
- radius: `20` cells
- grid width: `41 x 41`
- refresh cadence: `BACKGROUND_MET_REFRESH_TICKS = MESOSCALE_REFRESH_TICKS * 4`

`L0` is a dynamic coarse field, not a static hash wind map.

### L1

- cell size: `64 x 64` blocks
- radius: `16` cells
- grid width: `33 x 33`
- vertical layer height: `40` blocks
- max layers: `8`
- step size: `MESOSCALE_STEP_SECONDS = 3.2s`
- refresh cadence: `MESOSCALE_REFRESH_TICKS`

`L1` is a layered mesoscale model with temperature, humidity, and horizontal wind.

### L2

- local active regions only
- region core size: `32`
- halo per side: `16`
- nested BC applied on external faces only
- tornado forcing staged per active region as local body forcing descriptors

## Layer Responsibilities

### WorldScaleDriver

`WorldScaleDriver` is the world-scale slow driver.

It owns:

- `baseFlowX/Z`
- `airmassTemperatureBias`
- `airmassMoistureBias`
- `planetaryWavePhase`
- `stormActivity`
- `seasonPhase`
- persistent weather objects:
  - `CycloneCell`
  - `ConvectiveCluster`
  - `TornadoVortex`

It is deterministic on first creation from world seed, then persistent from save data.

### L0 / BackgroundMetGrid

`L0` is the first dynamic spatial field. It advances coarse state over time.

Its primary state is:

- `backgroundWindX`
- `backgroundWindZ`
- `ambientAirTemperatureKelvin`
- `humidity`

It also carries driver-derived forcing fields that are passed down to `L1`:

- convective heating/moistening/inflow/envelope
- tornado wind/heating/moistening/updraft proxy

It exports diagnostics:

- `vorticity`
- `divergence`
- `temperature_anomaly`

### L1 / MesoscaleGrid

`L1` consumes:

- terrain and biome forcing,
- `L0` background wind/temperature/humidity,
- convective forcing,
- tornado forcing.

It maintains layered state:

- ambient/deep/surface temperature
- wind `x/z`
- humidity

It exports diagnostics:

- `instability_proxy`
- `low_level_shear`
- `moisture_convergence`
- `lift_proxy`

It also provides localized summaries:

- `diagnosticsSummary(...)`
- `sampleTornadoEnvironment(...)`

These are used by `WorldScaleDriver` to decide whether convective environments are strong enough to support tornadogenesis.

### L2

`L2` is the local high-resolution solver around active windows.

It receives:

- one-way nested boundary conditions from `L1` on external faces only,
- local tornado body forcing descriptors for active regions that intersect tornado influence radii.

This keeps the division clean:

- `L1` provides environment,
- `L2` resolves local structure.

## Weather Objects

### CycloneCell

Purpose:

- world-scale pressure-like steering object
- creates broad rotation and thermal/moisture structure in `L0`

Characteristics:

- dual-envelope structure:
  - broad outer steering
  - sharper core
- persistent and drifting
- can be warm-core or cold-core depending on sign and biases

Cyclones are not solved from a pressure PDE. They are explicit persistent driver objects.

### ConvectiveCluster

Purpose:

- concentrated warm/moist/convergent event embedded in larger-scale weather
- bridge between cyclone-scale structure and severe local weather

Characteristics:

- finite radius
- intensity envelope over lifecycle
- drift with host/background flow
- injects:
  - heating
  - moistening
  - inflow/convergence

Clusters are strengthened or suppressed by mesoscale diagnostics:

- convective support
- lift support
- shear support

### TornadoVortex

Purpose:

- rare local extreme event
- not expected to occur often

Current model:

- explicit object, not naturally emergent from `L0`
- generated only if:
  - `stormActivity` is high enough,
  - a `ConvectiveCluster` is eligible,
  - local `L1` tornado environment exceeds threshold
- persistent lifecycle:
  - organizing
  - mature
  - rope-out
  - dissipated

Current forcing:

- injected into `L1`
- staged into `L2` as region-local descriptors

This is intentionally conservative. Tornadoes are allowed to be rare.

## Data Flow

### Top-down weather path

1. `WorldScaleDriver.advance(...)`
2. `BackgroundMetGrid.refresh(...)`
3. `MesoscaleGrid.refresh(...)`
4. `MesoscaleGrid.runPendingSteps(...)`
5. `L1 -> L2` nested boundary sampling
6. `TornadoVortex -> L2` local descriptor staging

### Feedback path

There is limited feedback upward:

- `MesoscaleGrid.diagnosticsSummary(...)` is sampled by the runtime
- summary is fed back into `WorldScaleDriver.advance(...)`
- this affects convective support and severe weather favorability

This is not a fully coupled two-way atmospheric model. It is a pragmatic semi-coupled gameplay/weather stack.

## Nested Boundary Conditions

Current `L1 -> L2` nested BC is:

- one-way
- external-face-only
- low-resolution face fields
- includes `vx / vy / vz / air_temperature`
- `vy` is reconstructed from horizontal divergence and vertical integration

Internal region faces continue to use `L2-L2` halo exchange.

This avoids corrupting interior continuity while still injecting mesoscale environment at the boundary.

## Diagnostics and Dumps

Current runtime dump entrypoint:

- `/aero dumpdata`

Outputs:

- `L0` JSON
- `L1` JSON

Helper scripts:

- `eval_background_snapshot.py`
- `eval_mesoscale_snapshot.py`

These are part of the standard debugging loop and should be kept in sync with any schema changes.

## Non-goals

This stack is not attempting to be:

- a full NWP model,
- a 3D global atmospheric solver,
- a moisture microphysics package,
- a guaranteed-frequent tornado simulator.

The design target is coherent, persistent, hierarchical weather with useful diagnostics.

## What Is Still Missing

These items are still open or only partially implemented:

- cloud/condensation proxy layer
- explicit cloud rendering path
- richer severe-weather observability
- stronger gameplay integration for weather effects
- fully validated `TornadoVortex -> L2` runtime behavior in gameplay, beyond descriptor staging
- recovered foliage/shaderpack wind integration

## Implementation Guidance

When extending this stack:

- keep `WorldScaleDriver` object-driven
- keep `L0` cheap and debuggable
- keep `L1` as the environment/diagnostics layer
- keep `L2` for local resolved structure
- prefer explicit dumpable state over hidden procedural magic

If a new weather feature cannot be observed in dumps, it is not complete enough to trust.
