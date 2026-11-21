package ua.nebula.mw;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import ua.nebula.mw.aa.AirDefenseManager;
import ua.nebula.mw.command.WarfareCommandAndMenu;
import ua.nebula.mw.display.DisplayRigManager;
import ua.nebula.mw.gear.GearManager;
import ua.nebula.mw.registry.WarfareRegistry;
import ua.nebula.mw.vehicle.VehicleListener;
import ua.nebula.mw.vehicle.VehicleManager;

public class ModernWarfarePlugin extends JavaPlugin {

    private static ModernWarfarePlugin instance;
    private WarfareRegistry registry;
    private DisplayRigManager rigManager;
    private VehicleManager vehicleManager;
    private AirDefenseManager airDefenseManager;
    private GearManager gearManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.registry = new WarfareRegistry(this);
        this.rigManager = new DisplayRigManager();
        this.vehicleManager = new VehicleManager(this, rigManager);
        this.airDefenseManager = new AirDefenseManager(this, vehicleManager);
        this.gearManager = new GearManager(this, registry, vehicleManager);

        WarfareCommandAndMenu cmdAndMenu = new WarfareCommandAndMenu(this, registry);
        PluginCommand milCmd = getCommand("mil");
        if (milCmd != null) {
            milCmd.setExecutor(cmdAndMenu);
            milCmd.setTabCompleter(cmdAndMenu);
        }

        Bukkit.getPluginManager().registerEvents(new VehicleListener(this, vehicleManager, registry), this);
        Bukkit.getPluginManager().registerEvents(gearManager, this);
        Bukkit.getPluginManager().registerEvents(cmdAndMenu, this);

        long interval = Math.max(1L, getConfig().getLong("settings.tick-interval", 1L));
        Bukkit.getScheduler().runTaskTimer(this, this::tickWorld, 20L, interval);

        getLogger().info("ModernWarfareDisplay (Zero-ResourcePack) успешно запущен!");
    }

    private void tickWorld() {
        vehicleManager.tickAll();
        airDefenseManager.tickAirDefense();
        gearManager.tickGearEffects();
    }

    @Override
    public void onDisable() {
        if (airDefenseManager != null) {
            airDefenseManager.clearMissiles();
        }
        if (vehicleManager != null) {
            vehicleManager.cleanupAll();
        }
        getLogger().info("ModernWarfareDisplay выключен.");
    }

    public static ModernWarfarePlugin getInstance() {
        return instance;
    }

    public WarfareRegistry getRegistry() {
        return registry;
    }

    public DisplayRigManager getRigManager() {
        return rigManager;
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
}
