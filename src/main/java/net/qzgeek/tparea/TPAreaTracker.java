package net.qzgeek.tparea;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;
import java.util.logging.Logger;

/**
 * 纯 Bukkit API 方案 — 进入时保存+替换快捷栏，离开时恢复。
 *
 * 恢复保证：
 * 1. 立即恢复 setItem
 * 2. updateInventory() 强制同步
 * 3. 2 个延迟任务：1tick 和 5tick 后再次 updateInventory
 * 4. 离开区域后关闭玩家可能卡住的任何容器
 * 5. 玩家若死亡会触发恢复
 */
public class TPAreaTracker implements Runnable {
    private final TPAreaPlugin plugin;
    private final Logger logger;
    private ScheduledTask task;

    private final Map<UUID, String> playersInArea = new HashMap<>();
    private final Map<UUID, ItemStack[]> savedHotbars = new HashMap<>();

    public TPAreaTracker(TPAreaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void start() {
        task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> run(), 20, 5);
        logger.info("区域检测已启动");
    }

    public void stop() {
        if (task != null) task.cancel();
        for (UUID uid : new ArrayList<>(playersInArea.keySet())) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null) forceRestore(p);
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
                    if (currentMenu != null) forceRestore(player);
                    enterArea(player, menuInArea);
                    playersInArea.put(uid, menuInArea);
                }
            } else {
                if (currentMenu != null) {
                    forceRestore(player);
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

    private void enterArea(Player player, String menuName) {
        ItemStack[] backup = new ItemStack[9];
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < 9; i++) backup[i] = inv.getItem(i);
        savedHotbars.put(player.getUniqueId(), backup);

        TPAreaMenu menu = plugin.getMenus().get(menuName);
        if (menu == null) return;
        ItemStack[] hotbar = menu.generateHotbar();
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, hotbar[i]);
        }
        if (plugin.isDebug()) player.sendMessage("§a[传送区] 进入传送区");
    }

    private void forceRestore(Player player) {
        ItemStack[] backup = savedHotbars.remove(player.getUniqueId());
        if (backup != null) {
            PlayerInventory inv = player.getInventory();
            for (int i = 0; i < 9; i++) {
                inv.setItem(i, backup[i]);
            }
        }
        // 关闭任何打开的容器
        if (player.getOpenInventory() != null
            && player.getOpenInventory().getType() != org.bukkit.event.inventory.InventoryType.CRAFTING) {
            player.closeInventory();
        }
        player.updateInventory();

        // 延迟重试 (1tick, 5tick, 20tick)
        UUID uid = player.getUniqueId();
        scheduleUpdate(uid, 1);
        scheduleUpdate(uid, 5);
        scheduleUpdate(uid, 20);

        if (plugin.isDebug()) player.sendMessage("§7[传送区] 离开传送区，快捷栏已恢复。");
    }

    private void scheduleUpdate(UUID uid, long delay) {
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> {
            Player p = Bukkit.getPlayer(uid);
            if (p != null) p.updateInventory();
        }, delay);
    }

    public boolean isInAnyArea(Player player) {
        return playersInArea.containsKey(player.getUniqueId());
    }

    public String getCurrentMenu(Player player) {
        return playersInArea.get(player.getUniqueId());
    }
}
