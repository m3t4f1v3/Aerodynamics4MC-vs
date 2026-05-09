package com.aerodynamics4mc.runtime;

import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.ships.PhysShip;
import org.valkyrienskies.core.api.ships.ShipPhysicsListener;
import org.valkyrienskies.core.api.world.PhysLevel;

/**
 * Per-ship attachment that injects aerodynamic forces sampled from the Aerodynamics4MC
 * wind field. Updated from the server (game) thread by {@link VsBridge#tickShipForces},
 * read from the physics thread inside {@link #physTick}.
 *
 * Attachments must be a final class with a no-arg constructor — see VsBeta requirements.
 */
public final class AeroVsForcesAttachment implements ShipPhysicsListener {
    private volatile double forceWorldX;
    private volatile double forceWorldY;
    private volatile double forceWorldZ;
    private volatile double torqueWorldX;
    private volatile double torqueWorldY;
    private volatile double torqueWorldZ;
    private volatile long lastUpdateGameTick;

    public AeroVsForcesAttachment() {
    }

    public void setWorldForce(double fx, double fy, double fz, double tx, double ty, double tz, long gameTick) {
        this.forceWorldX = fx;
        this.forceWorldY = fy;
        this.forceWorldZ = fz;
        this.torqueWorldX = tx;
        this.torqueWorldY = ty;
        this.torqueWorldZ = tz;
        this.lastUpdateGameTick = gameTick;
    }

    public long lastUpdateGameTick() {
        return lastUpdateGameTick;
    }

    @Override
    public void physTick(PhysShip physShip, PhysLevel physLevel) {
        double fx = forceWorldX;
        double fy = forceWorldY;
        double fz = forceWorldZ;
        double tx = torqueWorldX;
        double ty = torqueWorldY;
        double tz = torqueWorldZ;
        if (fx == 0.0 && fy == 0.0 && fz == 0.0 && tx == 0.0 && ty == 0.0 && tz == 0.0) {
            return;
        }
        Vector3dc force = new Vector3d(fx, fy, fz);
        physShip.applyWorldForce(force, physShip.getKinematics().getPosition());
        if (tx != 0.0 || ty != 0.0 || tz != 0.0) {
            physShip.applyWorldTorque(new Vector3d(tx, ty, tz));
        }
    }
}
