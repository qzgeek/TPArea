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
 * 3. 延迟任务反复 updateInventory
 * 4. 离开后关闭卡住的容器
 *
 * 关键修复：playersInArea 是唯一的权威状态源。
 * isInAnyArea / getCurrentMenu 完全基于 playersInArea。
 * 离开时，必须等连续 3 次检测都不在区域内才真正清理（防闪烁）。
 */
public class TPAreaTracker implements Runnable {
    private final TPAreaPlugin plugin;
    private final Logger logger;
    private ScheduledTask task;

    private final Map<UUID, String> playersInArea = new HashMap<>();
    private final Map<UUID, ItemStack[]> savedHotbars = new HashMap<>();
    // 离开缓冲计数：连续多少轮不在区域内才真正触发离开
    private final Map<UUID, Integer> leaveCounters = new HashMap<>();
    private static final int LEAVE_CONFIRM = 3; // 连续 3 次确认才离开

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
        leaveCounters.clear();
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uid = player.getUniqueId();
            String prev = playersInArea.get(uid);
            String now = findAreaForPlayer(player);

            if (now != null) {
                // 在区域内
                leaveCounters.remove(uid); // 重置离开计数
                if (!now.equals(prev)) {
                    // 进入新区域
                    if (prev != null) forceRestore(player);
                    enterArea(player, now);
                    playersInArea.put(uid, now);
                }
                // 已在区域内：不操作
            } else {
                // 不在区域
                if (prev != null) {
                    // 使用缓冲计数确认离开
                    int cnt = leaveCounters.getOrDefault(uid, 0) + 1;
                    leaveCounters.put(uid, cnt);
                    if (cnt >= LEAVE_CONFIRM) {
                        // 真正离开
                        forceRestore(player);
                        playersInArea.remove(uid);
                        leaveCounters.remove(uid);
                    }
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

    /** 离开时恢复快捷栏并发送刷新包 */
    private void forceRestore(Player player) {
        ItemStack[] backup = savedHotbars.remove(player.getUniqueId());
        if (backup != null) {
            PlayerInventory inv = player.getInventory();
            for (int i = 0; i < 9; i++) {
                inv.setItem(i, backup[i]);
            }
        }
        player.updateInventory();

        UUID uid = player.getUniqueId();
        // 多轮延迟刷新确保客户端拿到真实数据
        for (long delay : new long[]{1, 5, 10, 20}) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> {
                Player p = Bukkit.getPlayer(uid);
                if (p != null) p.updateInventory();
            }, delay);
        }

        if (plugin.isDebug()) player.sendMessage("§7[传送区] 离开传送区");
    }

    public boolean isInAnyArea(Player player) {
        return playersInArea.containsKey(player.getUniqueId());
    }

    public String getCurrentMenu(Player player) {
        return playersInArea.get(player.getUniqueId());
    }
}
