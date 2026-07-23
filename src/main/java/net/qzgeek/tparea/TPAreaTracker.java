package net.qzgeek.tparea;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * 核心逻辑：玩家进入传送区 → 替换快捷栏；离开 → 恢复
 *
 * 采用"备份+恢复"方案。离开区域时发送 updateInventory 强制客户端同步。
 * 每次进入区域前先做完整备份，离开时从备份恢复。
 * 区域内玩家所有交互均被拦截（Listener 中处理）。
 *
 * 关键修复：setContents 完全恢复 + updateInventory + 1tick 延迟强制刷新。
 */
public class TPAreaTracker implements Runnable {
    private final TPAreaPlugin plugin;
    private final Logger logger;
    private ScheduledTask task;

    private final Map<UUID, String> playersInArea = new HashMap<>();
    // 完整库存备份（39格 + 5格盔甲 + 副手 = 41格，但只需存快捷栏即可）
    private final Map<UUID, ItemStack[]> savedHotbars = new HashMap<>();

    public TPAreaTracker(TPAreaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void start() {
        task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> run(), 20, 10);
        logger.info("区域检测已启动 (每0.5秒)");
    }

    public void stop() {
        if (task != null) task.cancel();
        for (UUID uid : new ArrayList<>(playersInArea.keySet())) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null) restorePlayer(p);
        }
        playersInArea.clear();
        savedHotbars.clear();
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uid = player.getUniqueId();
            String currentMenu = playersInArea.get(uid);
            String menuInArea = findAreaForPlayer(player);

            if (menuInArea != null) {
                if (!menuInArea.equals(currentMenu)) {
                    if (currentMenu != null) restorePlayer(player);
                    coverPlayer(player, menuInArea);
                    playersInArea.put(uid, menuInArea);
                }
            } else {
                if (currentMenu != null) {
                    restorePlayer(player);
                    playersInArea.remove(uid);
                }
            }
        }
    }

    private String findAreaForPlayer(Player player) {
        for (TPArea area : plugin.getAreas().values()) {
            if (area.contains(player)) return area.getMenuName();
        }
        return null;
    }

    private void coverPlayer(Player player, String menuName) {
        // 备份原快捷栏
        if (!savedHotbars.containsKey(player.getUniqueId())) {
            ItemStack[] backup = new ItemStack[9];
            PlayerInventory inv = player.getInventory();
            for (int i = 0; i < 9; i++) {
                backup[i] = inv.getItem(i);
            }
            savedHotbars.put(player.getUniqueId(), backup);
        }

        TPAreaMenu menu = plugin.getMenus().get(menuName);
        if (menu == null) return;
        ItemStack[] hotbar = menu.generateHotbar();
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, hotbar[i]);
        }
        player.sendMessage("§a[传送区] 你已进入传送区，快捷栏已切换为传送菜单。");
    }

    private void restorePlayer(Player player) {
        ItemStack[] backup = savedHotbars.remove(player.getUniqueId());
        if (backup != null) {
            PlayerInventory inv = player.getInventory();
            for (int i = 0; i < 9; i++) {
                inv.setItem(i, backup[i]);
            }
            // 强制客户端同步
            player.updateInventory();
            // Paper/Folia：有些情况 updateInventory 不够，用 1tick 延迟任务触发重新同步
            final UUID uid = player.getUniqueId();
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> {
                Player p = Bukkit.getPlayer(uid);
                if (p != null) p.updateInventory();
            }, 1);
        }
        player.sendMessage("§7[传送区] 已离开传送区，快捷栏已恢复。");
    }

    public boolean isInAnyArea(Player player) {
        return playersInArea.containsKey(player.getUniqueId());
    }

    public String getCurrentMenu(Player player) {
        return playersInArea.get(player.getUniqueId());
    }
}
