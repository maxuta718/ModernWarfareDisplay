package ua.nebula.mw.model;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;

public record GearSpec(
        String id,
        String displayName,
        Material material,
        boolean armor,
        EquipmentSlot armorSlot,
        Color dyeColor,
        double damage,
        int magazineSize,
        int reloadTicks,
        int fireCooldownTicks,
        boolean guidedOrRocket,
        double damageReduction
) {}
