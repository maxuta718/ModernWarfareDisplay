package ua.nebula.mw;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ua.nebula.mw.aa.AirDefenseManager;
import ua.nebula.mw.command.WarfareCommandAndMenu;
import ua.nebula.mw.display.DisplayRigManager;
import ua.nebula.mw.gear.GearManager;
import ua.nebula.mw.registry.WarfareRegistry;
import ua.nebula.mw.vehicle.VehicleListener;
import ua.nebula.mw.vehicle.VehicleManager;

public class ModernWarfarePlugin extends JavaPlugin {

    private WarfareRegistry registry;
    private DisplayRigManager displayRigManager;
    private VehicleManager vehicleManager;
    private AirDefenseManager airDefenseManager;
    private GearManager gearManager;
    private WarfareCommandAndMenu commandAndMenu;
    private BukkitTask mainTickTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.registry = new WarfareRegistry(this);
        this.registry.reloadSpecs();

        this.displayRigManager = new DisplayRigManager(this);
        this.vehicleManager = new VehicleManager(this, registry, displayRigManager);
        this.airDefenseManager = new AirDefenseManager(this, vehicleManager);
        this.gearManager = new GearManager(this, registry, vehicleManager);
        this.commandAndMenu = new WarfareCommandAndMenu(this, registry);

        getServer().getPluginManager().registerEvents(new VehicleListener(this, registry, vehicleManager, gearManager), this);
        getServer().getPluginManager().registerEvents(gearManager, this);
        getServer().getPluginManager().registerEvents(commandAndMenu, this);

        PluginCommand milCmd = getCommand("mil");
        if (milCmd != null) {
            milCmd.setExecutor(commandAndMenu);
            milCmd.setTabCompleter(commandAndMenu);
        } else {
            getLogger().warning("Команда 'mil' не найдена в plugin.yml! Проверь файл ресурсов.");
        }

        vehicleManager.restoreVehiclesFromWorlds();

        long period = Math.max(1L, getConfig().getLong("settings.tick-period", 1L));
        this.mainTickTask = getServer().getScheduler().runTaskTimer(this, () -> {
            vehicleManager.tickVehicles();
            airDefenseManager.tickAirDefense();
            gearManager.tickGear();
        }, period, period);

        getLogger().info("ModernWarfareDisplay включён!");
    }

    @Override
    public void onDisable() {
        if (mainTickTask != null) {
            mainTickTask.cancel();
            mainTickTask = null;
        }
        if (airDefenseManager != null) {
            airDefenseManager.clearMissiles();
        }
        if (gearManager != null) {
            gearManager.cleanupAll();
        }
        if (vehicleManager != null) {
            vehicleManager.cleanupAll();
        }
        getLogger().info("ModernWarfareDisplay выключен.");
    }

    public WarfareRegistry getRegistry() {
        return registry;
    }

    public DisplayRigManager getDisplayRigManager() {
        return displayRigManager;
    }

    public VehicleManager getVehicleManager() {
        return vehicleManager;
    }

    public AirDefenseManager getAirDefenseManager() {
        return airDefenseManager;
    }

    public GearManager getGearManager() {
        return gearManager;
    }

    public WarfareCommandAndMenu getCommandAndMenu() {
        return commandAndMenu;
    }
}
