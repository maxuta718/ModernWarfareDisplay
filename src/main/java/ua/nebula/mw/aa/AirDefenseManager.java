package ua.nebula.mw.aa;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import ua.nebula.mw.ModernWarfarePlugin;
import ua.nebula.mw.model.ActiveVehicle;
import ua.nebula.mw.model.ContentCategory;
import ua.nebula.mw.vehicle.VehicleManager;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AirDefenseManager {

    private final ModernWarfarePlugin plugin;
    private final VehicleManager vehicleManager;
    private final Map<UUID, GuidedMissile> activeMissiles = new ConcurrentHashMap<>();

    public record GuidedMissile(UUID missileId, UUID launcherId, UUID targetVehicleId, double damage, int ticksRemaining) {
        public GuidedMissile step() {
            return new GuidedMissile(missileId, launcherId, targetVehicleId, damage, ticksRemaining - 1);
        }
    }

    public AirDefenseManager(ModernWarfarePlugin plugin, VehicleManager vehicleManager) {
        this.plugin = plugin;
        this.vehicleManager = vehicleManager;
    }

    public void tickAirDefense() {
        tickGuidedMissiles();

        double radarRange = plugin.getConfig().getDouble("settings.radar-range", 100.0);
        double rangeSq = radarRange * radarRange;

        for (ActiveVehicle aa : vehicleManager.getAllVehicles()) {
            if (aa.getSpec().category() != ContentCategory.AA) continue;
            if (!aa.isAnyEntityValid()) continue;

            // Вращение радара
            aa.setRotorAngle((aa.getRotorAngle() + 8f) % 360f);

            ActiveVehicle target = findNearestAirTarget(aa, rangeSq);
            if (target == null) continue;

            // Поворот башни/пусковой установки на цель
            Vector toTarget = target.getLocation().toVector().subtract(aa.getLocation().toVector());
            double desiredYaw = Math.toDegrees(Math.atan2(-toTarget.getX(), toTarget.getZ()));
            float relYaw = wrapDegrees((float) (desiredYaw - aa.getYaw()));
            aa.setTurretYawOffset(relYaw);

            if (aa.getRadarAlertCooldown() <= 0) {
                aa.setRadarAlertCooldown(100);
                notifyRadarAlert(aa, target, radarRange);
            }

            if (aa.getPrimaryCooldown() <= 0) {
                aa.setPrimaryCooldown(aa.getSpec().primaryCooldownTicks());
                launchGuidedMissile(aa, target);
            }
        }
    }

    private ActiveVehicle findNearestAirTarget(ActiveVehicle aa, double rangeSq) {
        if (aa.getLocation().getWorld() == null) return null;
        ActiveVehicle best = null;
        double bestDistSq = rangeSq;

        for (ActiveVehicle candidate : vehicleManager.getAllVehicles()) {
            if (candidate.getVehicleId().equals(aa.getVehicleId())) continue;
            if (!candidate.getSpec().airTarget()) continue;
            if (candidate.getLocation().getWorld() != aa.getLocation().getWorld()) continue;

            double dSq = candidate.getLocation().distanceSquared(aa.getLocation());
            if (dSq <= bestDistSq) {
                bestDistSq = dSq;
                best = candidate;
            }
        }
        return best;
    }

    private void notifyRadarAlert(ActiveVehicle aa, ActiveVehicle target, double range) {
        World world = aa.getLocation().getWorld();
        if (world == null) return;

        double dist = Math.sqrt(aa.getLocation().distanceSquared(target.getLocation()));
        String msg = "§c[ПВО РАДАР] §f" + aa.getSpec().displayName() + " §7обнаружил цель §e"
                + target.getSpec().displayName() + " §7на дистанции §c" + (int) dist + "м §7(радиус " + (int) range + "м)!";

        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(aa.getLocation()) <= (range + 30) * (range + 30)) {
                p.sendMessage(msg);
                p.sendActionBar("§c⚠ ТРЕВОГА ПВО: Цель " + target.getSpec().displayName() + " (" + (int) dist + "м)");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.8f);
            }
        }

        if (target.hasDriver()) {
            Player pilot = Bukkit.getPlayer(target.getDriverId());
            if (pilot != null) {
                pilot.sendActionBar("§4§l⚠ ЗАХВАТ РАДАРОМ ПВО (" + aa.getSpec().displayName() + ")!");
                pilot.playSound(pilot.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.2f, 0.5f);
            }
        }
    }

    private void launchGuidedMissile(ActiveVehicle aa, ActiveVehicle target) {
        World world = aa.getLocation().getWorld();
        if (world == null) return;

        Location launchLoc = aa.getLocation().clone().add(0, 2.2, 0);
        Vector dir = target.getLocation().clone().add(0, 0.8, 0).toVector()
                .subtract(launchLoc.toVector()).normalize().multiply(2.1);

        Arrow missile = world.spawn(launchLoc, Arrow.class, a -> {
            a.setVelocity(dir);
            a.setGravity(false);
            a.setCritical(true);
            a.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
        });

        double dmg = aa.getSpec().id().equals("patriot") ? 180.0 : 130.0;
        activeMissiles.put(missile.getUniqueId(), new GuidedMissile(
                missile.getUniqueId(), aa.getVehicleId(), target.getVehicleId(), dmg, 140
        ));

        world.playSound(launchLoc, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.8f, 0.7f);
    }

    private void tickGuidedMissiles() {
        Iterator> it = activeMissiles.entrySet().iterator();
        while (it.hasNext()) {
            GuidedMissile gm = it.next().getValue();
            if (gm.ticksRemaining() <= 0) {
                Entity e = Bukkit.getEntity(gm.missileId());
                if (e != null) e.remove();
                it.remove();
                continue;
            }

            Entity rawMissile = Bukkit.getEntity(gm.missileId());
            ActiveVehicle target = vehicleManager.getVehicleById(gm.targetVehicleId());

            if (!(rawMissile instanceof Arrow missile) || !missile.isValid() || target == null || !target.isAnyEntityValid()) {
                if (rawMissile != null) rawMissile.remove();
                it.remove();
                continue;
            }

            Location targetCenter = target.getLocation().clone().add(0, 0.8, 0);
            Vector desired = targetCenter.toVector().subtract(missile.getLocation().toVector());
            double distSq = desired.lengthSquared();

            if (distSq <= 4.5) {
                World world = missile.getWorld();
                boolean breakBlocks = plugin.getConfig().getBoolean("settings.allow-block-damage", false);
                world.createExplosion(missile.getLocation(), 4.2f, false, breakBlocks);
                world.spawnParticle(Particle.EXPLOSION_EMITTER, missile.getLocation(), 2);
                world.playSound(missile.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.9f);
                vehicleManager.damageVehicle(target, gm.damage(), missile);
                missile.remove();
                it.remove();
                continue;
            }

            Vector currentVel = missile.getVelocity();
            Vector steeredDir = currentVel.lengthSquared() > 0.01
                    ? currentVel.normalize().multiply(0.72).add(desired.normalize().multiply(0.28)).normalize()
                    : desired.normalize();

            missile.setVelocity(steeredDir.multiply(2.15));
            missile.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, missile.getLocation(), 4, 0.08, 0.08, 0.08, 0.01);
            it.setValue(gm.step());
        }
    }

    private float wrapDegrees(float deg) {
        while (deg > 180f) deg -= 360f;
        while (deg < -180f) deg += 360f;
        return deg;
    }

    public void clearMissiles() {
        for (UUID id : activeMissiles.keySet()) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        activeMissiles.clear();
    }
}
