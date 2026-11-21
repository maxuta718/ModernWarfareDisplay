package ua.nebula.mw.gear;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import ua.nebula.mw.ModernWarfarePlugin;
import ua.nebula.mw.model.ActiveVehicle;
import ua.nebula.mw.model.GearSpec;
import ua.nebula.mw.registry.WarfareRegistry;
import ua.nebula.mw.vehicle.VehicleManager;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GearManager implements Listener {

    private final ModernWarfarePlugin plugin;
    private final WarfareRegistry registry;
    private final VehicleManager vehicleManager;

    private final Map<UUID, Map<String, Integer>> ammoByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, ReloadState> reloadsByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Integer>> cooldownByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, GuidedJavelin> activeJavelins = new ConcurrentHashMap<>();

    public record ReloadState(String gearId, int ticksRemaining) {
        public ReloadState step() {
            return new ReloadState(gearId, ticksRemaining - 1);
        }
    }

    public record GuidedJavelin(UUID projectileId, UUID shooterId, UUID targetVehicleId, double damage, int ticksRemaining) {
        public GuidedJavelin step() {
            return new GuidedJavelin(projectileId, shooterId, targetVehicleId, damage, ticksRemaining - 1);
        }
    }

    public GearManager(ModernWarfarePlugin plugin, WarfareRegistry registry, VehicleManager vehicleManager) {
        this.plugin = plugin;
        this.registry = registry;
        this.vehicleManager = vehicleManager;
    }

    public void tickGear() {
        tickReloads();
        tickCooldowns();
        tickGuidedJavelins();
    }

    private void tickReloads() {
        Iterator> it = reloadsByPlayer.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ReloadState> entry = it.next();
            UUID playerId = entry.getKey();
            ReloadState state = entry.getValue();
            Player player = plugin.getServer().getPlayer(playerId);

            if (player == null || !player.isOnline()) {
                it.remove();
                continue;
            }

            GearSpec spec = registry.getGear(state.gearId());
            if (spec == null) {
                it.remove();
                continue;
            }

            if (state.ticksRemaining() <= 1) {
                ammoByPlayer.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                        .put(spec.id(), spec.magazineSize());
                it.remove();
                player.sendActionBar(Component.text("§aПерезарядка завершена: §f" + spec.displayName()
                        + " §7(" + spec.magazineSize() + "/" + spec.magazineSize() + ")"));
                player.playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 0.8f, 1.4f);
            } else {
                double progress = 1.0 - ((double) (state.ticksRemaining() - 1) / Math.max(1, spec.reloadTicks()));
                int pct = (int) Math.max(0, Math.min(100, progress * 100));
                player.sendActionBar(Component.text("§eПерезарядка " + spec.displayName() + "... §f" + pct + "%"));
                it.setValue(state.step());
            }
        }
    }

    private void tickCooldowns() {
        for (Map<String, Integer> map : cooldownByPlayer.values()) {
            map.entrySet().removeIf(e -> {
                int next = e.getValue() - 1;
                if (next <= 0) return true;
                e.setValue(next);
                return false;
            });
        }
    }

    private void tickGuidedJavelins() {
        Iterator> it = activeJavelins.entrySet().iterator();
        while (it.hasNext()) {
            GuidedJavelin gj = it.next().getValue();
            if (gj.ticksRemaining() <= 0) {
                Entity e = plugin.getServer().getEntity(gj.projectileId());
                if (e != null) e.remove();
                it.remove();
                continue;
            }

            Entity raw = plugin.getServer().getEntity(gj.projectileId());
            ActiveVehicle target = vehicleManager.getVehicleById(gj.targetVehicleId());
            if (!(raw instanceof Arrow rocket) || !rocket.isValid() || target == null || !target.isAnyEntityValid()) {
                if (raw != null) raw.remove();
                it.remove();
                continue;
            }

            Location aimLoc = target.getLocation().clone().add(0, 1.2, 0);
            Vector toTarget = aimLoc.toVector().subtract(rocket.getLocation().toVector());
            double distSq = toTarget.lengthSquared();

            if (distSq <= 5.0) {
                World world = rocket.getWorld();
                boolean breakBlocks = plugin.getConfig().getBoolean("settings.allow-block-damage", false);
                world.createExplosion(rocket.getLocation(), 4.5f, false, breakBlocks);
                world.spawnParticle(Particle.EXPLOSION_EMITTER, rocket.getLocation(), 2);
                world.playSound(rocket.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.8f, 0.75f);
                Entity shooter = plugin.getServer().getEntity(gj.shooterId());
                vehicleManager.damageVehicle(target, gj.damage(), shooter);
                rocket.remove();
                it.remove();
                continue;
            }

            Vector current = rocket.getVelocity();
            Vector blended = current.lengthSquared() > 0.01
                    ? current.normalize().multiply(0.75).add(toTarget.normalize().multiply(0.25)).normalize()
                    : toTarget.normalize();
            rocket.setVelocity(blended.multiply(2.4));
            rocket.getWorld().spawnParticle(Particle.SMOKE, rocket.getLocation(), 3, 0.06, 0.06, 0.06, 0.01);
            it.setValue(gj.step());
        }
    }

    public void startReload(Player player, GearSpec spec) {
        if (spec == null || spec.armor() || spec.magazineSize() <= 0) return;
        UUID playerId = player.getUniqueId();
        if (reloadsByPlayer.containsKey(playerId)) return;

        int currentAmmo = getAmmo(playerId, spec);
        if (currentAmmo >= spec.magazineSize()) {
            player.sendActionBar(Component.text("§7Магазин §f" + spec.displayName() + " §7уже полон"));
            return;
        }

        int reloadTicks = Math.max(5, spec.reloadTicks());
        reloadsByPlayer.put(playerId, new ReloadState(spec.id(), reloadTicks));
        player.playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_START, 0.9f, 1.0f);
    }

    public int getAmmo(UUID playerId, GearSpec spec) {
        Map<String, Integer> map = ammoByPlayer.get(playerId);
        if (map == null || !map.containsKey(spec.id())) {
            return spec.magazineSize();
        }
        return map.get(spec.id());
    }

    private void setAmmo(UUID playerId, GearSpec spec, int ammo) {
        ammoByPlayer.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .put(spec.id(), Math.max(0, Math.min(spec.magazineSize(), ammo)));
    }

    private boolean isOnCooldown(UUID playerId, String gearId) {
        Map<String, Integer> map = cooldownByPlayer.get(playerId);
        return map != null && map.getOrDefault(gearId, 0) > 0;
    }

    private void setCooldown(UUID playerId, String gearId, int ticks) {
        if (ticks <= 0) return;
        cooldownByPlayer.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(gearId, ticks);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (vehicleManager.getVehicleByDriver(player.getUniqueId()) != null) return;

        ItemStack item = event.getItem();
        String gearId = registry.getGearId(item);
        if (gearId == null) return;

        GearSpec spec = registry.getGear(gearId);
        if (spec == null || spec.armor()) return;

        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            startReload(player, spec);
            return;
        }

        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            fireWeapon(player, spec);
        }
    }

    private void fireWeapon(Player player, GearSpec spec) {
        UUID playerId = player.getUniqueId();
        if (reloadsByPlayer.containsKey(playerId)) {
            player.sendActionBar(Component.text("§cИдёт перезарядка..."));
            return;
        }
        if (isOnCooldown(playerId, spec.id())) return;

        int ammo = getAmmo(playerId, spec);
        if (ammo <= 0) {
            player.sendActionBar(Component.text("§cМагазин пуст! Нажмите ЛКМ для перезарядки"));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 0.6f);
            return;
        }

        setAmmo(playerId, spec, ammo - 1);
        setCooldown(playerId, spec.id(), Math.max(1, spec.fireCooldownTicks()));

        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        World world = eye.getWorld();
        if (world == null) return;

        if (spec.guidedOrRocket()) {
            ActiveVehicle lockedTarget = findLockedVehicleTarget(player, 160.0);
            Location muzzle = eye.clone().add(dir.clone().multiply(1.2));
            Arrow rocket = world.spawn(muzzle, Arrow.class, a -> {
                a.setShooter(player);
                a.setVelocity(dir.clone().multiply(2.4));
                a.setGravity(false);
                a.setCritical(true);
                a.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
            });
            if (lockedTarget != null) {
                activeJavelins.put(rocket.getUniqueId(), new GuidedJavelin(
                        rocket.getUniqueId(), playerId, lockedTarget.getVehicleId(), spec.damage(), 150
                ));
                player.sendActionBar(Component.text("§aЦель захвачена: §e" + lockedTarget.getSpec().displayName()));
            } else {
                player.sendActionBar(Component.text("§eПуск неуправляемой ракеты"));
            }
            world.playSound(muzzle, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.5f, 0.85f);
            world.spawnParticle(Particle.SMOKE, muzzle, 10, 0.2, 0.2, 0.2, 0.04);
        } else {
            world.playSound(eye, Sound.ENTITY_CROSSBOW_SHOOT, 1.2f, 1.25f);
            world.spawnParticle(Particle.SMOKE, eye.clone().add(dir.clone().multiply(0.9)), 4, 0.05, 0.05, 0.05, 0.01);

            RayTraceResult hit = world.rayTrace(eye, dir, 90.0, org.bukkit.FluidCollisionMode.NEVER, true, 0.25,
                    entity -> !entity.getUniqueId().equals(playerId) && !(entity instanceof Arrow));

            if (hit != null) {
                Location hitPos = hit.getHitPosition().toLocation(world);
                world.spawnParticle(Particle.CRIT, hitPos, 5, 0.1, 0.1, 0.1, 0.05);

                Entity hitEntity = hit.getHitEntity();
                if (hitEntity != null) {
                    ActiveVehicle vehicle = vehicleManager.findVehicleByAnyEntity(hitEntity.getUniqueId());
                    if (vehicle != null) {
                        vehicleManager.damageVehicle(vehicle, spec.damage() * 0.65, player);
                    } else if (hitEntity instanceof LivingEntity living) {
                        living.damage(spec.damage(), player);
                    }
                }
            }
        }

        int remaining = getAmmo(playerId, spec);
        player.sendActionBar(Component.text("§f" + spec.displayName() + " §7| Патроны: §e" + remaining + "§7/" + spec.magazineSize()));
        if (remaining == 0) {
            startReload(player, spec);
        }
    }

    private ActiveVehicle findLockedVehicleTarget(Player player, double maxRange) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        ActiveVehicle best = null;
        double bestDot = 0.92; // конус наведения

        for (ActiveVehicle vehicle : vehicleManager.getAllVehicles()) {
            if (!vehicle.isAnyEntityValid() || vehicle.getLocation().getWorld() != eye.getWorld()) continue;
            double dist = vehicle.getLocation().distance(eye);
            if (dist > maxRange || dist < 2.0) continue;

            Vector toV = vehicle.getLocation().clone().add(0, 0.8, 0).toVector().subtract(eye.toVector()).normalize();
            double dot = dir.dot(toV);
            if (dot >= bestDot) {
                bestDot = dot;
                best = vehicle;
            }
        }
        return best;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        double totalReduction = 0.0;
        for (ItemStack armorPiece : player.getInventory().getArmorContents()) {
            String gearId = registry.getGearId(armorPiece);
            if (gearId == null) continue;
            GearSpec spec = registry.getGear(gearId);
            if (spec != null && spec.armor()) {
                totalReduction += spec.damageReduction();
            }
        }

        if (totalReduction > 0.0) {
            totalReduction = Math.min(0.85, totalReduction);
            event.setDamage(Math.max(0.0, event.getDamage() * (1.0 - totalReduction)));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            ItemStack item = player.getInventory().getItemInMainHand();
            String gearId = registry.getGearId(item);
            if (gearId != null) {
                GearSpec spec = registry.getGear(gearId);
                if (spec != null && !spec.armor()) {
                    event.setCancelled(true);
                    startReload(player, spec);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onProjectileHit(ProjectileHitEvent event) {
        UUID id = event.getEntity().getUniqueId();
        GuidedJavelin gj = activeJavelins.remove(id);
        if (gj != null) {
            Location loc = event.getEntity().getLocation();
            World world = loc.getWorld();
            if (world != null) {
                boolean breakBlocks = plugin.getConfig().getBoolean("settings.allow-block-damage", false);
                world.createExplosion(loc, 4.5f, false, breakBlocks);
                world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 2);
                if (event.getHitEntity() != null) {
                    ActiveVehicle v = vehicleManager.findVehicleByAnyEntity(event.getHitEntity().getUniqueId());
                    if (v != null) {
                        vehicleManager.damageVehicle(v, gj.damage(), event.getEntity());
                    }
                }
            }
            event.getEntity().remove();
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        reloadsByPlayer.remove(id);
        cooldownByPlayer.remove(id);
    }

    public void cleanupAll() {
        for (UUID projId : activeJavelins.keySet()) {
            Entity e = plugin.getServer().getEntity(projId);
            if (e != null) e.remove();
        }
        activeJavelins.clear();
        reloadsByPlayer.clear();
        cooldownByPlayer.clear();
        ammoByPlayer.clear();
    }
}
