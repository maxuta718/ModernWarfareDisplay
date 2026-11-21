package ua.nebula.mw.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ua.nebula.mw.ModernWarfarePlugin;
import ua.nebula.mw.model.ContentCategory;
import ua.nebula.mw.model.GearSpec;
import ua.nebula.mw.model.VehicleSpec;
import ua.nebula.mw.registry.WarfareRegistry;

import java.util.ArrayList;
import java.util.List;

public class WarfareCommandAndMenu implements CommandExecutor, TabCompleter, Listener {

    private static final String MAIN_MENU_TITLE = ChatColor.DARK_GREEN + "Военный арсенал — Категории";
    private static final String CAT_MENU_PREFIX = ChatColor.DARK_BLUE + "Арсенал: ";

    private final ModernWarfarePlugin plugin;
    private final WarfareRegistry registry;

    public WarfareCommandAndMenu(ModernWarfarePlugin plugin, WarfareRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, MAIN_MENU_TITLE);
        int[] slots = {10, 11, 12, 13, 14};
        ContentCategory[] cats = ContentCategory.values();

        for (int i = 0; i < cats.length && i < slots.length; i++) {
            ContentCategory cat = cats[i];
            ItemStack stack = new ItemStack(cat.getIcon());
            ItemMeta meta = stack.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + cat.getDisplayName());
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Нажмите, чтобы открыть список");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(registry.getVehicleKey(), PersistentDataType.STRING, "cat:" + cat.name());
            stack.setItemMeta(meta);
            inv.setItem(slots[i], stack);
        }

        player.openInventory(inv);
    }

    public void openCategoryMenu(Player player, ContentCategory category) {
        Inventory inv = Bukkit.createInventory(null, 36, CAT_MENU_PREFIX + category.getDisplayName());
        int idx = 0;

        if (category == ContentCategory.GEAR) {
            for (GearSpec spec : registry.getAllGear().values()) {
                if (idx >= 27) break;
                inv.setItem(idx++, registry.createGearItem(spec));
            }
        } else {
            for (VehicleSpec spec : registry.getVehiclesByCategory(category)) {
                if (idx >= 27) break;
                inv.setItem(idx++, registry.createSpawnerItem(spec));
            }
        }

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "← Назад к категориям");
        back.setItemMeta(backMeta);
        inv.setItem(31, back);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (title.equals(MAIN_MENU_TITLE)) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;
            String tag = clicked.getItemMeta().getPersistentDataContainer().get(registry.getVehicleKey(), PersistentDataType.STRING);
            if (tag != null && tag.startsWith("cat:")) {
                ContentCategory cat = ContentCategory.valueOf(tag.substring(4));
                openCategoryMenu(player, cat);
            }
            return;
        }

        if (title.startsWith(CAT_MENU_PREFIX)) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            if (clicked.getType() == Material.ARROW) {
                openMainMenu(player);
                return;
            }

            player.getInventory().addItem(clicked.clone());
            player.sendMessage(ChatColor.GREEN + "Выдано: " + clicked.getItemMeta().getDisplayName());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Команда /mil menu доступна только игрокам.");
                return true;
            }
            openMainMenu(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("mw.admin")) {
                sender.sendMessage(ChatColor.RED + "Недостаточно прав.");
                return true;
            }
            plugin.reloadConfig();
            registry.reloadSpecs();
            sender.sendMessage(ChatColor.GREEN + "Конфигурация и реестр техники/снаряжения перезагружены.");
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("mw.admin")) {
                sender.sendMessage(ChatColor.RED + "Недостаточно прав.");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(ChatColor.YELLOW + "Использование: /mil give <игрок> <id>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Игрок не найден в сети.");
                return true;
            }
            String id = args[2].toLowerCase();
            VehicleSpec vSpec = registry.getVehicle(id);
            if (vSpec != null) {
                target.getInventory().addItem(registry.createSpawnerItem(vSpec));
                sender.sendMessage(ChatColor.GREEN + "Игроку " + target.getName() + " выдана техника: " + vSpec.displayName());
                target.sendMessage(ChatColor.GREEN + "Вам выдана техника: " + ChatColor.YELLOW + vSpec.displayName());
                return true;
            }
            GearSpec gSpec = registry.getGear(id);
            if (gSpec != null) {
                target.getInventory().addItem(registry.createGearItem(gSpec));
                sender.sendMessage(ChatColor.GREEN + "Игроку " + target.getName() + " выдано снаряжение: " + gSpec.displayName());
                target.sendMessage(ChatColor.GREEN + "Вам выдано снаряжение: " + ChatColor.YELLOW + gSpec.displayName());
                return true;
            }
            sender.sendMessage(ChatColor.RED + "Неизвестный ID: " + id);
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Использование: /mil <give|menu|reload> [игрок] [id]");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : List.of("menu", "give", "reload")) {
                if (sub.startsWith(args[0].toLowerCase())) out.add(sub);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) out.add(p.getName());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            String prefix = args[2].toLowerCase();
            for (String k : registry.getAllVehicles().keySet()) {
                if (k.startsWith(prefix)) out.add(k);
            }
            for (String k : registry.getAllGear().keySet()) {
                if (k.startsWith(prefix)) out.add(k);
            }
        }
        return out;
    }
}
