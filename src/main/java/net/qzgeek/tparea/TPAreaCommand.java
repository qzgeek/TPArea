package net.qzgeek.tparea;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TPAreaCommand implements CommandExecutor, TabCompleter {

    private final TPAreaPlugin plugin;

    public TPAreaCommand(TPAreaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "set" -> cmdSet(sender, args);
            case "remove" -> cmdRemove(sender, args);
            case "tpmenu" -> cmdTpMenu(sender, args);
            case "debug" -> cmdDebug(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    // ====== /tparea set <name> <x1> <y1> <z1> <x2> <y2> <z2> <menu> ======
    private void cmdSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage("§c仅玩家可用"); return; }
        if (args.length < 9) { sender.sendMessage("§c用法: /tparea set <名称> <x1> <y1> <z1> <x2> <y2> <z2> <菜单名>"); return; }

        String name = args[1];
        String world = ((Player) sender).getWorld().getName();
        try {
            int x1 = Integer.parseInt(args[2]), y1 = Integer.parseInt(args[3]), z1 = Integer.parseInt(args[4]);
            int x2 = Integer.parseInt(args[5]), y2 = Integer.parseInt(args[6]), z2 = Integer.parseInt(args[7]);
            String menuName = args[8];

            if (!plugin.getMenus().containsKey(menuName)) {
                sender.sendMessage("§c菜单 " + menuName + " 不存在，请先创建！");
                return;
            }

            TPArea area = new TPArea(name, world, x1, y1, z1, x2, y2, z2, menuName);
            plugin.getAreas().put(name, area);
            plugin.saveAreas();
            sender.sendMessage("§a传送区 " + name + " 已" + (plugin.getAreas().containsKey(name) ? "更新" : "创建") + "！");
        } catch (NumberFormatException e) {
            sender.sendMessage("§c坐标必须为整数！");
        }
    }

    // ====== /tparea remove <name> ======
    private void cmdRemove(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage("§c用法: /tparea remove <名称>"); return; }
        String name = args[1];
        if (plugin.getAreas().remove(name) != null) {
            plugin.saveAreas();
            sender.sendMessage("§a传送区 " + name + " 已删除！");
        } else {
            sender.sendMessage("§c传送区 " + name + " 不存在！");
        }
    }

    // ====== /tparea tpmenu <create|delete> <name> 或 set <name> set|remove <slot> ======
    private void cmdTpMenu(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§c用法:");
            sender.sendMessage("§c  /tparea tpmenu create <菜单名>");
            sender.sendMessage("§c  /tparea tpmenu delete <菜单名>");
            sender.sendMessage("§c  /tparea tpmenu set <菜单名> set <序号1-9>");
            sender.sendMessage("§c  /tparea tpmenu set <菜单名> remove <序号1-9>");
            return;
        }

        String action = args[1].toLowerCase();
        String menuName = args[2];

        switch (action) {
            case "create" -> {
                if (plugin.getMenus().containsKey(menuName)) {
                    sender.sendMessage("§c菜单 " + menuName + " 已存在！");
                    return;
                }
                TPAreaMenu menu = new TPAreaMenu(menuName);
                plugin.getMenus().put(menuName, menu);
                plugin.saveMenus();
                sender.sendMessage("§a传送菜单 " + menuName + " 已创建！");
            }
            case "delete" -> {
                if (plugin.getMenus().remove(menuName) != null) {
                    plugin.saveMenus();
                    sender.sendMessage("§a传送菜单 " + menuName + " 已删除！");
                } else {
                    sender.sendMessage("§c菜单 " + menuName + " 不存在！");
                }
            }
            case "set" -> cmdTpMenuSet(sender, args, menuName);
            default -> sender.sendMessage("§c未知操作: " + action);
        }
    }

    private void cmdTpMenuSet(CommandSender sender, String[] args, String menuName) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§c仅玩家可用"); return; }
        if (args.length < 5) {
            sender.sendMessage("§c用法: /tparea tpmenu set <菜单名> set|remove <序号>");
            return;
        }

        TPAreaMenu menu = plugin.getMenus().get(menuName);
        if (menu == null) {
            sender.sendMessage("§c菜单 " + menuName + " 不存在！");
            return;
        }

        String op = args[3].toLowerCase();
        int slot;
        try {
            slot = Integer.parseInt(args[4]);
            if (slot < 1 || slot > 9) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sender.sendMessage("§c序号必须为 1-9 的整数！");
            return;
        }

        switch (op) {
            case "set" -> {
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand == null || hand.getType() == Material.AIR) {
                    sender.sendMessage("§c请手持一个物品来作为传送点图标！");
                    return;
                }
                TPAreaMenu.TPAreaPoint point = new TPAreaMenu.TPAreaPoint(
                    player.getWorld().getName(),
                    player.getLocation().getX(),
                    player.getLocation().getY(),
                    player.getLocation().getZ(),
                    player.getLocation().getYaw(),
                    player.getLocation().getPitch(),
                    hand.clone()
                );
                menu.setPoint(slot, point);
                plugin.saveMenus();
                sender.sendMessage("§a已设置 " + menuName + " 的第 " + slot + " 个传送点！位置: " +
                    point.world + " (" + (int)point.x + "," + (int)point.y + "," + (int)point.z + ")");
            }
            case "remove" -> {
                menu.removePoint(slot);
                plugin.saveMenus();
                sender.sendMessage("§a已删除 " + menuName + " 的第 " + slot + " 个传送点！");
            }
            default -> sender.sendMessage("§c未知操作: " + op + " (只能 set 或 remove)");
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== TPArea 传送区域插件 ===");
        sender.sendMessage("§e/tparea set <名称> <x1> <y1> <z1> <x2> <y2> <z2> <菜单名>§7 - 创建/更新传送区");
        sender.sendMessage("§e/tparea remove <名称>§7 - 删除传送区");
        sender.sendMessage("§e/tparea tpmenu create <菜单名>§7 - 创建传送菜单");
        sender.sendMessage("§e/tparea tpmenu delete <菜单名>§7 - 删除传送菜单");
        sender.sendMessage("§e/tparea tpmenu set <菜单名> set <序号1-9>§7 - 设传送点(手持物品+当前位置)");
        sender.sendMessage("§e/tparea tpmenu set <菜单名> remove <序号>§7 - 删除传送点");
        sender.sendMessage("§e/tparea debug§7 - 切换调试消息");
    }

    private void cmdDebug(CommandSender sender) {
        boolean now = plugin.toggleDebug();
        sender.sendMessage(now ? "§a调试消息已开启" : "§7调试消息已关闭");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            list.addAll(Arrays.asList("set", "remove", "tpmenu"));
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("remove")) {
                list.addAll(plugin.getAreas().keySet());
            } else if (args[0].equalsIgnoreCase("tpmenu")) {
                list.addAll(Arrays.asList("create", "delete", "set"));
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("tpmenu")) {
            if (args[1].equalsIgnoreCase("delete") || args[1].equalsIgnoreCase("set")) {
                list.addAll(plugin.getMenus().keySet());
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("tpmenu") && args[1].equalsIgnoreCase("set")) {
            list.addAll(Arrays.asList("set", "remove"));
        } else if (args.length == 5 && args[0].equalsIgnoreCase("tpmenu") && args[1].equalsIgnoreCase("set")) {
            for (int i = 1; i <= 9; i++) list.add(String.valueOf(i));
        }
        return list;
    }
}
