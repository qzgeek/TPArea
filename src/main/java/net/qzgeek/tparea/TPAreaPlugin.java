package net.qzgeek.tparea;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;

public class TPAreaPlugin extends JavaPlugin {

    private final Map<String, TPArea> areas = new LinkedHashMap<>();
    private final Map<String, TPAreaMenu> menus = new LinkedHashMap<>();
    private TPAreaTracker tracker;

    @Override
    public void onEnable() {
        // Load config
        saveDefaultConfig();

        // Load data
        loadAreas();
        loadMenus();

        // Register commands
        TPAreaCommand cmd = new TPAreaCommand(this);
        getCommand("tparea").setExecutor(cmd);
        getCommand("tparea").setTabCompleter(cmd);

        // Register listeners
        getServer().getPluginManager().registerEvents(new TPAreaListener(this), this);

        // Start tracker
        tracker = new TPAreaTracker(this);
        tracker.start();

        getLogger().info("TPArea v" + getDescription().getVersion() + " 已启用！区域: " + areas.size() + "，菜单: " + menus.size());
    }

    @Override
    public void onDisable() {
        if (tracker != null) tracker.stop();
        getLogger().info("TPArea 已禁用");
    }

    // ====== Data Access ======

    public Map<String, TPArea> getAreas() { return areas; }
    public Map<String, TPAreaMenu> getMenus() { return menus; }
    public TPAreaTracker getTracker() { return tracker; }

    // ====== Persistence ======

    public void saveAreas() {
        FileConfiguration config = getConfig();
        config.set("areas", null);
        for (Map.Entry<String, TPArea> entry : areas.entrySet()) {
            ConfigurationSection sec = config.createSection("areas." + entry.getKey());
            entry.getValue().saveTo(sec);
        }
        saveConfig();
    }

    public void saveMenus() {
        FileConfiguration config = getConfig();
        config.set("menus", null);
        for (Map.Entry<String, TPAreaMenu> entry : menus.entrySet()) {
            ConfigurationSection sec = config.createSection("menus." + entry.getKey());
            entry.getValue().saveTo(sec);
        }
        saveConfig();
    }

    private void loadAreas() {
        ConfigurationSection sec = getConfig().getConfigurationSection("areas");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection areaSec = sec.getConfigurationSection(key);
            if (areaSec != null) {
                areas.put(key, TPArea.loadFrom(key, areaSec));
            }
        }
    }

    private void loadMenus() {
        ConfigurationSection sec = getConfig().getConfigurationSection("menus");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection menuSec = sec.getConfigurationSection(key);
            if (menuSec != null) {
                menus.put(key, TPAreaMenu.loadFrom(key, menuSec));
            }
        }
    }
}
