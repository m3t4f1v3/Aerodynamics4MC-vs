# Player-Facing Wind Design

## Purpose

This document defines the wind system from the player's point of view.

The mod should not be judged only by whether it contains `L0`, `L1`, `L2`, LBM, or Navier-Stokes-inspired simulation. It should be judged by whether players can perceive, learn, and control air as a world system.

The technical layers exist to support player-facing behavior:

- `L0`: weather-scale wind and pressure systems.
- `L1`: terrain-scale wind shaped by mountains, valleys, water, roughness, and boundary-layer effects.
- `L2`: local voxel-scale flow shaped by player buildings, devices, heat sources, and obstacles.

Players should not need to understand these names. They should experience them as:

- weather has character,
- terrain shapes wind,
- buildings and machines change nearby air.

## Core Design Statement

Air should become a playable environmental system.

The player should be able to:

- observe wind,
- predict wind,
- exploit wind,
- modify wind locally,
- build systems that depend on wind.

The goal is not full scientific CFD everywhere. The goal is a coherent game system where physical reasoning usually works.

## Player Mental Model

Players should gradually learn three rules:

1. Weather creates the large wind pattern.
2. Terrain bends, blocks, accelerates, and destabilizes that wind.
3. Player-built structures and devices affect air only locally.

This mental model should hold even when the implementation uses approximations.

## Layer Translation

### L0: Weather Character

Player-facing meaning:

- The world has a current wind regime.
- Wind can be calm, steady, gusty, stormy, rotating, or changing.
- Weather events have spatial structure, not just global random values.

Player should notice:

- trees and particles lean in the same broad direction,
- clouds, rain, smoke, and foliage imply large-scale wind,
- flying long distances has tailwind, headwind, or crosswind,
- storms produce stronger and less predictable wind,
- calm periods exist.

Player control:

- Mostly indirect.
- Players cannot directly edit L0, but can plan around it.
- Server/admin tools may expose weather controls for creative/test worlds.

Technical implication:

- L0 must be stable, coherent, and slow-changing.
- L0 must produce visible variation over time.
- L0 should support pressure systems, calm zones, broad rotation, and storm activity.
- L0 should be sampled by gameplay systems that do not need local obstacle detail.

Primary consumers:

- foliage and shader wind,
- clouds, rain, fog, ambient particles,
- flight vehicles at cruise scale,
- server-side coarse wind API,
- L1 forcing.

### L1: Terrain Wind

Player-facing meaning:

- Local geography changes the weather wind.
- Mountains, ridges, valleys, cliffs, forests, oceans, and plains have different wind behavior.

Player should notice:

- wind accelerates through passes and valleys,
- wind weakens behind ridges,
- exposed high terrain is windier,
- near-ground wind is weaker than aloft wind,
- flying across ridges can create lift, sink, shear, or turbulence,
- water/land and rough/smooth terrain feel different.

Player control:

- Players can choose where to build or fly.
- Players can use terrain for windmills, gliders, airships, or shelter.
- Large terraforming may eventually influence L1, but this should not be required for the first player-facing version.

Technical implication:

- L1 is the main layer for flight feel and terrain gameplay.
- L1 must expose vertical wind shear, turbulence intensity, gusts, and source metadata.
- L1 must be cheap enough to run server-side for multiple players.
- L1 should not require per-block building detail.

Primary consumers:

- aircraft and airship mods,
- wind turbines and large machines,
- shader and particle effects near terrain,
- player wind sampling tools,
- L2 boundary and initial conditions.

### L2: Local Voxel Air

Player-facing meaning:

- Player-built structures and local machines change nearby air.
- This is the layer where "my blocks changed the flow" should be true.

Player should notice:

- walls create wind shadows,
- windows and doors allow ventilation,
- chimneys, shafts, and tunnels guide flow,
- fans and heat sources create local motion,
- smoke/steam/particles follow local air,
- debug visualization reveals local flow lines.

Player control:

- Direct.
- Players place blocks, open holes, add fans, add heat sources, and build ducts.
- L2 should respond to local construction quickly enough to feel connected.

Technical implication:

- L2 should be activated by local consumers, not always by default.
- L2 should not be required for general weather or aircraft cruise flight.
- L2 should be client-local when used for visualization and local effects.
- Server-authoritative gameplay should not depend on untrusted client-only L2 unless a validation path exists.

Primary consumers:

- visual CFD overlay,
- smoke/steam/local particles,
- fans and local ventilation devices,
- building comfort or airflow gameplay,
- local wind instruments,
- optional engineering/debug tools.

## What Must Be Felt Without Debug Visualization

Debug visualization is useful, but it cannot be the main product.

The player should feel the wind through normal gameplay:

- foliage direction and intensity,
- smoke and campfire plume drift,
- rain and snow slant,
- dust, leaves, ash, steam, and mist particles,
- flags, banners, wind socks, wind vanes,
- windmill torque and turbine output,
- aircraft drift, lift disturbance, and gust response,
- sound cues in strong wind,
- movement or camera effects in extreme weather.

The overlay should explain the system, not replace the system.

## Observation Tools

The mod should provide player-facing instruments:

- Wind vane: shows horizontal direction.
- Wind sock: shows direction and approximate speed.
- Anemometer: numeric speed and gust strength.
- Barometer: pressure tendency and storm indicator.
- Smoke stick: low-tech local flow visualization.
- Engineering goggles: optional L2 vectors/streamlines.
- Weather station block: records recent L0/L1 wind and pressure.

These tools let players learn the rules without opening debug commands.

## Control Surfaces

Player control should be divided by scale.

L0 control:

- Mostly none in survival.
- Admin/weather commands only.
- Future machines may slightly influence weather only as late-game fiction, not baseline.

L1 control:

- Build in specific terrain.
- Place large wind machines in exposed terrain.
- Use terrain shelters and ridges.
- Potential future large-scale terraforming effects.

L2 control:

- Place/remove blocks.
- Open/close vents.
- Build ducts, chimneys, rooms, tunnels.
- Place fans, blowers, turbines, heat sources.
- Use smoke/steam to inspect flow.

## Gameplay Loops

### Weather Reading

Player observes wind direction, pressure, clouds, and storm indicators.

Useful outcomes:

- choose flight route,
- decide whether to launch aircraft,
- orient windmills,
- prepare for storm effects.

Required layers:

- L0 primarily,
- L1 for local correction.

### Terrain Exploitation

Player learns where terrain creates useful or dangerous wind.

Useful outcomes:

- build turbines at passes or ridges,
- fly gliders along slopes,
- avoid turbulent lee sides,
- build sheltered bases.

Required layers:

- L1 primarily,
- L0 as forcing.

### Building Ventilation

Player designs rooms, chimneys, ducts, or machines that move air.

Useful outcomes:

- smoke clears or accumulates,
- heat vents upward,
- machines require airflow,
- buildings can be made windy or sheltered.

Required layers:

- L2 for local flow,
- L1/L0 as boundary.

### Engineering Debug

Player uses overlays or tools to understand why a design works or fails.

Useful outcomes:

- see local recirculation,
- see wind shadow,
- tune openings and fan placement,
- validate turbine placement.

Required layers:

- L2 visualization,
- L1 diagnostic sampling.

## Sampling Policy

Consumers should not all sample the same layer.

Recommended policy:

- Ambient visuals: sample L0/L1.
- Foliage and shader wind: sample L1, fallback L0.
- Aircraft cruise flight: sample L1, fallback L0.
- Aircraft local aerodynamic solver: separate vehicle-relative solver, not world L2.
- Particles near buildings: sample L2 if active, otherwise L1.
- Wind turbines and large outdoor machines: sample L1.
- Fans, ducts, chimneys, room ventilation: sample L2.
- Server-authoritative gameplay: prefer L1/L0 unless L2 is server-trusted.

L2 should be opt-in by consumer need.

## Feedback Latency Targets

Different phenomena can have different response times.

Immediate response:

- local block placement affecting L2 visualization,
- fan/vent activation,
- smoke direction near active local flow.

Fast response:

- gust changes,
- L1 boundary updates to L2,
- local particle drift.

Slow response:

- L0 weather changes,
- pressure systems,
- storm lifecycle,
- temperature and humidity evolution.

This avoids forcing every layer to update at visual-frame speed.

## Design Constraints

### Do Not Make L2 Responsible For Weather

L2 is not the weather system.

It should not be required for:

- ordinary outdoor wind,
- long-range flight,
- server-wide weather,
- global particle motion.

### Do Not Hide Everything Behind Debug Lines

If the only obvious feature is a vector overlay, the design has failed.

The overlay should be a teaching and engineering tool.

### Prefer Believable Control Over Perfect Physics

Navier-Stokes flow is chaotic. The player needs understandable cause and effect.

If exact physics makes the result noisy, expensive, or hard to reason about, prefer a stable reduced model with clear feedback.

### Keep Layer Responsibilities Separate

L0:

- weather state,
- pressure systems,
- storm activity,
- broad wind regime.

L1:

- terrain shaping,
- boundary layer,
- wind shear,
- turbulence/gust diagnostics.

L2:

- local voxel obstruction,
- local devices,
- building-scale airflow.

## Feature Priorities

### Highest Value

- Wind vane / wind sock / anemometer tools.
- Smoke/steam particles sampling wind.
- L1 terrain wind made visibly different across terrain.
- L1 gust/turbulence values exposed to flight/foliage consumers.
- L2 only when local visual or building-airflow consumers exist.

### Medium Value

- Weather station block with pressure and wind history.
- Ridge lift and lee turbulence for aircraft.
- Better smoke plume behavior for chimneys and campfires.
- Windmill/turbine output based on L1 speed and turbulence.
- Engineering goggles for L2 vectors and streamlines.

### Lower Value Until Gameplay Needs It

- More L2 resolution.
- More MLUPS.
- Full L0 CFD.
- Fully dynamic high-resolution vehicle CFD.
- Precise interior ventilation physics.

## Near-Term Roadmap

### Step 1: Define Public Wind Sample Contract

Expose stable sample data:

- velocity,
- pressure anomaly,
- temperature,
- humidity,
- turbulence intensity,
- gust velocity,
- shear,
- source level,
- freshness/confidence.

This is the contract other mods should integrate against.

### Step 2: Add Player Instruments

Implement simple blocks/items first:

- wind vane,
- anemometer,
- smoke stick or smoke particle emitter,
- weather station.

These make L0/L1 immediately visible.

### Step 3: Make L1 Terrain Effects Obvious

Improve and tune:

- ridge lift,
- valley channeling,
- lee turbulence,
- roughness drag,
- high-altitude wind increase.

The goal is not maximum realism. The goal is that players can build or fly differently because of terrain.

### Step 4: Connect Common Effects To Sampling API

Connect:

- particles,
- smoke,
- foliage bridge,
- wind machines,
- aircraft integration points.

This gives the simulation visible consequences.

### Step 5: Keep L2 Local And Optional

Use L2 for:

- local flow overlay,
- smoke/vent/chimney/fan behavior,
- building-scale engineering.

Do not require L2 for every player at all times.

## Success Criteria

The system is working when:

- a player can tell wind direction without opening debug overlay,
- a player can find better wind terrain for a turbine or aircraft,
- a player can use smoke to understand a building airflow problem,
- a player can predict that ridge/valley/building shape matters,
- another mod can sample useful wind without knowing internal layer details,
- disabling L2 still leaves a meaningful weather and terrain wind system.

## Non-Goals

Not required for the first player-facing release:

- full global Navier-Stokes weather,
- scientifically exact atmospheric simulation,
- high-resolution dynamic vehicle CFD,
- server-authoritative client L2 feedback,
- perfect indoor air quality simulation,
- forcing every visual particle to use L2.

## Architectural Consequence

Future work should be prioritized by player feedback, not solver sophistication.

Before adding a physics feature, ask:

- What does the player see or feel?
- Can the player predict it?
- Can the player control or exploit it?
- Which layer is responsible?
- Which consumer uses it?
- How do we debug it in-game?

If these questions do not have clear answers, the feature should not be first priority.
