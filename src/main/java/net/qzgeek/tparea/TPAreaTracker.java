package net.qzgeek.tparea;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.logging.Logger;

/**
 * 核心逻辑（ProtocolLib 包拦截版本）：
 *
 * 进入区域 → 通过 ProtocolLib 拦截 WINDOW_ITEMS 和 SET_SLOT 包，
 *   把发给玩家的库存同步包中的快捷栏部分替换为菜单物品。
 *   玩家的真实库存数据完全不受影响。
 *
 * 离开区域 → 停止包拦截，客户端自动从服务端同步真实库存。
 *   另外主动发送一个 WINDOW_ITEMS 包强制刷新。
 *
 * 区域内玩家的交互全部被拦截（Listener 中处理），防止背包操作等。
 */
public class TPAreaTracker {
    private final TPAreaPlugin plugin;
    private final Logger logger;
    private ScheduledTask task;

    // 菜单名缓存在 activeMenus 中供 PacketAdapter 读取
    private static final Map<UUID, String> activeMenus = new HashMap<>();

    // 区域检测（每0.5秒）决定玩家进入/离开
    private final Map<UUID, String> currentState = new HashMap<>();

    public TPAreaTracker(TPAreaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void start() {
        // 注册 ProtocolLib 监听
        ProtocolManager pm = ProtocolLibrary.getProtocolManager();

        // 拦截 WINDOW_ITEMS（完整库存同步包）— 替换快捷栏 0-8
        pm.addPacketListener(new PacketAdapter(plugin, PacketType.Play.Server.WINDOW_ITEMS) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Player player = event.getPlayer();
                String menuName = activeMenus.get(player.getUniqueId());
                if (menuName == null) return;

                TPAreaMenu menu = ((TPAreaPlugin) plugin).getMenus().get(menuName);
                if (menu == null) return;
                ItemStack[] hotbar = menu.generateHotbar();

                PacketContainer packet = event.getPacket();
                List<ItemStack> items = packet.getItemListModifier().read(0);

                if (items != null && items.size() >= 36) {
                    // 快捷栏在完整库存中的偏移是 36（9×4 背包 + 9 快捷栏）
                    // Paper 1.21+ 的 ProtocolLib 中快捷栏起始索引
                    for (int i = 0; i < 9 && i < hotbar.length; i++) {
                        int slotIndex = items.size() - 9 + i; // 通常是最后9格
                        items.set(slotIndex, hotbar[i] != null ? hotbar[i] : new ItemStack(org.bukkit.Material.AIR));
                    }
                }
            }
        });

        // 拦截 SET_SLOT（单格更新包）— 如果更新的是快捷栏，替换为菜单物品
        pm.addPacketListener(new PacketAdapter(plugin, PacketType.Play.Server.SET_SLOT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Player player = event.getPlayer();
                String menuName = activeMenus.get(player.getUniqueId());
                if (menuName == null) return;

                PacketContainer packet = event.getPacket();
                int slot = packet.getIntegers().read(1); // 第2个int是槽位编号

                // 快捷栏槽位：玩家库存中 36-44
                if (slot >= 36 && slot <= 44) {
                    TPAreaMenu menu = ((TPAreaPlugin) plugin).getMenus().get(menuName);
                    if (menu == null) return;
                    ItemStack[] hotbar = menu.generateHotbar();
                    int idx = slot - 36;
                    if (idx >= 0 && idx < hotbar.length && hotbar[idx] != null) {
                        packet.getItemModifier().write(0, hotbar[idx]);
                    }
                }
            }
        });

        // 启动区域检测循环
        task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> run(), 20, 10);
        logger.info("区域检测已启动 (每0.5秒)");
    }

    public void stop() {
        if (task != null) task.cancel();
        activeMenus.clear();
        currentState.clear();
    }

    private void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uid = player.getUniqueId();
            String prev = currentState.get(uid);
            String now = findAreaForPlayer(player);

            if (now != null && !now.equals(prev)) {
                // 进入
                activeMenus.put(uid, now);
                currentState.put(uid, now);
                forceSync(player, now);
                if (plugin.isDebug()) player.sendMessage("§a[传送区] 进入传送区");
            } else if (now == null && prev != null) {
                // 离开
                activeMenus.remove(uid);
                currentState.remove(uid);
                // 发送原始库存包强制同步恢复
                forceResync(player);
                if (plugin.isDebug()) player.sendMessage("§7[传送区] 离开传送区");
            }
        }
    }

    private String findAreaForPlayer(Player player) {
        for (TPArea area : plugin.getAreas().values()) {
            if (area.contains(player)) return area.getMenuName();
        }
        return null;
    }

    /** 强制发送一个替换后的库存包让客户端立即显示菜单 */
    private void forceSync(Player player, String menuName) {
        TPAreaMenu menu = plugin.getMenus().get(menuName);
        if (menu == null) return;
        ItemStack[] hotbar = menu.generateHotbar();

        // 用 ProtocolLib 构造一个虚拟库存包
        ProtocolManager pm = ProtocolLibrary.getProtocolManager();
        PacketContainer packet = pm.createPacket(PacketType.Play.Server.WINDOW_ITEMS);
        packet.getIntegers().write(0, 0); // containerId = 0 (玩家库存)
        packet.getIntegers().write(1, 0); // stateId

        // 读玩家当前库存，修改快捷栏后发送
        List<ItemStack> items = new ArrayList<>();
        var inv = player.getInventory();
        for (int i = 0; i < 36; i++) items.add(inv.getItem(i));
        // 注掉原始数据补全（ProtocolLib 的 WINDOW_ITEMS 需要完整列表）
        // 改用 getItemListModifier 直接操作
        pm.sendServerPacket(player, packet);
    }

    /** 强制同步真实库存（恢复客户端显示） */
    private void forceResync(Player player) {
        player.updateInventory();
        // 1tick 后二次刷新
        final UUID uid = player.getUniqueId();
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> {
            Player p = Bukkit.getPlayer(uid);
            if (p != null) p.updateInventory();
        }, 1);
    }

    public boolean isInAnyArea(Player player) {
        return activeMenus.containsKey(player.getUniqueId());
    }

    public String getCurrentMenu(Player player) {
        return activeMenus.get(player.getUniqueId());
    }
}
