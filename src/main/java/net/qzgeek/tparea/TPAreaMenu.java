package net.qzgeek.tparea;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import org.bukkit.entity.Player;

/**
 * 快捷栏"传送菜单"：包含1~9个传送点
 */
public class TPAreaMenu {
    private final String name;
    // slot 1-9 → 传送点
    private final TPAreaPoint[] points = new TPAreaPoint[10]; // index 1-9

    public TPAreaMenu(String name) {
        this.name = name;
    }

    public String getName() { return name; }

    public TPAreaPoint getPoint(int slot) {
        if (slot < 1 || slot > 9) return null;
        return points[slot];
    }

    public void setPoint(int slot, TPAreaPoint point) {
        if (slot < 1 || slot > 9) return;
        points[slot] = point;
    }

    public void removePoint(int slot) {
        if (slot < 1 || slot > 9) return;
        points[slot] = null;
    }

    /**
     * 生成该菜单的 9 格快捷栏 ItemStack 数组
     */
    public ItemStack[] generateHotbar() {
        ItemStack[] hotbar = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            TPAreaPoint pt = points[i + 1];
            if (pt != null && pt.display != null && pt.display.getType() != Material.AIR) {
                hotbar[i] = pt.display.clone();
            } else {
                hotbar[i] = new ItemStack(Material.AIR);
            }
        }
        return hotbar;
    }

    public void saveTo(ConfigurationSection section) {
        for (int i = 1; i <= 9; i++) {
            TPAreaPoint pt = points[i];
            if (pt != null) {
                ConfigurationSection ptSec = section.createSection("slot" + i);
                ptSec.set("world", pt.world);
                ptSec.set("x", pt.x);
                ptSec.set("y", pt.y);
                ptSec.set("z", pt.z);
                ptSec.set("yaw", (double) pt.yaw);
                ptSec.set("pitch", (double) pt.pitch);
                ptSec.set("display", pt.display);
            }
        }
    }

    public static TPAreaMenu loadFrom(String name, ConfigurationSection section) {
        TPAreaMenu menu = new TPAreaMenu(name);
        for (int i = 1; i <= 9; i++) {
            ConfigurationSection ptSec = section.getConfigurationSection("slot" + i);
            if (ptSec != null) {
                String world = ptSec.getString("world");
                double x = ptSec.getDouble("x");
                double y = ptSec.getDouble("y");
                double z = ptSec.getDouble("z");
                float yaw = (float) ptSec.getDouble("yaw");
                float pitch = (float) ptSec.getDouble("pitch");
                ItemStack display = ptSec.getItemStack("display");
                menu.points[i] = new TPAreaPoint(world, x, y, z, yaw, pitch, display);
            }
        }
        return menu;
    }

    /**
     * 传送点：目标位置 + 显示物品
     */
    public static class TPAreaPoint {
        public final String world;
        public final double x, y, z;
        public final float yaw, pitch;
        public final ItemStack display;

        public TPAreaPoint(String world, double x, double y, double z, float yaw, float pitch, ItemStack display) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.display = display;
        }

        public void teleport(Player player) {
            World w = Bukkit.getWorld(world);
            if (w == null) {
                player.sendMessage("§c目标世界不存在: " + world);
                return;
            }
            Location loc = new Location(w, x, y, z, yaw, pitch);
            player.teleportAsync(loc);
        }
    }
}
