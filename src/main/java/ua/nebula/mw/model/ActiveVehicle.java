package ua.nebula.mw.model;

import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.TextDisplay;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ActiveVehicle {

    public enum PartRole {
        ROOT,
        HULL,
        TURRET,
        BARREL,
        WING_LEFT,
        WING_RIGHT,
        TAIL,
        PROPELLER_1,
        PROPELLER_2,
        PROPELLER_3,
        PROPELLER_4,
        EXHAUST,
        RADAR,
        LAUNCHER
    }

    public record RigNode(PartRole role, Display entity, Vector3f localOffset, Vector3f scale, Vector3f eulerDeg) {}

    private final UUID vehicleId;
    private final VehicleSpec spec;
    private final Location location;
    private final Interaction seat;
    private final TextDisplay hpDisplay;
    private final List<RigNode> nodes = new ArrayList<>();

    private double hp;
    private double maxHp;
    private UUID driverId;
    private Location originLocation;
    private int primaryCooldown;
    private int secondaryCooldown;
    private double forwardSpeed;
    private float yaw;
    private float pitch;
    private float roll;
    private float turretYawOffset;
    private float rotorAngle;
    private int radarAlertCooldown;

    public ActiveVehicle(UUID vehicleId, VehicleSpec spec, Location location, Interaction seat, TextDisplay hpDisplay, double maxHp) {
        this.vehicleId = vehicleId;
        this.spec = spec;
        this.location = location.clone();
        this.originLocation = location.clone();
        this.seat = seat;
        this.hpDisplay = hpDisplay;
        this.maxHp = maxHp;
        this.hp = maxHp;
        this.yaw = location.getYaw();
        this.pitch = location.getPitch();
    }

    public UUID getVehicleId() { return vehicleId; }
    public VehicleSpec getSpec() { return spec; }
    public Location getLocation() { return location; }
    public Interaction getSeat() { return seat; }
    public TextDisplay getHpDisplay() { return hpDisplay; }
    public List<RigNode> getNodes() { return nodes; }

    public double getHp() { return hp; }
    public void setHp(double hp) { this.hp = Math.max(0.0, Math.min(maxHp, hp)); }
    public double getMaxHp() { return maxHp; }
    public void setMaxHp(double maxHp) { this.maxHp = maxHp; this.hp = maxHp; }

    public UUID getDriverId() { return driverId; }
    public void setDriverId(UUID driverId) { this.driverId = driverId; }
    public boolean hasDriver() { return driverId != null; }

    public Location getOriginLocation() { return originLocation; }
    public void setOriginLocation(Location originLocation) { this.originLocation = originLocation.clone(); }

    public int getPrimaryCooldown() { return primaryCooldown; }
    public void setPrimaryCooldown(int primaryCooldown) { this.primaryCooldown = Math.max(0, primaryCooldown); }
    public int getSecondaryCooldown() { return secondaryCooldown; }
    public void setSecondaryCooldown(int secondaryCooldown) { this.secondaryCooldown = Math.max(0, secondaryCooldown); }

    public double getForwardSpeed() { return forwardSpeed; }
    public void setForwardSpeed(double forwardSpeed) { this.forwardSpeed = forwardSpeed; }

    public float getYaw() { return yaw; }
    public void setYaw(float yaw) { this.yaw = yaw; }
    public float getPitch() { return pitch; }
    public void setPitch(float pitch) { this.pitch = pitch; }
    public float getRoll() { return roll; }
    public void setRoll(float roll) { this.roll = roll; }

    public float getTurretYawOffset() { return turretYawOffset; }
    public void setTurretYawOffset(float turretYawOffset) { this.turretYawOffset = turretYawOffset; }

    public float getRotorAngle() { return rotorAngle; }
    public void setRotorAngle(float rotorAngle) { this.rotorAngle = rotorAngle; }

    public int getRadarAlertCooldown() { return radarAlertCooldown; }
    public void setRadarAlertCooldown(int radarAlertCooldown) { this.radarAlertCooldown = Math.max(0, radarAlertCooldown); }

    public void cleanupEntities() {
        for (RigNode node : nodes) {
            if (node.entity() != null && node.entity().isValid()) {
                node.entity().remove();
            }
        }
        nodes.clear();
        if (hpDisplay != null && hpDisplay.isValid()) {
            hpDisplay.remove();
        }
        if (seat != null && seat.isValid()) {
            seat.remove();
        }
    }

    public boolean isAnyEntityValid() {
        return (seat != null && seat.isValid()) || (hpDisplay != null && hpDisplay.isValid()) || nodes.stream().anyMatch(n -> n.entity() != null && n.entity().isValid());
    }

    public List<Entity> getAllEntities() {
        List<Entity> all = new ArrayList<>();
        if (seat != null) all.add(seat);
        if (hpDisplay != null) all.add(hpDisplay);
        for (RigNode node : nodes) {
            if (node.entity() != null) all.add(node.entity());
        }
        return all;
    }
}
