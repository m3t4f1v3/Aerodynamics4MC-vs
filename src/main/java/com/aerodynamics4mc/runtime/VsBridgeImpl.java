package com.aerodynamics4mc.runtime;

import com.aerodynamics4mc.api.AeroWindSample;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.primitives.AABBic;
import org.valkyrienskies.core.api.attachment.AttachmentRegistration;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.api.ships.properties.IShipActiveChunksSet;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;

/**
 * The half of the VS bridge that actually imports VS / joml types. Loaded only
 * after {@link VsBridge#available()} confirms VS is on the classpath.
 *
 * Responsibilities:
 *
 *  * Keep the wind field "ship-aware". Every tick we mark all section snapshots
 *    overlapping each ship's expanded worldspace AABB as transient ship-overlays
 *    via {@link AeroServerRuntime#notifyShipOverlaySection}. That re-runs
 *    {@link com.aerodynamics4mc.runtime.AeroServerRuntime#populateMirrorSectionSnapshot}
 *    (which calls {@link VsBridge#augmentSectionWithShipObstacles}), so the
 *    obstacle field tracks the ship's current orientation and position even
 *    while it moves.
 *
 *  * Compute aerodynamic forces by surface integration, not bluff-body drag.
 *    For every exposed face of every solid ship-block we sample the wind field
 *    at the worldspace face center and accumulate three contributions:
 *      - Pressure: F_p = -p · A · n̂  (the LBM pressure perturbation pushes on
 *        the face; high pressure on the windward side, low/suction on the lee).
 *      - Stagnation: F_s = -ρ · v_n · |v_n| · A · n̂ for v_n &lt; 0 (Newtonian
 *        impact; only oncoming flow contributes).
 *      - Skin friction: F_f = ½ρCf · |v_t| · v_t · A in the tangential direction.
 *    Forces and r×F torques are accumulated with r measured from the ship's
 *    worldspace COM, then handed off to {@link AeroVsForcesAttachment} which
 *    applies them on the physics thread via {@code applyWorldForce/Torque}.
 */
final class VsBridgeImpl {
    private static final double AIR_DENSITY_KG_PER_M3 = 1.225;
    private static final double SKIN_FRICTION_COEFFICIENT = 0.01;
    private static final double FACE_AREA_M2 = 1.0;
    private static final double MAX_TOTAL_FORCE_NEWTONS = 5.0e7;
    private static final double MAX_TOTAL_TORQUE_NEWTON_METERS = 5.0e8;
    /** Pad the ship's worldspace AABB by this many blocks when marking sections dirty,
     * so sections the ship is about to enter or just left also get refreshed. */
    private static final int OVERLAY_SECTION_MARGIN_BLOCKS = 16;

    private VsBridgeImpl() {
    }

    static void registerAttachment() {
        AttachmentRegistration.Builder<AeroVsForcesAttachment> builder =
            ValkyrienSkiesMod.getVsCore().newAttachmentRegistrationBuilder(AeroVsForcesAttachment.class);
        ValkyrienSkiesMod.getVsCore().registerAttachment(builder.build());
    }

    static void augmentSectionWithShipObstacles(
        ServerLevel level,
        BlockPos sectionOrigin,
        float[] obstacle,
        int sectionSize
    ) {
        AABB sectionAabb = new AABB(
            sectionOrigin.getX(),
            sectionOrigin.getY(),
            sectionOrigin.getZ(),
            sectionOrigin.getX() + sectionSize,
            sectionOrigin.getY() + sectionSize,
            sectionOrigin.getZ() + sectionSize
        );
        Iterable<Ship> ships = VSGameUtilsKt.getShipsIntersecting(level, sectionAabb);
        BlockPos.MutableBlockPos shipPos = new BlockPos.MutableBlockPos();
        Vector3d cellWorld = new Vector3d();
        Vector3d cellShip = new Vector3d();
        for (Ship ship : ships) {
            Matrix4dc worldToShip = ship.getWorldToShip();
            double minX = Math.max(sectionAabb.minX, ship.getWorldAABB().minX());
            double minY = Math.max(sectionAabb.minY, ship.getWorldAABB().minY());
            double minZ = Math.max(sectionAabb.minZ, ship.getWorldAABB().minZ());
            double maxX = Math.min(sectionAabb.maxX, ship.getWorldAABB().maxX());
            double maxY = Math.min(sectionAabb.maxY, ship.getWorldAABB().maxY());
            double maxZ = Math.min(sectionAabb.maxZ, ship.getWorldAABB().maxZ());
            int xMin = Math.max(0, (int) Math.floor(minX) - sectionOrigin.getX());
            int yMin = Math.max(0, (int) Math.floor(minY) - sectionOrigin.getY());
            int zMin = Math.max(0, (int) Math.floor(minZ) - sectionOrigin.getZ());
            int xMax = Math.min(sectionSize - 1, (int) Math.ceil(maxX) - sectionOrigin.getX());
            int yMax = Math.min(sectionSize - 1, (int) Math.ceil(maxY) - sectionOrigin.getY());
            int zMax = Math.min(sectionSize - 1, (int) Math.ceil(maxZ) - sectionOrigin.getZ());
            if (xMin > xMax || yMin > yMax || zMin > zMax) {
                continue;
            }
            for (int x = xMin; x <= xMax; x++) {
                for (int y = yMin; y <= yMax; y++) {
                    for (int z = zMin; z <= zMax; z++) {
                        int cell = (y * sectionSize + z) * sectionSize + x;
                        if (obstacle[cell] >= 0.5f) {
                            continue;
                        }
                        cellWorld.set(
                            sectionOrigin.getX() + x + 0.5,
                            sectionOrigin.getY() + y + 0.5,
                            sectionOrigin.getZ() + z + 0.5
                        );
                        worldToShip.transformPosition(cellWorld, cellShip);
                        shipPos.set(cellShip.x, cellShip.y, cellShip.z);
                        BlockState shipState = level.getBlockState(shipPos);
                        if (shipState.isAir()) {
                            continue;
                        }
                        VoxelShape shape = shipState.getCollisionShape(level, shipPos);
                        if (shape.isEmpty()) {
                            continue;
                        }
                        obstacle[cell] = 1.0f;
                    }
                }
            }
        }
    }

    static void tickShipForces(MinecraftServer server, VsBridge.WindSampler sampler, long gameTick) {
        for (ServerLevel level : server.getAllLevels()) {
            String levelDimId = VSGameUtilsKt.getDimensionId(level);
            for (Ship ship : VSGameUtilsKt.getAllShips(level)) {
                if (!(ship instanceof LoadedServerShip loaded)) {
                    continue;
                }
                if (!levelDimId.equals(ship.getChunkClaimDimension())) {
                    continue;
                }
                refreshShipObstacleSections(level, loaded);
                applyWindForceToShip(level, loaded, sampler, gameTick);
            }
        }
    }

    /** Mark every section the ship currently overlaps (with margin) for live rebuild. */
    private static void refreshShipObstacleSections(ServerLevel level, LoadedServerShip ship) {
        var aabb = ship.getWorldAABB();
        int sectionSize = 16;
        int minX = Math.floorDiv((int) Math.floor(aabb.minX()) - OVERLAY_SECTION_MARGIN_BLOCKS, sectionSize) * sectionSize;
        int minY = Math.floorDiv((int) Math.floor(aabb.minY()) - OVERLAY_SECTION_MARGIN_BLOCKS, sectionSize) * sectionSize;
        int minZ = Math.floorDiv((int) Math.floor(aabb.minZ()) - OVERLAY_SECTION_MARGIN_BLOCKS, sectionSize) * sectionSize;
        int maxX = Math.floorDiv((int) Math.floor(aabb.maxX()) + OVERLAY_SECTION_MARGIN_BLOCKS, sectionSize) * sectionSize;
        int maxY = Math.floorDiv((int) Math.floor(aabb.maxY()) + OVERLAY_SECTION_MARGIN_BLOCKS, sectionSize) * sectionSize;
        int maxZ = Math.floorDiv((int) Math.floor(aabb.maxZ()) + OVERLAY_SECTION_MARGIN_BLOCKS, sectionSize) * sectionSize;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int sx = minX; sx <= maxX; sx += sectionSize) {
            for (int sy = minY; sy <= maxY; sy += sectionSize) {
                for (int sz = minZ; sz <= maxZ; sz += sectionSize) {
                    cursor.set(sx, sy, sz);
                    AeroServerRuntime.notifyShipOverlaySection(level, cursor.immutable());
                }
            }
        }
    }

    private static void applyWindForceToShip(
        ServerLevel level,
        LoadedServerShip ship,
        VsBridge.WindSampler sampler,
        long gameTick
    ) {
        AeroVsForcesAttachment attachment = ship.getOrPutAttachment(
            AeroVsForcesAttachment.class,
            AeroVsForcesAttachment::new
        );
        if (attachment == null) {
            return;
        }
        AABBic shipAABB = ship.getShipAABB();
        if (shipAABB == null) {
            attachment.setWorldForce(0, 0, 0, 0, 0, 0, gameTick);
            return;
        }

        Matrix4dc shipToWorld = ship.getShipToWorld();
        Vector3dc comWorld = ship.getKinematics().getPosition();
        Vector3dc shipLinVel = ship.getKinematics().getVelocity();
        Vector3dc shipAngVel = ship.getKinematics().getAngularVelocity();

        double sumFx = 0, sumFy = 0, sumFz = 0;
        double sumTx = 0, sumTy = 0, sumTz = 0;

        Vector3d shipFaceCenter = new Vector3d();
        Vector3d worldFaceCenter = new Vector3d();
        Vector3d worldNormal = new Vector3d();
        BlockPos.MutableBlockPos shipBlockPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos shipNeighborPos = new BlockPos.MutableBlockPos();
        IShipActiveChunksSet activeChunks = ship.getActiveChunksSet();

        int yMin = shipAABB.minY();
        int yMax = shipAABB.maxY();
        int xMin = shipAABB.minX();
        int xMax = shipAABB.maxX();
        int zMin = shipAABB.minZ();
        int zMax = shipAABB.maxZ();

        // Iterate only chunks that actually contain ship blocks.
        java.util.List<long[]> chunkBuffer = new java.util.ArrayList<>(activeChunks.getSize());
        activeChunks.forEach((cx, cz) -> chunkBuffer.add(new long[] {cx, cz}));

        for (long[] chunk : chunkBuffer) {
            int cx = (int) chunk[0];
            int cz = (int) chunk[1];
            int chunkBlockMinX = Math.max(xMin, cx << 4);
            int chunkBlockMinZ = Math.max(zMin, cz << 4);
            int chunkBlockMaxX = Math.min(xMax, (cx << 4) + 15);
            int chunkBlockMaxZ = Math.min(zMax, (cz << 4) + 15);
            for (int y = yMin; y <= yMax; y++) {
                for (int x = chunkBlockMinX; x <= chunkBlockMaxX; x++) {
                    for (int z = chunkBlockMinZ; z <= chunkBlockMaxZ; z++) {
                        shipBlockPos.set(x, y, z);
                        BlockState state = level.getBlockState(shipBlockPos);
                        if (state.isAir()) {
                            continue;
                        }
                        if (state.getCollisionShape(level, shipBlockPos).isEmpty()) {
                            continue;
                        }
                        for (Direction dir : Direction.values()) {
                            shipNeighborPos.set(x + dir.getStepX(), y + dir.getStepY(), z + dir.getStepZ());
                            BlockState neighbor = level.getBlockState(shipNeighborPos);
                            if (!neighbor.isAir() && !neighbor.getCollisionShape(level, shipNeighborPos).isEmpty()) {
                                continue; // interior face, no aerodynamic exposure
                            }
                            // Worldspace face center: ship-space block center offset by 0.5 along the outward normal.
                            shipFaceCenter.set(x + 0.5 + 0.5 * dir.getStepX(),
                                               y + 0.5 + 0.5 * dir.getStepY(),
                                               z + 0.5 + 0.5 * dir.getStepZ());
                            shipToWorld.transformPosition(shipFaceCenter, worldFaceCenter);
                            // Worldspace face normal: rotate the shipspace unit normal by shipToWorld's rotation.
                            worldNormal.set(dir.getStepX(), dir.getStepY(), dir.getStepZ());
                            shipToWorld.transformDirection(worldNormal);
                            double normLen = worldNormal.length();
                            if (normLen < 1.0e-9) {
                                continue;
                            }
                            worldNormal.div(normLen);

                            // Sample the wind field at the worldspace face center.
                            Vec3 sampleAt = new Vec3(worldFaceCenter.x, worldFaceCenter.y, worldFaceCenter.z);
                            AeroWindSample sample = VsBridge.sample(sampler, level, sampleAt);
                            Vec3 wind = sample.effectiveVelocity();
                            double pressure = sample.pressure();

                            // Local ship velocity at this face: v_lin + ω × (r_face - r_com).
                            double rX = worldFaceCenter.x - comWorld.x();
                            double rY = worldFaceCenter.y - comWorld.y();
                            double rZ = worldFaceCenter.z - comWorld.z();
                            double shipVelX = shipLinVel.x() + (shipAngVel.y() * rZ - shipAngVel.z() * rY);
                            double shipVelY = shipLinVel.y() + (shipAngVel.z() * rX - shipAngVel.x() * rZ);
                            double shipVelZ = shipLinVel.z() + (shipAngVel.x() * rY - shipAngVel.y() * rX);
                            double vRelX = wind.x - shipVelX;
                            double vRelY = wind.y - shipVelY;
                            double vRelZ = wind.z - shipVelZ;
                            double vN = vRelX * worldNormal.x + vRelY * worldNormal.y + vRelZ * worldNormal.z;
                            double vTx = vRelX - vN * worldNormal.x;
                            double vTy = vRelY - vN * worldNormal.y;
                            double vTz = vRelZ - vN * worldNormal.z;
                            double vTmag = Math.sqrt(vTx * vTx + vTy * vTy + vTz * vTz);

                            // 1. Pressure perturbation from the LBM field.
                            double pCoeff = -pressure * FACE_AREA_M2;
                            double dFx = pCoeff * worldNormal.x;
                            double dFy = pCoeff * worldNormal.y;
                            double dFz = pCoeff * worldNormal.z;

                            // 2. Newtonian-impact stagnation contribution for oncoming flow (vN < 0).
                            if (vN < 0.0) {
                                double stagCoeff = -AIR_DENSITY_KG_PER_M3 * vN * Math.abs(vN) * FACE_AREA_M2;
                                dFx += stagCoeff * worldNormal.x;
                                dFy += stagCoeff * worldNormal.y;
                                dFz += stagCoeff * worldNormal.z;
                            }

                            // 3. Skin friction along the tangential wind component.
                            if (vTmag > 1.0e-6) {
                                double frictionCoeff = 0.5 * AIR_DENSITY_KG_PER_M3
                                    * SKIN_FRICTION_COEFFICIENT * vTmag * FACE_AREA_M2;
                                dFx += frictionCoeff * vTx;
                                dFy += frictionCoeff * vTy;
                                dFz += frictionCoeff * vTz;
                            }

                            sumFx += dFx;
                            sumFy += dFy;
                            sumFz += dFz;
                            sumTx += rY * dFz - rZ * dFy;
                            sumTy += rZ * dFx - rX * dFz;
                            sumTz += rX * dFy - rY * dFx;
                        }
                    }
                }
            }
        }

        attachment.setWorldForce(
            clamp(sumFx, MAX_TOTAL_FORCE_NEWTONS),
            clamp(sumFy, MAX_TOTAL_FORCE_NEWTONS),
            clamp(sumFz, MAX_TOTAL_FORCE_NEWTONS),
            clamp(sumTx, MAX_TOTAL_TORQUE_NEWTON_METERS),
            clamp(sumTy, MAX_TOTAL_TORQUE_NEWTON_METERS),
            clamp(sumTz, MAX_TOTAL_TORQUE_NEWTON_METERS),
            gameTick
        );
    }

    private static double clamp(double v, double limit) {
        if (v > limit) return limit;
        if (v < -limit) return -limit;
        return v;
    }
}
