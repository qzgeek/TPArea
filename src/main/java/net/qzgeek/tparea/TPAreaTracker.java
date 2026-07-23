package net.qzgeek.tparea;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.ListenerOptions;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class TPAreaTracker {
    private final TPAreaPlugin plugin;
    private final Logger logger;
    private ScheduledTask task;

    private static final Map<UUID, String> activeMenus = new HashMap<>();
    private final Map<UUID, String> currentState = new HashMap<>();

    // ProtocolLib listeners reference for unregister
    private PacketAdapter slotListener;

    public TPAreaTracker(TPAreaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void start() {
        ProtocolManager pm = ProtocolLibrary.getProtocolManager();

        // 只拦截 SET_SLOT — 阻止快捷栏真实数据下发给客户端
        slotListener = new PacketAdapter(plugin, com.comphenix.protocol.events.ListenerPriority.NORMAL, PacketType.Play.Server.SET_SLOT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Player player = event.getPlayer();
                if (!activeMenus.containsKey(player.getUniqueId())) return;

                PacketContainer packet = event.getPacket();
                try {
                    // SET_SLOT packet: getIntegers().read(0)=containerId, read(1)=slot
                    int slot = packet.getIntegers().read(1);
                    if (slot >= 36 && slot <= 44) {
                        event.setCancelled(true);
                    }
                } catch (Exception ignored) {}
            }
        };
        pm.addPacketListener(slotListener);

        // 区域检测循环: 每 5 tick = 250ms
        task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> run(), 20, 5);
        logger.info("区域检测已启动 (每0.25秒)");
    }

    public void stop() {
        if (task != null) task.cancel();
        if (slotListener != null) {
            ProtocolLibrary.getProtocolManager().removePacketListener(slotListener);
        }
        activeMenus.clear();
        currentState.clear();
    }

    private void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uid = player.getUniqueId();
            String prev = currentState.get(uid);
            String now = findAreaForPlayer(player);

            if (now != null && !now.equals(prev)) {
                activeMenus.put(uid, now);
                currentState.put(uid, now);
                sendMenuPackets(player, now);
                if (plugin.isDebug()) player.sendMessage("§a[传送区] 进入传送区");
            } else if (now == null && prev != null) {
                activeMenus.remove(uid);
                currentState.remove(uid);
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

    /** 发送 9 个虚假 SET_SLOT 包覆盖客户端快捷栏 */
    private void sendMenuPackets(Player player, String menuName) {
        TPAreaMenu menu = plugin.getMenus().get(menuName);
        if (menu == null) return;
        ItemStack[] hotbar = menu.generateHotbar();
        ProtocolManager pm = ProtocolLibrary.getProtocolManager();

        for (int i = 0; i < 9; i++) {
            PacketContainer packet = pm.createPacket(PacketType.Play.Server.SET_SLOT);
            packet.getIntegers().write(0, 0);
            packet.getIntegers().write(1, 36 + i);
            ItemStack item = (i < hotbar.length && hotbar[i] != null && hotbar[i].getType() != Material.AIR)
                ? hotbar[i] : new ItemStack(Material.AIR);
            packet.getItemModifier().write(0, item);
            pm.sendServerPacket(player, packet, false);
        }
    }

    /** 强制恢复 */
    private void forceResync(Player player) {
        player.updateInventory();
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
