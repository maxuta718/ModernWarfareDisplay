package ua.nebula.mw.model;

import org.bukkit.Color;
import org.bukkit.Material;

public record VehicleSpec(
        String id,
        String displayName,
        ContentCategory category,
        Material hullMaterial,
        Material accentMaterial,
        Color primaryColor,
        double maxHp,
        double speed,
        int primaryCooldownTicks,
        int secondaryCooldownTicks,
        boolean stationary,
        boolean airTarget
) {}
