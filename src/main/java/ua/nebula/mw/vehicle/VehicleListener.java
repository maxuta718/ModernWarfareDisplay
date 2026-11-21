package ua.nebula.mw.vehicle;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import ua.nebula.mw.model.ActiveVehicle;
import ua.nebula.mw.model.VehicleSpec;
import ua.nebula.mw.registry.WarfareRegistry;

public class VehicleListener implements Listener {

    private final WarfareRegistry registry;
    private final VehicleManager vehicleManager;

    public VehicleListener(WarfareRegistry registry, VehicleManager vehicleManager) {
        this.registry = registry;
        this.vehicleManager = vehicleManager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        ActiveVehicle vehicle = vehicleManager.findVehicleByAnyEntity(event.getRightClicked().getUniqueId());
        if (vehicle != null) {
            event.setCancelled(true);
            vehicleManager.enterVehicle(player, vehicle);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        Action action = event.getAction();

        ItemStack item = event.getItem();
        String vehicleId = registry.getVehicleId(item);
        if (vehicleId != null && (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR)) {
            event.setCancelled(true);
            VehicleSpec spec = registry.getVehicle(vehicleId);
            if (spec == null) {
                player.sendMessage("§cНеизвестная модель техники: §f" + vehicleId);
                return;
            }
            Location spawnLoc;
            if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
                spawnLoc = event.getClickedBlock().getLocation().add(0.5, 1.0, 0.5);
            } else {
                spawnLoc = player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(3.0));
            }
            spawnLoc.setYaw(player.getLocation().getYaw());
            vehicleManager.spawnVehicle(spec, spawnLoc, player);
            player.sendMessage("§aРазвёрнута техника: §e" + spec.displayName());
            return;
        }

        ActiveVehicle driven = vehicleManager.getVehicleByDriver(player.getUniqueId());
        if (driven != null) {
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
                vehicleManager.firePrimary(player);
            } else if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
                event.setCancelled(true);
                vehicleManager.fireSecondary(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        ActiveVehicle targetVehicle = vehicleManager.findVehicleByAnyEntity(victim.getUniqueId());
        if (targetVehicle != null) {
            event.setCancelled(true);
            Entity damager = event.getDamager();
            if (damager instanceof Player player) {
                ActiveVehicle driven = vehicleManager.getVehicleByDriver(player.getUniqueId());
                if (driven != null && driven.getVehicleId().equals(targetVehicle.getVehicleId())) {
                    vehicleManager.fireSecondary(player);
                    return;
                }
            }
            vehicleManager.damageVehicle(targetVehicle, Math.max(4.0, event.getFinalDamage()), damager);
            return;
        }

        if (event.getDamager() instanceof Player attacker) {
            ActiveVehicle driven = vehicleManager.getVehicleByDriver(attacker.getUniqueId());
            if (driven != null) {
                event.setCancelled(true);
                vehicleManager.fireSecondary(attacker);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            ActiveVehicle driven = vehicleManager.getVehicleByDriver(player.getUniqueId());
            if (driven != null && (event.getCause() == EntityDamageEvent.DamageCause.FALL
                    || event.getCause() == EntityDamageEvent.DamageCause.FLY_INTO_WALL
                    || event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onProjectileHit(ProjectileHitEvent event) {
        Entity proj = event.getEntity();
        Location hitLoc = event.getHitBlock() != null
                ? event.getHitBlock().getLocation().add(0.5, 0.5, 0.5)
                : proj.getLocation();
        vehicleManager.handleProjectileHit(proj, hitLoc, event.getHitEntity());
        proj.remove();
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) {
            Player player = event.getPlayer();
            if (vehicleManager.getVehicleByDriver(player.getUniqueId()) != null) {
                vehicleManager.leaveVehicle(player);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        vehicleManager.leaveVehicle(event.getPlayer());
    }
}
