package net.qzgeek.tparea;

import org.bukkit.command.PluginCommand;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.List;
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

        // Register command using CommandMap (Paper 1.21+ compatible)
        TPAreaCommand cmd = new TPAreaCommand(this);
        CommandMap commandMap = getServer().getCommandMap();
        commandMap.register("tparea", new TpAreaBukkitCommand(cmd));

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
        var config = getConfig();
        config.set("areas", null);
        for (Map.Entry<String, TPArea> entry : areas.entrySet()) {
            var sec = config.createSection("areas." + entry.getKey());
            entry.getValue().saveTo(sec);
        }
        saveConfig();
    }

    public void saveMenus() {
        var config = getConfig();
        config.set("menus", null);
        for (Map.Entry<String, TPAreaMenu> entry : menus.entrySet()) {
            var sec = config.createSection("menus." + entry.getKey());
            entry.getValue().saveTo(sec);
        }
        saveConfig();
    }

    private void loadAreas() {
        var sec = getConfig().getConfigurationSection("areas");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            var areaSec = sec.getConfigurationSection(key);
            if (areaSec != null) {
                areas.put(key, TPArea.loadFrom(key, areaSec));
            }
        }
    }

    private void loadMenus() {
        var sec = getConfig().getConfigurationSection("menus");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            var menuSec = sec.getConfigurationSection(key);
            if (menuSec != null) {
                menus.put(key, TPAreaMenu.loadFrom(key, menuSec));
            }
        }
    }

    /** Our own BukkitCommand — avoids getCommand() which is deprecated on Paper 1.21+ */
    private static class TpAreaBukkitCommand extends BukkitCommand {
        private final TPAreaCommand delegate;

        TpAreaBukkitCommand(TPAreaCommand delegate) {
            super("tparea", "传送区域管理命令", "/tparea <子命令>", List.of("tpa"));
            this.delegate = delegate;
            setPermission("tparea.admin");
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            return delegate.onCommand(sender, this, label, args);
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
            return delegate.onTabComplete(sender, this, alias, args);
        }
    }
}
