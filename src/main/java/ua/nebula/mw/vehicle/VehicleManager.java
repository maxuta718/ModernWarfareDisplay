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
                vehicle.setPrimaryCooldown(vehicle.getPrimaryCooldown() - tickInterval);
            }
            if (vehicle.getSecondaryCooldown() > 0) {
                vehicle.setSecondaryCooldown(vehicle.getSecondaryCooldown
