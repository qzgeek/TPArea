package net.qzgeek.tparea;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.Serializable;
import java.util.*;

/**
 * 传送区域：立方体区域，关联一个快捷栏菜单名
 */
public class TPArea {
    private final String name;
    private final String worldName;
    private final int x1, y1, z1, x2, y2, z2;
    private final String menuName;

    public TPArea(String name, String worldName, int x1, int y1, int z1, int x2, int y2, int z2, String menuName) {
        this.name = name;
        this.worldName = worldName;
        this.x1 = Math.min(x1, x2);
        this.y1 = Math.min(y1, y2);
        this.z1 = Math.min(z1, z2);
        this.x2 = Math.max(x1, x2);
        this.y2 = Math.max(y1, y2);
        this.z2 = Math.max(z1, z2);
        this.menuName = menuName;
    }

    public String getName() { return name; }
    public String getWorldName() { return worldName; }
    public String getMenuName() { return menuName; }

    /** 判断玩家是否在该区域范围内 */
    public boolean contains(Player player) {
        if (!player.getWorld().getName().equals(worldName)) return false;
        Location loc = player.getLocation();
        return loc.getBlockX() >= x1 && loc.getBlockX() <= x2
            && loc.getBlockY() >= y1 && loc.getBlockY() <= y2
            && loc.getBlockZ() >= z1 && loc.getBlockZ() <= z2;
    }

    public void saveTo(ConfigurationSection section) {
        section.set("world", worldName);
        section.set("x1", x1);
        section.set("y1", y1);
        section.set("z1", z1);
        section.set("x2", x2);
        section.set("y2", y2);
        section.set("z2", z2);
        section.set("menu", menuName);
    }

    public static TPArea loadFrom(String name, ConfigurationSection section) {
        return new TPArea(
            name,
            section.getString("world", "world"),
            section.getInt("x1"), section.getInt("y1"), section.getInt("z1"),
            section.getInt("x2"), section.getInt("y2"), section.getInt("z2"),
            section.getString("menu", "default")
        );
    }
}
