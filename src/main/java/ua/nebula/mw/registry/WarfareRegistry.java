package ua.nebula.mw.registry;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import ua.nebula.mw.ModernWarfarePlugin;
import ua.nebula.mw.model.ContentCategory;
import ua.nebula.mw.model.GearSpec;
import ua.nebula.mw.model.VehicleSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WarfareRegistry {

    private final ModernWarfarePlugin plugin;
    private final NamespacedKey vehicleKey;
    private final NamespacedKey gearKey;

    private final Map<String, VehicleSpec> vehicles = new LinkedHashMap<>();
    private final Map<String, GearSpec> gear = new LinkedHashMap<>();

    public WarfareRegistry(ModernWarfarePlugin plugin) {
        this.plugin = plugin;
        this.vehicleKey = new NamespacedKey(plugin, "vehicle_id");
        this.gearKey = new NamespacedKey(plugin, "gear_id");
        reloadSpecs();
    }

    public void reloadSpecs() {
        vehicles.clear();
        gear.clear();

        registerVehicle("oplot", ContentCategory.GROUND, Material.GREEN_TERRACOTTA, Material.IRON_BLOCK,
                Color.fromRGB(62, 82, 54), 1200.0, 0.32, 45, 3, false, false);
        registerVehicle("t80", ContentCategory.GROUND, Material.MOSS_BLOCK, Material.IRON_BLOCK,
                Color.fromRGB(74, 92, 56), 1100.0, 0.36, 40, 3, false, false);
        registerVehicle("bmp2", ContentCategory.GROUND, Material.OLIVE_PLANKS, Material.IRON_BLOCK,
                Color.fromRGB(70, 86, 52), 650.0, 0.44, 14, 4, false, false);
        registerVehicle("kozak", ContentCategory.GROUND, Material.GREEN_CONCRETE, Material.BLACK_CONCRETE,
                Color.fromRGB(58, 74, 48), 420.0, 0.56, 20, 3, false, false);

        registerVehicle("f16", ContentCategory.AIR, Material.LIGHT_GRAY_CONCRETE, Material.WHITE_CONCRETE,
                Color.fromRGB(165, 172, 180), 520.0, 1.15, 35, 2, false, true);
        registerVehicle("su30sm", ContentCategory.AIR, Material.CYAN_TERRACOTTA, Material.LIGHT_BLUE_CONCRETE,
                Color.fromRGB(92, 128, 156), 680.0, 1.10, 30, 2, false, true);
        registerVehicle("mi24", ContentCategory.AIR, Material.GREEN_CONCRETE, Material.DARK_OAK_PLANKS,
                Color.fromRGB(64, 80, 50), 600.0, 0.68, 25, 3, false, true);

        registerVehicle("fpv_drone", ContentCategory.DRONES, Material.BLACK_CONCRETE, Material.RED_CONCRETE,
                Color.fromRGB(45, 45, 50), 35.0, 0.72, 0, 0, false, true);
        registerVehicle("mavic_drone", ContentCategory.DRONES, Material.LIGHT_GRAY_CONCRETE, Material.BLACK_CONCRETE,
                Color.fromRGB(145, 148, 152), 55.0, 0.52, 25, 0, false, true);

        registerVehicle("patriot", ContentCategory.AA, Material.GREEN_CONCRETE, Material.LIGHT_GRAY_CONCRETE,
                Color.fromRGB(68, 84, 58), 800.0, 0.0, 35, 0, true, false);
        registerVehicle("irist", ContentCategory.AA, Material.GREEN_TERRACOTTA, Material.IRON_BLOCK,
                Color.fromRGB(76, 90, 62), 750.0, 0.0, 25, 0, true, false);
        registerVehicle("gepard", ContentCategory.AA, Material.MOSS_BLOCK, Material.IRON_BLOCK,
                Color.fromRGB(72, 88, 54), 700.0, 0.34, 4, 0, false, false);

        double plateRed = plugin.getConfig().getDouble("armor-reduction.plate_carrier", 0.25);
        double helmRed = plugin.getConfig().getDouble("armor-reduction.tactical_helmet", 0.15);

        registerGear(new GearSpec("plate_carrier", "Бронежилет «Plate Carrier»", Material.LEATHER_CHESTPLATE,
                true, EquipmentSlot.CHEST, Color.fromRGB(56, 68, 46), 0.0, 0, 0, 0, false, plateRed));
        registerGear(new GearSpec("tactical_helmet", "Тактический шлем", Material.LEATHER_HELMET,
                true, EquipmentSlot.HEAD, Color.fromRGB(56, 68, 46), 0.0, 0, 0, 0, false, helmRed));
        registerGear(new GearSpec("m4a1", "Штурмовая винтовка M4A1", Material.CROSSBOW,
                false, null, null, 11.0, 30, 36, 3, false, 0.0));
        registerGear(new GearSpec("ak74", "Автомат АК-74", Material.CROSSBOW,
                false, null, null, 12.5, 30, 38, 3, false, 0.0));
        registerGear(new GearSpec("javelin", "ПТРК «Javelin»", Material.BOW,
                false, null, null, 190.0, 1, 70, 25, true, 0.0));
    }

    private void registerVehicle(String id, ContentCategory category, Material hull, Material accent,
                                 Color color, double defaultHp, double defaultSpeed,
                                 int defaultPrimaryCd, int defaultSecondaryCd,
                                 boolean stationary, boolean airTarget) {
        String base = "vehicles." + id + ".";
        String name = plugin.getConfig().getString(base + "name", id);
        double hp = plugin.getConfig().getDouble(base + "max-hp", defaultHp);
        double speed = plugin.getConfig().getDouble(base + "speed", defaultSpeed);
        int primaryCd = plugin.getConfig().getInt(base + "ammo-cooldown-ticks", defaultPrimaryCd);
        int secondaryCd = plugin.getConfig().getInt(base + "mg-cooldown-ticks", defaultSecondaryCd);

        vehicles.put(id, new VehicleSpec(id, name, category, hull, accent, color,
                hp, speed, primaryCd, secondaryCd, stationary, airTarget));
    }

    private void registerGear(GearSpec spec) {
        gear.put(spec.id(), spec);
    }

    public VehicleSpec getVehicle(String id) {
        return id == null ? null : vehicles.get(id.toLowerCase());
    }

    public GearSpec getGear(String id) {
        return id == null ? null : gear.get(id.toLowerCase());
    }

    public Map<String, VehicleSpec> getAllVehicles() {
        return Collections.unmodifiableMap(vehicles);
    }

    public Map<String, GearSpec> getAllGear() {
        return Collections.unmodifiableMap(gear);
    }

    public List<VehicleSpec> getVehiclesByCategory(ContentCategory category) {
        List<VehicleSpec> list = new ArrayList<>();
        for (VehicleSpec spec : vehicles.values()) {
            if (spec.category() == category) {
                list.add(spec);
            }
        }
        return list;
    }

    public ItemStack createSpawnerItem(VehicleSpec spec) {
        ItemStack stack = new ItemStack(spec.category().getIcon());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6Призыв: §e" + spec.displayName());
            List<String> lore = new ArrayList<>();
            lore.add("§7Категория: §f" + spec.category().getDisplayName());
            lore.add("§7Прочность: §c" + (int) spec.maxHp() + " HP");
            lore.add("§7ПКМ по земле — развернуть технику");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(vehicleKey, PersistentDataType.STRING, spec.id());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public ItemStack createGearItem(GearSpec spec) {
        ItemStack stack = new ItemStack(spec.material());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (meta instanceof LeatherArmorMeta leatherMeta && spec.dyeColor() != null) {
                leatherMeta.setColor(spec.dyeColor());
            }
            meta.setDisplayName("§a" + spec.displayName());
            List<String> lore = new ArrayList<>();
            if (spec.armor()) {
                lore.add("§7Снижение урона: §b" + (int) (spec.damageReduction() * 100) + "%");
            } else {
                lore.add("§7Урон: §c" + spec.damage());
                lore.add("§7Магазин: §f" + spec.magazineSize() + " патр.");
                if (spec.guided()) {
                    lore.add("§7Режим: §eСамонаведение");
                }
            }
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(gearKey, PersistentDataType.STRING, spec.id());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public NamespacedKey getVehicleKey() {
        return vehicleKey;
    }

    public NamespacedKey getGearKey() {
        return gearKey;
    }
}
