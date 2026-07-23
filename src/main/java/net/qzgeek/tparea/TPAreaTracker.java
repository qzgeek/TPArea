package net.qzgeek.tparea;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;  // Folia compat

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.logging.Logger;

/**
 * 核心逻辑：检测玩家在区域中 → 覆盖快捷栏；离开 → 恢复
 *
 * 使用"客户端快捷栏覆盖"方式：不修改玩家数据，只创建自定义快捷栏物品供玩家交互。
 * 玩家点击/丢弃等操作触发传送。
 */
public class TPAreaTracker implements Runnable {
    private final TPAreaPlugin plugin;
    private final Logger logger;
    private ScheduledTask task;

    // 当前在区域内的玩家 → 他们看到的菜单名
    private final Map<UUID, String> playersInArea = new HashMap<>();

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
        // 恢复所有玩家的快捷栏
        for (UUID uid : new ArrayList<>(playersInArea.keySet())) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null) restorePlayer(p);
        }
        playersInArea.clear();
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uid = player.getUniqueId();
            String currentMenu = playersInArea.get(uid);

            // 查找玩家当前所在的区域
            String menuInArea = findAreaForPlayer(player);

            if (menuInArea != null) {
                // 在区域内
                if (!menuInArea.equals(currentMenu)) {
                    // 进入新区域 或 切换到另一个区域
                    if (currentMenu != null) restorePlayer(player);
                    coverPlayer(player, menuInArea);
                    playersInArea.put(uid, menuInArea);
                }
                // 已在同一区域：不操作
            } else {
                if (currentMenu != null) {
                    // 离开了区域
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

    /** 覆盖玩家快捷栏为菜单 */
    private void coverPlayer(Player player, String menuName) {
        TPAreaMenu menu = plugin.getMenus().get(menuName);
        if (menu == null) return;
        ItemStack[] hotbar = menu.generateHotbar();
        // 只在快捷栏(0-8)覆盖，不影响背包
        for (int i = 0; i < 9; i++) {
            player.getInventory().setItem(i, hotbar[i]);
        }
        player.sendMessage("§a[传送区] 你已进入传送区 " + menuName + "，快捷栏已切换为传送菜单。");
    }

    /** 恢复玩家快捷栏 — 通过清除并让客户端重新同步 */
    private void restorePlayer(Player player) {
        // 清除快捷栏
        for (int i = 0; i < 9; i++) {
            player.getInventory().setItem(i, null);
        }
        player.updateInventory();
        player.sendMessage("§7[传送区] 已离开传送区，快捷栏已恢复。");
    }

    public boolean isInAnyArea(Player player) {
        return playersInArea.containsKey(player.getUniqueId());
    }

    /** 获取玩家当前所在菜单名 */
    public String getCurrentMenu(Player player) {
        return playersInArea.get(player.getUniqueId());
    }
}
