package net.qzgeek.tparea;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * A：区域检测，不与 tick 挂钩，用独立线程每 500ms 检测。
 * 检测到玩家进入 → 加入缓存
 * 检测到玩家离开 → 从缓存移除 → 通知 B 恢复
 *
 * 缓存是一个 ConcurrentHashMap，由 A 写入，由 B（TPAreaPacketEngine）读取。
 * A 完全不碰玩家库存。
 */
public class TPAreaTracker {

    private final TPAreaPlugin plugin;
    private final Logger logger;
    private final Map<UUID, String> cache = new ConcurrentHashMap<>();
    private final TPAreaPacketEngine engine;
    private java.util.concurrent.ScheduledExecutorService executor;
    private java.util.concurrent.ScheduledFuture<?> future;

    public TPAreaTracker(TPAreaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.engine = new TPAreaPacketEngine(plugin, cache);
    }

    public void start() {
        engine.start();
        executor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TPArea-Detector");
            t.setDaemon(true);
            return t;
        });
        future = executor.scheduleAtFixedRate(this::detect, 0, 500, TimeUnit.MILLISECONDS);
        logger.info("[A] 区域检测已启动 (每500ms, 独立线程)");
    }

    public void stop() {
        if (future != null) future.cancel(false);
        if (executor != null) executor.shutdown();
        engine.stop();
        // 通知所有缓存玩家恢复
        for (UUID uid : new ArrayList<>(cache.keySet())) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null) engine.notifyLeave(p);
        }
        cache.clear();
    }

    private void detect() {
        // 当前在线玩家有哪些在区域内
        Set<UUID> found = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            String menu = findAreaForPlayer(player);
            if (menu != null) {
                found.add(player.getUniqueId());
                // 不在缓存中 → 新进入
                if (!cache.containsKey(player.getUniqueId())) {
                    cache.put(player.getUniqueId(), menu);
                }
            }
        }
        // 缓存中有但检测不在 → 离开
        for (UUID uid : new ArrayList<>(cache.keySet())) {
            if (!found.contains(uid)) {
                cache.remove(uid);
                Player player = Bukkit.getPlayer(uid);
                if (player != null) {
                    engine.notifyLeave(player);
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

    public boolean isInAnyArea(Player player) {
        return cache.containsKey(player.getUniqueId());
    }

    public String getCurrentMenu(Player player) {
        return cache.get(player.getUniqueId());
    }
}
