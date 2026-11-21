package ua.nebula.mw.model;

import org.bukkit.Material;

public enum ContentCategory {
    GROUND("Наземная техника", Material.GREEN_CONCRETE),
    AIR("Воздушная техника", Material.LIGHT_BLUE_CONCRETE),
    DRONES("Дроны (БПЛА)", Material.BLACK_CONCRETE),
    AA("ПВО / ППО", Material.IRON_BLOCK),
    GEAR("Оружие и броня", Material.CROSSBOW);

    private final String displayName;
    private final Material icon;

    ContentCategory(String displayName, Material icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }
}
