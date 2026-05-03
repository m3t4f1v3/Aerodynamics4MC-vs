# Wind Shear Weather Roadmap

## Scope

This roadmap describes how to upgrade the current `L0/L1` weather stack so it can support believable wind shear for flight-vehicle mods.

The target is not full numerical weather prediction. The target is a game-engine weather model that:

- provides spatially and temporally coherent wind,
- provides vertical wind shear, gusts, and turbulence intensity,
- gives flight vehicles physically useful samples,
- keeps `L0/L1` cheap enough to run server-side,
- remains dumpable and debuggable.

## Current Baseline

The current stack is:

- `WorldScaleDriver`: slow world-scale procedural weather driver
- `L0 / BackgroundMetGrid`: coarse dynamic background field
- `L1 / MesoscaleGrid`: layered terrain-aware mesoscale field
- `L2`: local resolved LBM around active bricks

Recent baseline improvements:

- `L1` layers are interpreted as terrain-following AGL layers instead of absolute world-y layers.
- `L1 -> L2` seeding uses local interpolated terrain height when mapping y to L1 layers.
- `L0` has a synoptic lull factor so fair-weather wind can weaken instead of staying near a constant speed.
- `L0` target wind is modified by terrain form drag and contour-following deflection.
- Cyclone rotation now uses a pseudo-latitude Coriolis sign and strength.

This is enough for broad environmental wind and L2 boundary conditions. It is not enough for high-quality flight wind shear.

## Main Gap

The current model still tends to prescribe wind directly.

Current simplified path:

```text
WorldScaleDriver target wind
  -> L0 relax/advection/diffusion
  -> terrain/roughness correction
  -> L1 layered relaxation
  -> consumers
```

For flight dynamics, this is too weak because wind direction and wind speed are still mostly imposed. A more useful model should derive wind from slower scalar fields.

Target path:

```text
pressure / geopotential / temperature / humidity / terrain
  -> pressure gradient + Coriolis + friction
  -> geostrophic and surface wind
  -> ABL vertical shear profile
  -> gust and turbulence field
  -> flight sampling API
```

## Design Principles

- `L0` should become a prognostic scalar-field driver, not only a target-wind provider.
- `L1` should remain the atmospheric boundary-layer and diagnostics layer.
- `L2` should remain local resolved flow only. Do not make L2 responsible for world weather.
- Flight vehicles should sample a field contract, not internal solver buffers.
- Every new state used for gameplay must be visible in dumps.
- Prefer reduced-order atmospheric models over expensive CFD at L0/L1.

## Required Flight Sampling Contract

Flight-vehicle mods need more than `vx/vy/vz/p`.

Recommended sample output:

- mean velocity: `vx`, `vy`, `vz`
- pressure or pressure anomaly
- temperature
- humidity
- turbulence intensity
- gust velocity: `gustX`, `gustY`, `gustZ`
- wind shear: either `du/dy`, `dw/dy` or a compact velocity-gradient tensor
- source level: `L2`, `L1`, or `L0`
- confidence or freshness metadata

Vehicle integration should sample multiple points:

- wing left/right
- wing root/tip
- nose/tail
- upper/lower relevant surfaces

A single center-point wind sample is not enough to model shear, gust roll, yaw disturbances, or rotor/propeller inflow changes.

## Phase 1: Stabilize L1 ABL Semantics

Goal: make `L1` provide useful vertical wind shear even before L0 becomes fully scalar-field driven.

Implementation:

- Keep `L1` terrain-following AGL layers.
- Add an explicit ABL profile stage in `MesoscaleGrid`.
- Compute near-surface wind using roughness length and stability class.
- Compute aloft wind from `L0`.
- Blend from surface wind to aloft wind by height.
- Add Ekman-style directional turning with height.
- Export low-level shear diagnostics in physical units.

Inputs:

- terrain height
- roughness length
- surface class
- surface temperature
- ambient temperature
- humidity
- L0 aloft wind

Outputs:

- layered wind profile
- layered temperature/humidity profile
- `du/dy`, `dw/dy`
- stability class or mixing strength

Acceptance criteria:

- wind at 2 to 10 blocks AGL is weaker than aloft wind over rough terrain,
- wind turns with height under nonzero pseudo-Coriolis,
- night/stable conditions produce stronger near-surface shear,
- daytime/unstable conditions produce stronger vertical mixing and weaker shear.

Code targets:

- `fabric-mod/src/main/java/com/aerodynamics4mc/runtime/MesoscaleGrid.java`
- `fabric-mod/native/src/aero_lbm_mesoscale.cpp`
- L1 dump/export paths in `AeroServerRuntime`

## Phase 2: Add L0 Pressure And Geopotential Fields

Goal: make L0 wind derive from pressure gradients instead of directly from target wind.

New L0 state:

- pressure anomaly or geopotential height
- potential temperature anomaly
- humidity
- terrain height
- roughness
- optional storm activity and frontal activity

Derived L0 wind:

```text
geostrophic wind = pressure_gradient x Coriolis
surface wind = geostrophic wind + friction + terrain form drag
```

Implementation:

- Add pressure/geopotential arrays to `BackgroundMetGrid`.
- Make `WorldScaleDriver` generate and persist pressure systems, not only wind vectors.
- Use finite differences on pressure/geopotential to derive aloft wind.
- Keep existing direct wind as fallback only.
- Dump pressure, pressure gradient, and derived geostrophic wind.

Acceptance criteria:

- high/low pressure systems create rotating flow without directly assigning wind vectors,
- wind naturally changes direction around pressure structures,
- calm regions appear between weak pressure gradients,
- rain/thunder can strengthen pressure gradients without overriding the whole field.

Code targets:

- `WorldScaleDriver.java`
- `BackgroundMetGrid.java`
- L0 snapshot and JSON dump code

## Phase 3: Terrain-Induced Mesoscale Wind

Goal: make large terrain features affect L0/L1 wind before L2 is involved.

Mechanisms:

- form drag over steep terrain
- ridge lift on windward slopes
- lee-side shear and wake proxy
- valley or channeling flow
- water-land thermal contrast for simple sea/lake breeze proxy

Implementation:

- Extend current terrain form drag with directional terrain curvature and channel metrics.
- Precompute or lazily sample terrain gradients at L0 and L1 scales.
- Add terrain-lift proxy to L1 vertical velocity and gust generation.
- Add diagnostics for terrain lift and lee shear.

Acceptance criteria:

- wind accelerates through valleys,
- wind weakens or deflects upwind of steep terrain,
- lee side has increased turbulence intensity,
- L1 boundary conditions are not globally parallel over complex terrain.

Code targets:

- `WorldgenSeedTerrainProvider.java`
- `BackgroundMetGrid.java`
- `MesoscaleGrid.java`

## Phase 4: Gust And Turbulence Field

Goal: provide flight-relevant unsteady wind without running expensive turbulence-resolving CFD everywhere.

Mechanisms:

- deterministic spatiotemporal correlated noise,
- Ornstein-Uhlenbeck gust process per coarse cell,
- turbulence intensity from roughness, shear, terrain lift, and convection,
- vertical gust proxy from buoyancy and terrain lift.

Recommended output:

- `turbulenceIntensity`
- `gustX/Z`
- `gustY`
- `gustTimeScaleSeconds`
- optional `eddyDissipationRateProxy`

Implementation:

- Add gust state to `BackgroundMetGrid` or a dedicated `GustField`.
- Use world seed plus cell coordinate for deterministic phase.
- Evolve gust state slowly enough to be coherent for vehicles.
- Downscale gusts from L0 to L1 using AGL layer and stability.

Acceptance criteria:

- gusts are continuous while flying across cells,
- gust amplitude increases over rough terrain and unstable convection,
- gusts do not create frequent nonphysical instant flips,
- dumps can explain why a region is turbulent.

## Phase 5: Flight Sampling API

Goal: expose a stable API for other mods.

API behavior:

- inside active L2 brick: sample L2 resolved field,
- outside active L2: sample L1 ABL field,
- outside L1 coverage: sample L0 derived field,
- always return metadata about source and freshness.

Recommended Java contract:

```java
record AeroWindSample(
    double x,
    double y,
    double z,
    float velocityX,
    float velocityY,
    float velocityZ,
    float pressure,
    float temperatureKelvin,
    float humidity,
    float turbulenceIntensity,
    float gustX,
    float gustY,
    float gustZ,
    float shearDuDy,
    float shearDwDy,
    Level level,
    long frameId,
    int ageTicks
) {}
```

Commands and diagnostics:

- `/aero sample_wind <x> <y> <z>`
- `/aero dump_l0`
- `/aero dump_l1`
- optional `/aero wind_profile <x> <z>`

Acceptance criteria:

- a flight mod can sample multiple aircraft points every tick,
- sampling is allocation-free or near allocation-free on the hot path,
- sample results are stable under missing L2 data,
- output includes enough metadata to debug source changes.

Code targets:

- `AeroServerRuntime.java`
- public runtime API class or service
- optional Fabric event/API package

## Phase 6: Validation Scenarios

Required scenarios:

- flat terrain, neutral stability, weak pressure gradient
- flat terrain, strong pressure gradient
- stable night boundary layer
- unstable daytime boundary layer
- ridge crossing
- valley channeling
- low pressure system
- high pressure system
- thunderstorm/convective gust region

For each scenario, record:

- L0 pressure and geostrophic wind
- L1 vertical wind profile
- shear values
- turbulence intensity
- sampled aircraft-point winds

Minimum validation tools:

- JSON dumps
- CSV profile export
- visualized vector field
- in-game `/aero sample_wind`

## Phase 7: Gameplay Tuning

Goal: keep the model physically motivated but playable.

Tuning rules:

- cap extreme gusts before passing them to flight vehicles,
- expose config for global wind strength and turbulence strength,
- make calm periods common enough to be noticeable,
- avoid making all storms unflyable,
- keep deterministic behavior for multiplayer servers.

Suggested config:

- `wind.global_strength`
- `wind.shear_strength`
- `wind.gust_strength`
- `wind.turbulence_strength`
- `wind.terrain_effect_strength`
- `wind.calm_period_strength`

## Non-goals

These are not required for the first usable wind-shear release:

- full compressible atmospheric solver,
- full cloud microphysics,
- full NWP-style data assimilation,
- per-block obstacle resolving at L0/L1,
- local aircraft wake interaction with world weather,
- offline CFD for aircraft aerodynamic coefficients.

## Priority Order

1. L1 ABL vertical shear profile.
2. L0 pressure/geopotential field and geostrophic wind derivation.
3. L0/L1 terrain-induced wind effects.
4. Gust and turbulence field.
5. Public flight sampling API.
6. Validation scenarios and dumps.
7. Gameplay tuning and config.

## Release Gates

The wind-shear feature should not be considered release-ready until:

- `/aero sample_wind` returns L2/L1/L0 source metadata,
- L1 vertical profiles are dumpable and explainable,
- pressure-derived L0 wind is visible in dumps,
- calm periods can occur without turning the whole system off,
- a test aircraft can sample multiple points and observe meaningful differential wind,
- server tick cost remains bounded with multiple players.

