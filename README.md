# Aerodynamics4MC Fabric

[![Build Status](https://github.com/MozillaFiredoge/Aerodynamics4MC-Fabric/workflows/build/badge.svg)](https://github.com/MozillaFiredoge/Aerodynamics4MC-Fabric/actions)
[![Native Matrix](https://github.com/MozillaFiredoge/Aerodynamics4MC-Fabric/workflows/native-matrix/badge.svg)](https://github.com/MozillaFiredoge/Aerodynamics4MC-Fabric/actions)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

> Aerodynamics4MC is a Fabric mod that brings a multi-scale **real-time wind / weather** system to
> Minecraft. The server owns coarse weather, clients may run a local high-resolution CFD layer for
> visualization, and other mods consume wind through the public API rather than reading internal
> solver buffers.
>
> Aerodynamics4MC 是一个为 Minecraft 提供 **多尺度实时风场与天气** 的 Fabric mod。粗尺度天气由
> 服务端权威产出，客户端可选地运行高分辨率 CFD 用于可视化，外部 mod 通过公共 API 读取风场。

---

## Read this first / 先读这里

If you are going to **modify wind generation** (e.g., change how L0/L1 wind is produced, add a new
storm type, retune cyclone strength, port to a different LBM), the entry point is:

如果你要 **修改风场产生逻辑**（例如改 L0/L1 的风产生方式、加新风暴类型、重调气旋强度、换一个 LBM 模型），
请先读：

> 📘 **[`../docs/wind-system-overview.md`](../docs/wind-system-overview.md)** — single authoritative
> overview of the wind system: layers, principles, formulas, file:line citations, tunable
> constants, and a maintainer cheat-sheet. Bilingual.

That document is the single source of truth for the wind-system architecture. The rest of this README
covers project setup, public API contracts, and gameplay components.

该文档是风系统架构的唯一权威来源。本 README 余下部分覆盖：项目构建、公共 API、游戏内组件。

---

## Status / 当前状态

The mod has a **driver + three nested grids**:

| Layer | Java class | Resolution | Role | Gameplay trust |
|-------|-----------|------------|------|----------------|
| Driver | `runtime.WorldScaleDriver` | 384×384 pressure cells (256 b/cell) | Cyclones, convective clusters, tornadoes, planetary waves | Server-authoritative |
| **L0** | `runtime.BackgroundMetGrid` | 41×41 cells × 256 b/cell | World-scale pressure, geostrophic wind, terrain & roughness | Server-authoritative |
| **L1** | `runtime.MesoscaleGrid` | 33×33×8 cells (64×64×40 b/cell) | Terrain-following wind, ABL shear, gust/turbulence diagnostics, builds L2 forcing | Server-authoritative |
| **L2** | `runtime.NativeSimulationBridge` (server, **disabled**) / `client.ClientL2Solver` (default) | Server: 64³ window · Client: 32³ bricks | D3Q27 cumulant LBM + SGS + Boussinesq | **Client-local by default** |

`SERVER_AUTHORITATIVE_L2_ENABLED = false` (`AeroServerRuntime.java:123`). Server L2, capture, and
inspect-patch paths still exist for diagnostics — **do not** treat them as the release architecture.

---

## Requirements / 环境要求

| Component | Version |
|-----------|---------|
| Minecraft | 1.21.11 |
| Yarn mappings | 1.21.11+build.4 |
| Fabric Loader | 0.18.4 |
| Fabric API | 0.141.2+1.21.11 |
| Java | 21+ |

Supported native targets:

| Platform | Library |
|----------|---------|
| Windows x86_64 | `aero_lbm.dll` |
| Linux x86_64 / ARM64 | `libaero_lbm.so` |
| macOS ARM64 | `libaero_lbm.dylib` |

OpenCL is optional at runtime (GPU backend); a CPU fallback exists. Force CPU with `AERO_LBM_CPU_ONLY=1`.

---

## Build And Run / 构建与运行

From `fabric-mod/`:

```bash
# Run development client
./gradlew runClient

# Compile + bundle native resources
./gradlew compileJava compileClientJava prepareNativeResources

# Development jar
./gradlew build

# Distributable (remapped) jar
./gradlew remapJar
```

Native library (manual build):

```bash
cd native
cmake -S . -B build
cmake --build build -j
```

Multi-platform superbuild and full native details: [`native/README.md`](native/README.md).

---

## Maintainer entry points / 维护者入口

Pick the row matching what you want to change. Every row links into the wind-system overview, where
the actual file paths and constants live.

| I want to change … / 我想改… | Start here |
|---|---|
| How storms / cyclones / tornadoes spawn and evolve | [overview §2 — WorldScaleDriver](../docs/wind-system-overview.md#2-worldscaledriver--planetary-scale-weather--行星尺度天气) |
| L0 background wind law (geostrophic, terrain drag, roughness) | [overview §3 — BackgroundMetGrid](../docs/wind-system-overview.md#3-l0--backgroundmetgrid--背景天气网格) |
| L1 layered ABL / wind shear / native L2 forcing | [overview §4 — MesoscaleGrid](../docs/wind-system-overview.md#4-l1--mesoscalegrid--中尺度网格) |
| Native LBM kernel, channel layout, server-vs-client L2 | [overview §5 — Native LBM](../docs/wind-system-overview.md#5-l2--native-lbm--原生格子玻尔兹曼) |
| Public sampling API consumed by other mods | [overview §6](../docs/wind-system-overview.md#6-public-wind-api--风采样公共-api), [`docs/wind-sampling-api.md`](../docs/wind-sampling-api.md) |
| Tick cadence, refresh intervals, top-level constants | [overview §1.2](../docs/wind-system-overview.md#12-per-tick-orchestration--每-tick-编排) |
| Quick "10 most load-bearing constants" reference | [overview §8.2](../docs/wind-system-overview.md#82-the-10-load-bearing-constants--十大先动这里常量) |

---

## Runtime architecture (sketch) / 运行架构示意

```text
WorldScaleDriver (cyclones, convection, tornadoes, planetary waves)
        │
        ▼
L0 BackgroundMetGrid  41×41 / 256 b/cell
   semi-Lagrangian advect → diffuse → geostrophic adjust
   → terrain form-drag → roughness drag
        │
        ▼
L1 MesoscaleGrid  33×33×8  (64×64×40 b/cell)
   ABL shear, Ekman turn, terrain bounce-back, gusts, turbulence
   → builds 25-channel forcing for native L2
        │
        ▼
L2 Native LBM  64³ (server, disabled) / 32³ bricks (client)
   D3Q27 cumulant + SGS + Boussinesq
        │
        ▼  AeroCoarseWindPayload (32-block-cell L1 broadcast)
client AeroClientWindApi · ClientL2Solver · particle / Iris bridge
```

For the full diagram, formulas, and per-step math, see
[`../docs/wind-system-overview.md`](../docs/wind-system-overview.md).

---

## Gameplay components / 游戏内组件

The gameplay surface is intentionally thin. Most behavior should consume wind via the API.

| Component | Role |
|-----------|------|
| Fan block | Adds local momentum forcing for ducts / ventilation tests |
| Duct block | Helps shape local airflow paths |
| Wind Meter item | Right-click instrument that samples server-trusted L1/L0 wind at the player |
| Terrain & blocks | Feed world-delta and static masks into L1's L2 forcing payload |
| Particles & Iris bridge | Should sample wind through the public API path |

---

## Wind sampling API / 风采样 API

Other mods consume wind through `com.aerodynamics4mc.api`.

Server side:

```java
import com.aerodynamics4mc.api.AeroWindApi;
import com.aerodynamics4mc.api.AeroWindSample;
import com.aerodynamics4mc.api.SamplePolicy;
import net.minecraft.util.math.Vec3d;

AeroWindSample sample = AeroWindApi.sample(serverWorld, position);
AeroWindSample coarse = AeroWindApi.sample(player, position, SamplePolicy.SERVER_COARSE_ONLY);

if (coarse.isTrustedForGameplay()) {
    Vec3d wind = coarse.meanVelocity();
    float speed = coarse.speedMetersPerSecond();
}
```

Client side:

```java
import com.aerodynamics4mc.api.AeroClientWindApi;
import com.aerodynamics4mc.api.AeroWindSample;
import com.aerodynamics4mc.api.SamplePolicy;

AeroWindSample sample = AeroClientWindApi.sample(
    clientWorld, position, SamplePolicy.CLIENT_LOCAL_PREFERRED);
Vec3d visualDrift = sample.effectiveVelocity();
```

`SamplePolicy` selection guide:

| Policy | Recommended use |
|--------|-----------------|
| `SERVER_COARSE_ONLY` | Server-authoritative gameplay, vehicles, Create/Aeronautics cruise-scale wind |
| `GAMEPLAY_SERVER_ONLY` | Server-trusted L2 when available, otherwise L1/L0 |
| `SERVER_AGGREGATED_PREFERRED` | Client path that prefers server-published L2 atlas when present |
| `CLIENT_LOCAL_PREFERRED` | Client visuals, smoke, particles, local player feedback |
| `VISUAL_LOCAL_FIRST` | Visualization and engineering overlays |
| `DIAGNOSTIC_ALL_SOURCES` | Debug only |

Trust rule:

```java
if (!sample.isTrustedForGameplay()) {
    return;
}
```

Do not use client-local L2 for server-side balance, aircraft physics, or machine output unless the
result is independently validated. Full contract: [`../docs/wind-sampling-api.md`](../docs/wind-sampling-api.md).

### Recommended integration patterns / 推荐集成模式

For aircraft, airships, turbines, outdoor machines:

```java
AeroWindSample s = AeroWindApi.sample(player, pos, SamplePolicy.SERVER_COARSE_ONLY);
Vec3d wind = s.meanVelocity();
float turbulence = s.turbulenceIntensity();
```

For client-only particles (smoke / steam / ash / dust):

```java
AeroWindSample s = AeroClientWindApi.sample(world, pos, SamplePolicy.CLIENT_LOCAL_PREFERRED);
Vec3d drift = s.effectiveVelocity();
```

For engineering overlays:

```java
AeroWindSample s = AeroClientWindApi.sample(world, pos, SamplePolicy.VISUAL_LOCAL_FIRST);
if (s.level() == AeroWindSample.Level.L2) { /* show local voxel flow */ }
```

Consumers must not depend on packet formats, internal grid classes, native handles, atlas layouts, or
visualizer buffers.

---

## Commands / 指令

Server commands require permission level 2+:

| Command | Purpose |
|---------|---------|
| `/aero start` | Start server wind runtime and coarse sync |
| `/aero stop` | Stop runtime and clear active state |
| `/aero status` | Print server runtime, L0/L1, coordinator, native status |
| `/aero render` | Print current client render mode status |
| `/aero render vectors on/off` | Toggle velocity-vector rendering |
| `/aero render streamlines on/off` | Toggle streamline rendering |
| `/aero dumpdata` | Dump runtime diagnostics |
| `/aero dump_l1` | Dump L1 mesoscale snapshot |
| `/aero nested_feedback` | Print legacy nested-feedback diagnostics |
| `/aero capture_l2 …` | Deprecated diagnostic capture path |
| `/aero inspect_patch …` | Deprecated inspection / analysis path |

Client commands:

| Command | Purpose |
|---------|---------|
| `/aero_client_l2` | Print client L2 status |
| `/aero_client_l2 on` / `off` | Enable / disable experimental client-local L2 |

Client L2 is currently default-off in code. When enabled, it is for local visualization/effects and
must not be treated as server-authoritative gameplay state.

---

## Visualization / 可视化

| Mode | Description |
|------|-------------|
| Vectors | One line segment per sampled cell; direction = velocity, length & color encode speed |
| Streamlines | Seeded integration through the sampled field, colored by speed |

The CFD-style viridis line renderer is the preferred presentation; the old glyph path is legacy.

---

## Native solver C ABI / 原生求解器 C ABI

The native library also exposes a small C ABI for standalone wind-tunnel validation, independent of
Minecraft / Fabric / Create:Aeronautics internals.

| Document | Purpose |
|----------|---------|
| [`native/docs/wind_tunnel_solver_api.md`](native/docs/wind_tunnel_solver_api.md) | C ABI for rectangular wind-tunnel cases |
| [`native/docs/jni_dll_usage_zh.md`](native/docs/jni_dll_usage_zh.md) | JNA/JNI DLL usage notes (中文) |
| [`native/tools/benchmark_solver_dll.py`](native/tools/benchmark_solver_dll.py) | Python smoke benchmark |

The C ABI is a low-Mach static wind-tunnel interface: input grid size, voxel solid mask, BC settings,
optional previous macro state; output velocity and pressure-proxy fields.

---

## Known boundaries / 已知边界

| Area | Current position |
|------|------------------|
| Near-sonic aircraft | Not supported by the in-game world solver |
| Dynamic propeller geometry | Not in the public wind-tunnel API |
| Server-trusted local L2 | Not the default path |
| Multiplayer L2 feedback | Requires future validation/aggregation design |
| Analysis/capture paths | Deprecated diagnostics, not release architecture |
| Voxel-to-airfoil design workflow | Out of scope for the in-game runtime API |

For Create:Aeronautics-style integration, use `SERVER_COARSE_ONLY` for world wind plus a separate
vehicle-relative offline / proxy aerodynamic model for wing/propeller coefficients.

---

## Documentation map / 文档地图

**Wind system (start at the top):**

| Document | Purpose |
|----------|---------|
| 📘 [`../docs/wind-system-overview.md`](../docs/wind-system-overview.md) | **Authoritative wind-system overview** — read this first |
| [`../docs/wind-sampling-api.md`](../docs/wind-sampling-api.md) | Public sampling API contract (current) |
| [`../docs/world-scale-weather-design.md`](../docs/world-scale-weather-design.md) | Driver phenomenology / intent (older naming) |
| [`../docs/wind-shear-weather-roadmap.md`](../docs/wind-shear-weather-roadmap.md) | ABL / wind-shear roadmap |
| [`../docs/player-facing-wind-design.md`](../docs/player-facing-wind-design.md) | Product philosophy |

**Native solver:**

| Document | Purpose |
|----------|---------|
| [`native/README.md`](native/README.md) | Native build details |
| [`../docs/native-jni-interface-reference.md`](../docs/native-jni-interface-reference.md) | JNI / channel-layout reference |
| [`../docs/native-physics-engine-todo.md`](../docs/native-physics-engine-todo.md) | Open work in the native solver |
| [`../docs/native-authoritative-l2-runtime-design.md`](../docs/native-authoritative-l2-runtime-design.md) | Server-authoritative L2 design (*partially stale* — see overview §7) |
| [`native/docs/wind_tunnel_solver_api.md`](native/docs/wind_tunnel_solver_api.md) | Standalone C ABI |

**Integration & legacy:**

| Document | Purpose |
|----------|---------|
| [`../docs/shaderpack-wind-compat-design.md`](../docs/shaderpack-wind-compat-design.md) | Iris / BSL bridge design |
| [`../docs/on-demand-l2-prefetch-design.md`](../docs/on-demand-l2-prefetch-design.md) | Brick-prefetch design (*aspirational*) |
| [`../docs/local-air-patch-design.md`](../docs/local-air-patch-design.md) | Legacy patch concept (*deprecated*) |
| [`../docs/phase2-completion-log.md`](../docs/phase2-completion-log.md), [`../docs/phase3-completion-log.md`](../docs/phase3-completion-log.md), [`../docs/phase3-implementation-plan.md`](../docs/phase3-implementation-plan.md) | Historical milestones |

Repo-root [`../CLAUDE.md`](../CLAUDE.md) carries a high-level project summary used by tooling — its
constants table should stay aligned with the wind-system overview.

---

## License / 许可证

MIT. See repository license files for details.
