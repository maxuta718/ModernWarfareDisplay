package ua.nebula.mw.display;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ua.nebula.mw.ModernWarfarePlugin;
import ua.nebula.mw.model.ActiveVehicle;
import ua.nebula.mw.model.ActiveVehicle.PartRole;
import ua.nebula.mw.model.ActiveVehicle.RigNode;
import ua.nebula.mw.model.ContentCategory;
import ua.nebula.mw.model.VehicleSpec;

import java.util.UUID;

public class DisplayRigManager {

    private final ModernWarfarePlugin plugin;

    public DisplayRigManager(ModernWarfarePlugin plugin) {
        this.plugin = plugin;
    }

    public ActiveVehicle spawnRig(VehicleSpec spec, Location spawnLoc) {
        World world = spawnLoc.getWorld();
        if (world == null) {
            throw new IllegalStateException("World cannot be null when spawning vehicle rig");
        }

        UUID vehicleId = UUID.randomUUID();

        Interaction seat = world.spawn(spawnLoc, Interaction.class, entity -> {
            entity.setInteractionWidth(2.4f);
            entity.setInteractionHeight(1.6f);
            entity.setResponsive(true);
            entity.setPersistent(false);
        });

        TextDisplay hpDisplay = world.spawn(spawnLoc.clone().add(0, 2.8, 0), TextDisplay.class, display -> {
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(false);
            display.setShadowed(true);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setPersistent(false);
            display.text(net.kyori.adventure.text.Component.text(formatHpLabel(spec.displayName(), spec.maxHp(), spec.maxHp())));
        });

        ActiveVehicle vehicle = new ActiveVehicle(vehicleId, spec, spawnLoc, seat, hpDisplay, spec.maxHp());

        buildCategoryRig(vehicle, world, spawnLoc);
        updateRigTransforms(vehicle);
        return vehicle;
    }

    private void buildCategoryRig(ActiveVehicle vehicle, World world, Location base) {
        VehicleSpec spec = vehicle.getSpec();
        ContentCategory category = spec.category();

        switch (category) {
            case GROUND -> {
                addBlockNode(vehicle, world, base, PartRole.HULL, spec.hullMaterial(),
                        new Vector3f(0f, 0.35f, 0f), new Vector3f(2.2f, 0.85f, 3.6f), new Vector3f(0f, 0f, 0f));
                addBlockNode(vehicle, world, base, PartRole.TURRET, spec.accentMaterial(),
                        new Vector3f(0f, 1.35f, -0.1f), new Vector3f(1.45f, 0.65f, 1.8f), new Vector3f(0f, 0f, 0f));
                addBlockNode(vehicle, world, base, PartRole.BARREL, Material.BLACKSTONE,
                        new Vector3f(0f, 1.55f, 1.6f), new Vector3f(0.24f, 0.24f, 2.4f), new Vector3f(0f, 0f, 0f));
                addBlockNode(vehicle, world, base, PartRole.EXHAUST, Material.GRAY_CONCRETE,
                        new Vector3f(0.75f, 1.05f, -1.6f), new Vector3f(0.25f, 0.45f, 0.25f), new Vector3f(0f, 0f, 0f));
            }
            case AIR -> {
                addBlockNode(vehicle, world, base, PartRole.HULL, spec.hullMaterial(),
                        new Vector3f(0f, 0.4f, 0f), new Vector3f(1.3f, 0.9f, 4.4f), new Vector3f(0f, 0f, 0f));
                addBlockNode(vehicle, world, base, PartRole.WING_LEFT, spec.accentMaterial(),
                        new Vector3f(-1.9f, 0.45f, -0.3f), new Vector3f(2.6f, 0.14f, 1.5f), new Vector3f(0f, 0f, 0f));
                addBlockNode(vehicle, world, base, PartRole.WING_RIGHT, spec.accentMaterial(),
                        new Vector3f(1.9f, 0.45f, -0.3f), new Vector3f(2.6f, 0.14f, 1.5f), new Vector3f(0f, 0f, 0f));
                addBlockNode(vehicle, world, base, PartRole.TAIL, spec.hullMaterial(),
                        new Vector3f(0f, 1.15f, -2.0f), new Vector3f(0.22f, 1.1f, 0.9f), new Vector3f(0f, 0f, 0f));
                if ("mi24".equalsIgnoreCase(spec.id())) {
                    addBlockNode(vehicle, world, base, PartRole.PROPELLER_1, Material.DARK_OAK_PLANKS,
                            new Vector3f(0f, 1.45f, 0.2f), new Vector3f(3.8f, 0.08f, 0.22f), new Vector3f(0f, 0f, 0f));
                }
            }
            case DRONES -> {
                addBlockNode(vehicle, world, base, PartRole.HULL, spec.hullMaterial(),
                        new Vector3f(0f, 0.25f, 0f), new Vector3f(0.75f, 0.25f, 0.85f), new Vector3f(0f, 0f, 0f));
                addBlockNode(vehicle, world, base, PartRole.PROPELLER_1, spec.accentMaterial(),
                        new Vector3f(-0.55f, 0.42f, 0.55f), new Vector3f(0.55f, 0.05f, 0.1f), new Vector3f(0f, 0f, 0f));
                addBlockNode(vehicle, world, base, PartRole.PROPELLER_2, spec.accentMaterial(),
                        new Vector3f(0.55f, 0.42f, 0.55f), new Vector3f(0.55f, 0.05f, 0.1f), new Vector3f(0f, 0f, 0f));
                addBlockNode(vehicle, world, base, PartRole.PROPELLER_3, spec.accentMaterial(),
                        new Vector3f(-0.55f, 0.42f, -0.55f), new Vector3f(0.55f, 0.05f, 0.1f), new Vector3f(0f, 0f, 0f));
                addBlockNode(vehicle, world, base, PartRole.PROPELLER_4, spec.accentMaterial(),
                        new Vector3f(0.55f, 0.42f, -0.55f), new Vector3f(0.55f, 0.05f, 0.1f), new Vector3f(0f, 0f, 0f));
            }
            case AA -> {
                if (spec.stationary()) {
                    addBlockNode(vehicle, world, base, PartRole.HULL, spec.hullMaterial(),
                            new Vector3f(0f, 0.3f, 0f), new Vector3f(2.4f, 0.6f, 2.4f), new Vector3f(0f, 0f, 0f));
                    addBlockNode(vehicle, world, base, PartRole.LAUNCHER, spec.accentMaterial(),
                            new Vector3f(0f, 1.25f, 0f), new Vector3f(1.5f, 1.4f, 1.5f), new Vector3f(-35f, 0f, 0f));
                    addBlockNode(vehicle, world, base, PartRole.RADAR, Material.LIGHT_GRAY_CONCRETE,
                            new Vector3f(0f, 2.35f, -0.5f), new Vector3f(1.2f, 0.55f, 0.25f), new Vector3f(0f, 0f, 0f));
                } else {
                    addBlockNode(vehicle, world, base, PartRole.HULL, spec.hullMaterial(),
                            new Vector3f(0f, 0.35f, 0f), new Vector3f(2.1f, 0.85f, 3.4f), new Vector3f(0f, 0f, 0f));
                    addBlockNode(vehicle, world, base, PartRole.TURRET, spec.accentMaterial(),
                            new Vector3f(0f, 1.35f, 0f), new Vector3f(1.5f, 0.7f, 1.7f), new Vector3f(0f, 0f, 0f));
                    addBlockNode(vehicle, world, base, PartRole.BARREL, Material.IRON_BLOCK,
                            new Vector3f(-0.85f, 1.45f, 1.1f), new Vector3f(0.18f, 0.18f, 2.1f), new Vector3f(-15f, 0f, 0f));
                    addBlockNode(vehicle, world, base, PartRole.RADAR, Material.WHITE_CONCRETE,
                            new Vector3f(0.85f, 1.55f, 0.4f), new Vector3f(0.55f, 0.75f, 0.25f), new Vector3f(0f, 0f, 0f));
                }
            }
            case GEAR -> addBlockNode(vehicle, world, base, PartRole.HULL, spec.hullMaterial(),
                    new Vector3f(0f, 0.3f, 0f), new Vector3f(1.2f, 0.8f, 1.2f), new Vector3f(0f, 0f, 0f));
        }
    }

    private void addBlockNode(ActiveVehicle vehicle, World world, Location base, PartRole role,
                              Material material, Vector3f localOffset, Vector3f scale, Vector3f eulerDeg) {
        BlockData blockData = material.createBlockData();
        BlockDisplay display = world.spawn(base, BlockDisplay.class, d -> {
            d.setBlock(blockData);
            d.setInterpolationDuration(2);
            d.setTeleportDuration(2);
            d.setPersistent(false);
        });
        vehicle.getNodes().add(new RigNode(role, display, localOffset, scale, eulerDeg));
    }

    @SuppressWarnings("unused")
    private void addItemNode(ActiveVehicle vehicle, World world, Location base, PartRole role,
                             ItemStack stack, Vector3f localOffset, Vector3f scale, Vector3f eulerDeg) {
        ItemDisplay display = world.spawn(base, ItemDisplay.class, d -> {
            d.setItemStack(stack);
            d.setInterpolationDuration(2);
            d.setTeleportDuration(2);
            d.setPersistent(false);
        });
        vehicle.getNodes().add(new RigNode(role, display, localOffset, scale, eulerDeg));
    }

    public void updateRigTransforms(ActiveVehicle vehicle) {
        Location loc = vehicle.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        float yawRad = (float) Math.toRadians(-vehicle.getYaw());
        float pitchRad = (float) Math.toRadians(vehicle.getPitch());
        float rollRad = (float) Math.toRadians(vehicle.getRoll());

        Matrix4f bodyRot = new Matrix4f()
                .rotateY(yawRad)
                .rotateX(pitchRad)
                .rotateZ(rollRad);

        for (RigNode node : vehicle.getNodes()) {
            Display entity = node.entity();
            if (entity == null || !entity.isValid()) continue;

            Vector3f offset = new Vector3f(node.localOffset());
            float extraYawDeg = 0f;
            float extraPitchDeg = 0f;

            if (node.role() == PartRole.TURRET || node.role() == PartRole.BARREL) {
                extraYawDeg = vehicle.getTurretYawOffset();
            } else if (node.role() == PartRole.RADAR) {
                extraYawDeg = vehicle.getRotorAngle() * 0.5f;
            } else if (node.role() == PartRole.PROPELLER_1 || node.role() == PartRole.PROPELLER_2
                    || node.role() == PartRole.PROPELLER_3 || node.role() == PartRole.PROPELLER_4) {
                extraYawDeg = vehicle.getRotorAngle() * 3.5f;
            }

            Vector3f rotatedOffset = new Vector3f(offset);
            bodyRot.transformPosition(rotatedOffset);

            Location partLoc = new Location(world,
                    loc.getX() + rotatedOffset.x(),
                    loc.getY() + rotatedOffset.y(),
                    loc.getZ() + rotatedOffset.z(),
                    vehicle.getYaw(),
                    vehicle.getPitch());

            if (!entity.getLocation().equals(partLoc)) {
                entity.teleport(partLoc);
            }

            Quaternionf partQuat = new Quaternionf()
                    .rotationXYZ(
                            (float) Math.toRadians(node.eulerDeg().x() + extraPitchDeg),
                            (float) Math.toRadians(node.eulerDeg().y() + extraYawDeg),
                            (float) Math.toRadians(node.eulerDeg().z())
                    );

            Vector3f halfScale = new Vector3f(node.scale());
            Vector3f centeredTranslation = new Vector3f(
                    -halfScale.x() * 0.5f,
                    0f,
                    -halfScale.z() * 0.5f
            );

            Transformation transform = new Transformation(
                    centeredTranslation,
                    partQuat,
                    halfScale,
                    new Quaternionf()
            );

            entity.setInterpolationDelay(0);
            entity.setInterpolationDuration(2);
            entity.setTeleportDuration(2);
            entity.setTransformation(transform);
        }

        if (vehicle.getSeat() != null && vehicle.getSeat().isValid()) {
            Location seatLoc = new Location(world, loc.getX(), loc.getY() + 0.65, loc.getZ(), vehicle.getYaw(), vehicle.getPitch());
            vehicle.getSeat().teleport(seatLoc);
        }

        if (vehicle.getHpDisplay() != null && vehicle.getHpDisplay().isValid()) {
            Location hpLoc = new Location(world, loc.getX(), loc.getY() + 2.95, loc.getZ());
            vehicle.getHpDisplay().teleport(hpLoc);
            vehicle.getHpDisplay().text(net.kyori.adventure.text.Component.text(
                    formatHpLabel(vehicle.getSpec().displayName(), vehicle.getHp(), vehicle.getMaxHp())
            ));
        }
    }

    public static String formatHpLabel(String name, double hp, double maxHp) {
        int current = Math.max(0, (int) Math.ceil(hp));
        int max = Math.max(1, (int) Math.ceil(maxHp));
        double ratio = Math.max(0.0, Math.min(1.0, hp / max));
        String color = ratio > 0.6 ? "§a" : (ratio > 0.3 ? "§e" : "§c");
        return "§f" + name + " " + color + current + "§7/§f" + max + " HP";
    }
}
