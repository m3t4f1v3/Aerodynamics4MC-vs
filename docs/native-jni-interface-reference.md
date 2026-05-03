# Native JNI Interface Reference

本文档整理了 `fabric-mod/native` 中 C++ 求解器暴露给 Java 的所有接口。

## 概述

Native 求解器通过 JNI (Java Native Interface) 暴露了三个主要的 API 层：

1. **Simulation Bridge API** - 高级仿真服务接口（推荐用于新功能）
2. **Mesoscale Bridge API** - 中尺度气象模拟接口
3. **Legacy LBM Bridge API** - 传统 LBM 求解器接口（已废弃，仅用于兼容）

---

## 1. Simulation Bridge API

**Java 类**: `com.aerodynamics4mc.runtime.NativeSimulationBridge`  
**C API 头文件**: `fabric-mod/native/include/aero_lbm_capi.h`

这是**推荐使用的主接口**，提供完整的多区域、多世界仿真管理。

### 1.1 服务生命周期

#### `createService() -> long`
创建一个仿真服务实例。

**返回**: 服务句柄 (serviceKey)，失败返回 0

**C API**: `aero_lbm_simulation_create_service`

---

#### `releaseService(long serviceKey)`
释放仿真服务及其所有资源。

**C API**: `aero_lbm_simulation_release_service`

---

### 1.2 区域 (Region) 管理

Region 是一个独立的 CFD 求解区域（通常 64³ 体素）。

#### `uploadStaticRegion(...)`
上传区域的静态几何数据（障碍物、表面类型、热源等）。

**参数**:
- `serviceKey`: 服务句柄
- `regionKey`: 区域唯一标识
- `nx, ny, nz`: 网格尺寸
- `obstacle[]`: 障碍物掩码 (0=流体, 1=固体)
- `surfaceKind[]`: 表面材质类型
- `openFaceMask[]`: 开放面掩码
- `emitterPowerWatts[]`: 热源功率 (瓦特)
- `faceSkyExposure[]`: 面天空暴露度
- `faceDirectExposure[]`: 面直接暴露度

**返回**: 成功返回 true

**C API**: `aero_lbm_simulation_upload_static_region`

---

#### `activateRegion(long serviceKey, long regionKey, int nx, int ny, int nz) -> boolean`
激活一个区域，分配 GPU/CPU 资源并初始化求解器上下文。

**C API**: `aero_lbm_simulation_activate_region`

---

#### `deactivateRegion(long serviceKey, long regionKey) -> boolean`
停用区域，释放计算资源但保留几何数据。

**C API**: `aero_lbm_simulation_deactivate_region`

---

#### `hasRegion(long serviceKey, long regionKey) -> boolean`
检查区域是否存在（已上传几何数据）。

**C API**: `aero_lbm_simulation_has_region`

---

#### `isRegionReady(long serviceKey, long regionKey) -> boolean`
检查区域是否已激活且准备好求解。

**C API**: `aero_lbm_simulation_is_region_ready`

---

### 1.3 区域求解

#### `stepRegionStored(...) -> float`
执行一步 LBM 迭代（使用存储的边界条件）。

**参数**:
- `serviceKey, regionKey`: 服务和区域句柄
- `nx, ny, nz`: 网格尺寸
- `boundaryWindX/Y/Z`: 边界风速 (m/s)
- `fallbackBoundaryAirTemperatureKelvin`: 回退边界温度
- `externalFaceMask`: 外部面掩码
- `boundaryFaceResolution`: 边界面分辨率
- `boundaryWindFace[X/Y/Z][]`: 边界面风速数组
- `boundaryAirTemperatureKelvin[]`: 边界面温度数组
- `spongeThicknessCells`: 海绵层厚度
- `spongeVelocityRelaxation`: 速度松弛系数
- `spongeTemperatureRelaxation`: 温度松弛系数
- `tornadoDescriptorCount`: 龙卷风描述符数量
- `tornadoDescriptors[]`: 龙卷风参数数组

**返回**: 最大速度 (lattice units)，失败返回 NaN

**C API**: `aero_lbm_simulation_step_region_stored`

**用途**: 这是主要的求解接口，适用于实时游戏循环。

---

#### `uploadRegionForcing(...) -> boolean`
上传区域的强制项（风扇、热源）。

**参数**:
- `thermalSource[]`: 热源强度数组
- `fanMask[]`: 风扇掩码
- `fanVx/Vy/Vz[]`: 风扇速度分量

**C API**: `aero_lbm_simulation_upload_region_forcing`

---

### 1.4 状态读写

#### `getRegionFlowState(...) -> boolean`
读取区域的流场状态（速度 + 压力）。

**参数**:
- `outFlowState[]`: 输出数组，大小 = nx*ny*nz*4 (vx, vy, vz, pressure)

**C API**: `aero_lbm_simulation_get_region_flow_state`

---

#### `getRegionTemperatureState(...) -> boolean`
读取区域的温度场。

**参数**:
- `outTemperature[]`: 输出数组，大小 = nx*ny*nz

**C API**: `aero_lbm_simulation_get_region_temperature_state`

---

#### `setRegionTemperatureState(...) -> boolean`
设置区域的温度场（用于初始化或热重启）。

**C API**: `aero_lbm_simulation_set_region_temperature_state`

---

#### `sampleRegionPoint(...) -> boolean`
采样区域中单个点的流场和温度。

**参数**:
- `sampleX/Y/Z`: 采样点坐标
- `outProbeValues[]`: 输出数组，大小 = 6 (vx, vy, vz, pressure, temperature, ?)

**C API**: `aero_lbm_simulation_sample_region_point`

**用途**: 用于玩家位置的风速查询。

---

### 1.5 区域间通信

#### `exchangeRegionHalo(...) -> boolean`
在两个相邻区域之间交换边界层数据（halo exchange）。

**参数**:
- `firstRegionKey, secondRegionKey`: 两个区域的句柄
- `gridSize`: 网格尺寸
- `offsetX/Y/Z`: 第二个区域相对第一个区域的偏移

**C API**: `aero_lbm_simulation_exchange_region_halo`

**用途**: 实现多区域无缝耦合。

---

#### `exchangeRegionHaloBatch(...) -> int`
批量执行多个 halo 交换操作。

**返回**: 成功执行的交换数量

**C API**: `aero_lbm_simulation_exchange_region_halo_batch`

---

### 1.6 Brick World API（分块世界管理）

Brick World 是一个基于分块的大规模世界管理系统。

#### `ensureBrickWorldRuntime(...) -> boolean`
确保 Brick World 运行时已初始化。

**参数**:
- `worldKey`: 世界唯一标识
- `brickSize`: 砖块尺寸（通常 16 或 32）
- `dxMeters`: 空间步长 (米)
- `dtSeconds`: 时间步长 (秒)

**C API**: `aero_lbm_simulation_ensure_brick_world_runtime`

---

#### `uploadBrickWorldStaticBrick(...) -> boolean`
上传单个砖块的静态几何数据。

**参数**: 类似 `uploadStaticRegion`，但针对单个砖块

**C API**: `aero_lbm_simulation_upload_brick_world_static_brick`

---

#### `stepBrickWorldRuntime(...) -> boolean`
执行 Brick World 的多步迭代。

**参数**:
- `stepCount`: 迭代步数

**C API**: `aero_lbm_simulation_step_brick_world_runtime`

---

#### `getBrickWorldRuntimeStatus(...) -> BrickWorldRuntimeStatus`
获取 Brick World 的运行时状态。

**返回**: 包含以下字段的记录：
- `brickSize`: 砖块尺寸
- `knownBrickCount`: 已知砖块数量
- `activeHintCount`: 活跃提示数量
- `activeBrickCount`: 活跃砖块数量
- `geometryDirtyCount`: 几何脏标记数量
- `forcingDirtyCount`: 强制项脏标记数量
- `pendingReinitCount`: 待重新初始化数量
- `epoch`: 纪元计数器

**C API**: `aero_lbm_simulation_get_brick_world_runtime_status`

---

### 1.7 数据压缩

#### `compressFloatGrid3d(...) -> byte[]`
使用 ZFP 压缩 3D 浮点网格。

**参数**:
- `values[]`: 输入浮点数组
- `nx, ny, nz`: 网格尺寸
- `tolerance`: 压缩容差

**返回**: 压缩后的字节数组

**C API**: 内部实现（通过 ZFP 库）

---

#### `decompressFloatGrid3d(...) -> boolean`
解压缩 3D 浮点网格。

**C API**: 内部实现

---

### 1.8 诊断接口

#### `runtimeInfo() -> String`
获取运行时信息（OpenCL 设备、后端类型等）。

**返回**: 格式化的运行时信息字符串

**C API**: `aero_lbm_simulation_runtime_info`

---

#### `lastError() -> String`
获取最后一次错误信息。

**C API**: `aero_lbm_simulation_last_error`

---

## 2. Mesoscale Bridge API

**Java 类**: `com.aerodynamics4mc.runtime.MesoscaleNativeBridge`  
**用途**: 中尺度气象模拟（L1 层，33×33×8 网格）

### 2.1 传输系数计算

#### `deriveTransport(...) -> Transport`
根据物理参数计算 LBM 传输系数。

**参数**:
- `nx, ny, nz`: 网格尺寸
- `dxMeters`: 空间步长 (米)
- `dtSeconds`: 时间步长 (秒)
- `molecularNuMeters2PerSecond`: 分子粘度 (m²/s)
- `prandtlAir`: 空气普朗特数
- `turbulentPrandtl`: 湍流普朗特数

**返回**: `Transport` 记录，包含：
- `velocityScaleMetersPerSecond`: 速度尺度
- `molecularNuLattice`: 格子单位分子粘度
- `molecularAlphaLattice`: 格子单位热扩散率
- `molecularTauShear`: 剪切松弛时间
- `molecularTauThermal`: 热松弛时间

**C API**: `aero_lbm_mesoscale_derive_transport`

---

### 2.2 上下文管理

#### `createContext(...) -> long`
创建中尺度模拟上下文。

**返回**: 上下文句柄

**C API**: `aero_lbm_mesoscale_create_context`

---

#### `stepContext(...) -> boolean`
执行一步中尺度模拟。

**参数**:
- `contextKey`: 上下文句柄
- `forcing[]`: 强制项数组（24 通道）
- `outState[]`: 输出状态数组（5 通道：vx, vy, vz, pressure, temperature）

**C API**: `aero_lbm_mesoscale_step_context`

---

#### `releaseContext(long contextKey)`
释放中尺度上下文。

**C API**: `aero_lbm_mesoscale_release_context`

---

## 3. Legacy LBM Bridge API（已废弃）

**注意**: 这些接口已被 Simulation Bridge API 取代，仅用于向后兼容。

### 主要接口（简要列举）

- `nativeInit(int gridSize, int inputChannels, int outputChannels) -> boolean`
- `nativeStep(float[] payload, int gridSize, long contextKey, float[] output) -> boolean`
- `nativeStepDirect(ByteBuffer payload, int gridSize, long contextKey, float[] output) -> boolean`
- `nativeReleaseContext(long contextKey)`
- `nativeShutdown()`
- `nativeRuntimeInfo() -> String`
- `nativeLastError() -> String`
- `nativeTimingInfo() -> String`

---

## 4. 数据结构定义

### 4.1 常量

```java
// NativeSimulationBridge
FLOW_STATE_CHANNELS = 4           // vx, vy, vz, pressure
PACKED_ATLAS_CHANNELS = 4
PLAYER_PROBE_CHANNELS = 6         // vx, vy, vz, pressure, temperature, ?
TORNADO_DESCRIPTOR_FLOATS = 17
NESTED_FEEDBACK_MAX_BINS = 8
BRICK_RUNTIME_STATUS_FIELDS = 8
```

### 4.2 World Delta 类型

```java
WORLD_DELTA_BLOCK_CHANGED = 1
WORLD_DELTA_CHUNK_LOADED = 2
WORLD_DELTA_CHUNK_UNLOADED = 3
WORLD_DELTA_BLOCK_ENTITY_LOADED = 4
WORLD_DELTA_BLOCK_ENTITY_UNLOADED = 5
WORLD_DELTA_WORLD_UNLOADED = 6
WORLD_DELTA_FOCUS_CHANGED = 7
WORLD_DELTA_BRICK_STATIC_CELL_PATCH = 8
```

---

## 5. 典型使用流程

### 5.1 单区域求解（实时模拟）

```java
NativeSimulationBridge bridge = new NativeSimulationBridge();

// 1. 创建服务
long serviceKey = bridge.createService();

// 2. 上传静态几何
long regionKey = computeRegionKey(x, y, z);
bridge.uploadStaticRegion(
    serviceKey, regionKey, 64, 64, 64,
    obstacle, surfaceKind, openFaceMask,
    emitterPowerWatts, faceSkyExposure, faceDirectExposure
);

// 3. 激活区域
bridge.activateRegion(serviceKey, regionKey, 64, 64, 64);

// 4. 上传强制项（每帧）
bridge.uploadRegionForcing(
    serviceKey, regionKey, 64, 64, 64,
    thermalSource, fanMask, fanVx, fanVy, fanVz
);

// 5. 执行求解（每帧）
float maxSpeed = bridge.stepRegionStored(
    serviceKey, regionKey, 64, 64, 64,
    boundaryWindX, boundaryWindY, boundaryWindZ,
    ambientTemperature,
    externalFaceMask, boundaryFaceResolution,
    boundaryWindFaceX, boundaryWindFaceY, boundaryWindFaceZ,
    boundaryAirTemperatureKelvin,
    spongeThickness, spongeVelocityRelax, spongeTempRelax,
    0, null  // 无龙卷风
);

// 6. 读取结果
float[] flowState = new float[64 * 64 * 64 * 4];
bridge.getRegionFlowState(serviceKey, regionKey, 64, 64, 64, flowState);

// 7. 清理
bridge.deactivateRegion(serviceKey, regionKey);
bridge.releaseService(serviceKey);
```

### 5.2 中尺度模拟

```java
MesoscaleNativeBridge bridge = new MesoscaleNativeBridge();

// 1. 计算传输系数
Transport transport = bridge.deriveTransport(
    33, 33, 8,
    64.0f,      // dx = 64 meters
    0.05f,      // dt = 0.05 seconds
    1.5e-5f,    // molecular viscosity
    0.71f,      // Prandtl number
    0.85f       // turbulent Prandtl
);

// 2. 创建上下文
long contextKey = bridge.createContext(
    33, 33, 8, 64.0f, 0.05f, 1.5e-5f, 0.71f, 0.85f
);

// 3. 执行迭代
float[] forcing = new float[33 * 33 * 8 * 24];
float[] outState = new float[33 * 33 * 8 * 5];
bridge.stepContext(
    contextKey, 33, 33, 8,
    64.0f, 0.05f, 1.5e-5f, 0.71f, 0.85f,
    forcing, outState
);

// 4. 清理
bridge.releaseContext(contextKey);
```

---

## 6. 性能注意事项

1. **避免频繁创建/销毁上下文**: 上下文创建涉及 GPU 内存分配，开销较大
2. **批量操作**: 使用 `exchangeRegionHaloBatch` 而非多次调用单次交换
3. **直接缓冲区**: `stepRegion` 使用 `ByteBuffer.allocateDirect()` 避免 JNI 拷贝
4. **压缩传输**: 对于大规模数据传输，使用 `compressFloatGrid3d`

---

## 7. 错误处理

所有接口在失败时返回 `false` / `null` / `NaN`，可通过以下方式获取详细错误：

```java
if (!bridge.activateRegion(...)) {
    String error = bridge.lastError();
    System.err.println("Activation failed: " + error);
}
```

---

## 8. 为 Aeronautics Mod 扩展接口

### 8.1 需要新增的接口

为了支持飞行器气动参数计算，建议新增以下接口：

```java
// 新增：气动力计算接口
public class AeroCoefficients {
    float liftCoefficient;      // CL
    float dragCoefficient;      // CD
    float pitchMoment;          // CM
    float rollMoment;
    float yawMoment;
    float[] centerOfPressure;   // [x, y, z]
}

// 在 NativeSimulationBridge 中新增
public AeroCoefficients computeAeroForces(
    long serviceKey,
    long regionKey,
    int nx, int ny, int nz,
    float freestreamVelocity,   // 来流速度 (m/s)
    float angleOfAttack,        // 攻角 (度)
    float[] referencePoint      // 参考点 [x, y, z]
);
```

### 8.2 C API 扩展

在 `aero_lbm_capi.h` 中新增：

```c
typedef struct AeroLbmAeroCoefficients {
    float lift_coefficient;
    float drag_coefficient;
    float pitch_moment;
    float roll_moment;
    float yaw_moment;
    float center_of_pressure[3];
} AeroLbmAeroCoefficients;

AERO_LBM_CAPI_EXPORT int aero_lbm_simulation_compute_aero_forces(
    long long service_key,
    long long region_key,
    int nx, int ny, int nz,
    float freestream_velocity,
    float angle_of_attack,
    const float* reference_point,
    AeroLbmAeroCoefficients* out_coefficients
);
```

---

## 9. 总结

当前接口已经非常完善，支持：
- ✅ 多区域管理
- ✅ 实时求解
- ✅ 温度场耦合
- ✅ 边界条件控制
- ✅ 状态读写
- ✅ 数据压缩

**缺少的功能**（需要为 Aeronautics 扩展）：
- ❌ 气动力/力矩积分计算
- ❌ 攻角扫描批处理
- ❌ 收敛判断接口
- ❌ STL/OBJ 模型导入

建议优先实现气动力计算接口，这是 Aeronautics Mod 的核心需求。
