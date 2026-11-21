package ua.nebula.mw.vehicle;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import ua.nebula.mw.ModernWarfarePlugin;
import ua.nebula.mw.display.DisplayRigManager;
import ua.nebula.mw.model.ActiveVehicle;
import ua.nebula.mw.model.ContentCategory;
import ua.nebula.mw.model.VehicleSpec;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class VehicleManager {

    private final ModernWarfarePlugin plugin;
    private final DisplayRigManager rigManager;

    private final Map<UUID, ActiveVehicle> vehiclesById = new HashMap<>();
    private final Map<UUID, UUID> seatToVehicle = new HashMap<>();
    private final Map<UUID, UUID> driverToVehicle = new HashMap<>();
    private final Map<UUID, ActiveVehicle> projectileToOwnerVehicle = new HashMap<>();

    public VehicleManager(ModernWarfarePlugin plugin, DisplayRigManager rigManager) {
        this.plugin = plugin;
        this.rigManager = rigManager;
    }

    public ActiveVehicle spawnVehicle(VehicleSpec spec, Location location, Player spawner) {
        Location cleanLoc = location.clone();
        cleanLoc.setPitch(0f);

        ActiveVehicle vehicle = rigManager.spawnRig(spec, cleanLoc);
        vehiclesById.put(vehicle.getVehicleId(), vehicle);
        seatToVehicle.put(vehicle.getSeat().getUniqueId(), vehicle.getVehicleId());

        if (spawner != null) {
            spawner.getWorld().playSound(cleanLoc, Sound.BLOCK_ANVIL_PLACE, 0.8f, 0.85f);
            spawner.getWorld().spawnParticle(Particle.CLOUD, cleanLoc.clone().add(0, 0.8, 0), 18, 0.8, 0.4, 0.8, 0.02);
        }
        return vehicle;
    }

    public boolean enterVehicle(Player player, ActiveVehicle vehicle) {
        if (vehicle == null || !vehicle.isAnyEntityValid()) return false;
        if (driverToVehicle.containsKey(player.getUniqueId())) {
            leaveVehicle(player);
        }
        if (vehicle.hasDriver() && !vehicle.getDriverId().equals(player.getUniqueId())) {
            player.sendMessage("§cВ этой машине уже есть оператор/водитель!");
            return false;
        }

        vehicle.setDriverId(player.getUniqueId());
        driverToVehicle.put(player.getUniqueId(), vehicle.getVehicleId());
        player.sendMessage("§aВы заняли место в §e" + vehicle.getSpec().displayName()
                + " §7(Вперёд/назад — движение, ПКМ — основное орудие, ЛКМ — пулемёт/доп., Shift — выход)");
        player.playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 0.9f, 1.1f);
        return true;
    }

    public void leaveVehicle(Player player) {
        UUID vehicleId = driverToVehicle.remove(player.getUniqueId());
        if (vehicleId == null) return;

        ActiveVehicle vehicle = vehiclesById.get(vehicleId);
        if (vehicle != null && player.getUniqueId().equals(vehicle.getDriverId())) {
            vehicle.setDriverId(null);
            vehicle.setForwardSpeed(0.0);
            Location exitLoc = vehicle.getLocation().clone().add(0, 1.5, 0);
            if (player.isOnline() && exitLoc.getWorld() != null) {
                player.teleport(exitLoc);
                player.sendMessage("§7Вы покинули §f" + vehicle.getSpec().displayName());
            }
        }
    }

    public void firePrimary(Player player) {
        ActiveVehicle vehicle = getVehicleByDriver(player.getUniqueId());
        if (vehicle == null || vehicle.getPrimaryCooldown() > 0) return;

        VehicleSpec spec = vehicle.getSpec();
        vehicle.setPrimaryCooldown(Math.max(1, spec.primaryCooldownTicks()));

        Location muzzle = getMuzzleLocation(vehicle, player);
        Vector dir = player.getLocation().getDirection().normalize();
        World world = muzzle.getWorld();
        if (world == null) return;

        if (spec.category() == ContentCategory.DRONES && "fpv_drone".equalsIgnoreCase(spec.id())) {
            detonateVehicle(vehicle, (float) plugin.getConfig().getDouble("settings.explosion-power-fpv", 5.0));
            return;
        }

        if ("mavic_drone".equalsIgnoreCase(spec.id())) {
            Snowball vog = world.spawn(muzzle, Snowball.class, sb -> {
                sb.setShooter(player);
                sb.setVelocity(new Vector(dir.getX() * 0.85, -0.35, dir.getZ() * 0.85));
            });
            projectileToOwnerVehicle.put(vog.getUniqueId(), vehicle);
            world.playSound(muzzle, Sound.ENTITY_TNT_PRIMED, 0.8f, 1.4f);
            return;
        }

        double speed = spec.category() == ContentCategory.AIR ? 3.4 : 2.6;
        Arrow shell = world.spawn(muzzle, Arrow.class, arrow -> {
            arrow.setShooter(player);
            arrow.setVelocity(dir.multiply(speed));
            arrow.setCritical(true);
            arrow.setDamage(6.0);
            arrow.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
        });
        projectileToOwnerVehicle.put(shell.getUniqueId(), vehicle);

        world.playSound(muzzle, Sound.ENTITY_GENERIC_EXPLODE, 1.1f, 0.65f);
        world.spawnParticle(Particle.FLAME, muzzle, 14, 0.25, 0.25, 0.25, 0.08);
        world.spawnParticle(Particle.SMOKE, muzzle, 18, 0.35, 0.35, 0.35, 0.05);
    }

    public void fireSecondary(Player player) {
        ActiveVehicle vehicle = getVehicleByDriver(player.getUniqueId());
        if (vehicle == null || vehicle.getSecondaryCooldown() > 0) return;

        VehicleSpec spec = vehicle.getSpec();
        if (spec.secondaryCooldownTicks() <= 0) return;
        vehicle.setSecondaryCooldown(Math.max(1, spec.secondaryCooldownTicks()));

        Location muzzle = getMuzzleLocation(vehicle, player);
        Vector dir = player.getLocation().getDirection().normalize();
        World world = muzzle.getWorld();
        if (world == null) return;

        Snowball tracer = world.spawn(muzzle, Snowball.class, sb -> {
            sb.setShooter(player);
            sb.setVelocity(dir.multiply(3.2));
        });
        projectileToOwnerVehicle.put(tracer.getUniqueId(), vehicle);
        world.playSound(muzzle, Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.6f);
        world.spawnParticle(Particle.CRIT, muzzle, 5, 0.1, 0.1, 0.1, 0.04);
    }

    private Location getMuzzleLocation(ActiveVehicle vehicle, Player player) {
        Location eye = player.getEyeLocation();
        return eye.clone().add(eye.getDirection().normalize().multiply(1.8));
    }

    public void tickAll() {
        int tickInterval = Math.max(1, plugin.getConfig().getInt("settings.tick-interval", 1));
        boolean allowBlockDamage = plugin.getConfig().getBoolean("settings.allow-block-damage", false);
        double radarRange = plugin.getConfig().getDouble("settings.radar-range", 100.0);
        double droneMaxRange = plugin.getConfig().getDouble("settings.drone-max-range", 140.0);

        Iterator> it = vehiclesById.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ActiveVehicle> entry = it.next();
            ActiveVehicle vehicle = entry.getValue();

            if (!vehicle.isAnyEntityValid() || vehicle.getHp() <= 0) {
                removeVehicleInternal(vehicle);
                it.remove();
                continue;
            }

            if (vehicle.getPrimaryCooldown() > 0) {
                vehicle.setPrimaryCooldown(Math.max(0, vehicle.getPrimaryCooldown() - tickInterval));
            }
            if (vehicle.getSecondaryCooldown() > 0) {
                vehicle.setSecondaryCooldown(Math.max(0, vehicle.getSecondaryCooldown() - tickInterval));
            }

            updateMovement(vehicle, droneMaxRange, allowBlockDamage);
            if (vehicle.getSpec().category() == ContentCategory.AA) {
                updateRadarPing(vehicle, radarRange);
            }
        }
    }

    private void updateMovement(ActiveVehicle vehicle, double droneMaxRange, boolean allowBlockDamage) {
        VehicleSpec spec = vehicle.getSpec();
        Location current = vehicle.getLocation();
        if (current == null || current.getWorld() == null) return;

        if (vehicle.hasDriver()) {
            Player driver = plugin.getServer().getPlayer(vehicle.getDriverId());
            if (driver == null || !driver.isOnline()) {
                vehicle.setDriverId(null);
                vehicle.setForwardSpeed(0.0);
            } else {
                float yaw = driver.getLocation().getYaw();
                current.setYaw(yaw);

                if (!spec.stationary()) {
                    double maxSpeed = spec.speed() * 0.25;
                    double targetSpeed = vehicle.getForwardSpeed();
                    if (Math.abs(targetSpeed) < 0.01) {
                        targetSpeed = maxSpeed * 0.5;
                    }
                    Vector forward = driver.getLocation().getDirection().setY(0);
                    if (forward.lengthSquared() > 1e-6) {
                        forward.normalize().multiply(targetSpeed);
                        Location next = current.clone().add(forward);

                        if (spec.category() == ContentCategory.DRONES && vehicle.getSpawnLocation() != null
                                && next.distanceSquared(vehicle.getSpawnLocation()) > droneMaxRange * droneMaxRange) {
                            driver.sendMessage("§cПотеряна связь с дроном (выход за радиус " + (int) droneMaxRange + " м)!");
                            detonateVehicle(vehicle, 2.5f);
                            return;
                        }

                        RayTraceResult hit = current.getWorld().rayTraceBlocks(
                                current.clone().add(0, 0.5, 0),
                                forward.clone().normalize(),
                                forward.length() + 0.5,
                                FluidCollisionMode.NEVER,
                                true
                        );
                        if (hit == null) {
                            current.setX(next.getX());
                            current.setZ(next.getZ());
                            if (spec.category() == ContentCategory.DRONES || spec.category() == ContentCategory.AIR) {
                                double yDelta = driver.getLocation().getDirection().getY() * targetSpeed * 0.6;
                                current.setY(Math.max(current.getWorld().getMinHeight() + 1.0, current.getY() + yDelta));
                            } else {
                                int highestY = current.getWorld().getHighestBlockYAt(current);
                                current.setY(Math.max(highestY + 1.0, current.getY()));
                            }
                        } else if (spec.category() == ContentCategory.DRONES && "fpv_drone".equalsIgnoreCase(spec.id())) {
                            detonateVehicle(vehicle, (float) plugin.getConfig().getDouble("settings.explosion-power-fpv", 5.0));
                            return;
                        }
                    }
                }
            }
        }

        rigManager.updateRigTransform(vehicle, current);
        if (vehicle.getSeat() != null && vehicle.getSeat().isValid()) {
            vehicle.getSeat().teleport(current);
        }
    }

    private void updateRadarPing(ActiveVehicle vehicle, double radarRange) {
        Location loc = vehicle.getLocation();
        if (loc == null || loc.getWorld() == null) return;
        double rangeSq = radarRange * radarRange;
        for (ActiveVehicle other : vehiclesById.values()) {
            if (other == vehicle || !other.isAnyEntityValid()) continue;
            if (other.getSpec().category() == ContentCategory.AIR || other.getSpec().category() == ContentCategory.DRONES) {
                if (other.getLocation().getWorld().equals(loc.getWorld())
                        && other.getLocation().distanceSquared(loc) <= rangeSq) {
                    loc.getWorld().spawnParticle(Particle.SONIC_BOOM, loc.clone().add(0, 1.5, 0), 1);
                    break;
                }
            }
        }
    }

    public void damageVehicle(ActiveVehicle vehicle, double amount, Entity attacker) {
        if (vehicle == null || amount <= 0) return;
        double nextHp = Math.max(0.0, vehicle.getHp() - amount);
        vehicle.setHp(nextHp);
        if (vehicle.getLocation() != null && vehicle.getLocation().getWorld() != null) {
            vehicle.getLocation().getWorld().spawnParticle(
                    Particle.DAMAGE_INDICATOR,
                    vehicle.getLocation().clone().add(0, 1.0, 0),
                    6, 0.4, 0.4, 0.4, 0.05
            );
        }
        if (nextHp <= 0) {
            detonateVehicle(vehicle, 3.5f);
        }
    }

    public void detonateVehicle(ActiveVehicle vehicle, float power) {
        if (vehicle == null) return;
        Location loc = vehicle.getLocation();
        if (loc != null && loc.getWorld() != null) {
            boolean setFire = plugin.getConfig().getBoolean("settings.explosion-fire", false);
            boolean breakBlocks = plugin.getConfig().getBoolean("settings.allow-block-damage", false);
            loc.getWorld().createExplosion(loc, Math.max(0.5f, power), setFire, breakBlocks);
        }
        removeVehicle(vehicle.getVehicleId());
    }

    public void removeVehicle(UUID vehicleId) {
        ActiveVehicle vehicle = vehiclesById.remove(vehicleId);
        if (vehicle != null) {
            removeVehicleInternal(vehicle);
        }
    }

    private void removeVehicleInternal(ActiveVehicle vehicle) {
        if (vehicle.getDriverId() != null) {
            UUID removed = driverToVehicle.remove(vehicle.getDriverId());
            if (removed != null) {
                Player driver = plugin.getServer().getPlayer(vehicle.getDriverId());
                if (driver != null && driver.isOnline() && vehicle.getLocation() != null && vehicle.getLocation().getWorld() != null) {
                    driver.teleport(vehicle.getLocation().clone().add(0, 1.5, 0));
                }
            }
        }
        if (vehicle.getSeat() != null) {
            seatToVehicle.remove(vehicle.getSeat().getUniqueId());
        }
        rigManager.removeRig(vehicle);
    }

    public void cleanupAll() {
        for (ActiveVehicle vehicle : vehiclesById.values()) {
            removeVehicleInternal(vehicle);
        }
        vehiclesById.clear();
        seatToVehicle.clear();
        driverToVehicle.clear();
        projectileToOwnerVehicle.clear();
    }

    public ActiveVehicle getVehicleById(UUID id) {
        return vehiclesById.get(id);
    }

    public ActiveVehicle getVehicleBySeat(UUID seatId) {
        UUID id = seatToVehicle.get(seatId);
        return id == null ? null : vehiclesById.get(id);
    }

    public ActiveVehicle getVehicleByDriver(UUID driverId) {
        UUID id = driverToVehicle.get(driverId);
        return id == null ? null : vehiclesById.get(id);
    }

    public ActiveVehicle getOwnerVehicleByProjectile(UUID projectileId) {
        return projectileToOwnerVehicle.get(projectileId);
    }

    public void removeProjectileOwner(UUID projectileId) {
        projectileToOwnerVehicle.remove(projectileId);
    }

    public Collection<ActiveVehicle> getAllVehicles() {
        return Collections.unmodifiableCollection(vehiclesById.values());
    }
}
