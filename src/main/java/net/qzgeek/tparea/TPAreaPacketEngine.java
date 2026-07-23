package net.qzgeek.tparea;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * B：读取缓存，向在缓存中的玩家发欺骗包。
 * 每 250ms 遍历缓存中的玩家，发送 SET_SLOT 覆盖客户端快捷栏。
 * 拦截 SET_SLOT 屏蔽服务器真实数据下发给缓存玩家。
 * 监听到 A 的离开通知 → 取消拦截 + 触发 updateInventory 恢复。
 */
public class TPAreaPacketEngine {

    private final TPAreaPlugin plugin;
    private final Map<UUID, String> cache; // A 的缓存引用
    private final ProtocolManager pm;
    private PacketAdapter slotBlocker;

    public TPAreaPacketEngine(TPAreaPlugin plugin, Map<UUID, String> cache) {
        this.plugin = plugin;
        this.cache = cache;
        this.pm = ProtocolLibrary.getProtocolManager();
    }

    public void start() {
        // 注册 SET_SLOT 拦截器：阻止真实快捷栏数据到达缓存玩家
        slotBlocker = new PacketAdapter(plugin, com.comphenix.protocol.events.ListenerPriority.HIGHEST,
                PacketType.Play.Server.SET_SLOT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Player player = event.getPlayer();
                if (!cache.containsKey(player.getUniqueId())) return;
                try {
                    int slot = event.getPacket().getIntegers().read(1);
                    if (slot >= 36 && slot <= 44) {
                        event.setCancelled(true);
                    }
                } catch (Exception ignored) {}
            }
        };
        pm.addPacketListener(slotBlocker);

        // 每 250ms 遍历缓存，发欺骗包覆盖快捷栏
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> {
            for (Map.Entry<UUID, String> entry : cache.entrySet()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player == null || !player.isOnline()) continue;
                TPAreaMenu menu = plugin.getMenus().get(entry.getValue());
                if (menu == null) continue;
                sendFakeSlots(player, menu.generateHotbar());
            }
        }, 5, 5);
    }

    public void stop() {
        if (slotBlocker != null) pm.removePacketListener(slotBlocker);
    }

    /** 向玩家发 9 个 SET_SLOT 覆盖快捷栏 */
    private void sendFakeSlots(Player player, ItemStack[] hotbar) {
        for (int i = 0; i < 9; i++) {
            PacketContainer p = pm.createPacket(PacketType.Play.Server.SET_SLOT);
            p.getIntegers().write(0, 0);
            p.getIntegers().write(1, 36 + i);
            p.getItemModifier().write(0, (i < hotbar.length && hotbar[i] != null && hotbar[i].getType() != Material.AIR)
                    ? hotbar[i] : new ItemStack(Material.AIR));
            pm.sendServerPacket(player, p, false);
        }
    }

    /** 被 A 调用：通知玩家离开，恢复真实背包 */
    public void notifyLeave(Player player) {
        // 取消拦截 + 强制刷新
        pm.sendServerPacket(player, pm.createPacket(PacketType.Play.Server.WINDOW_ITEMS), false);
        player.updateInventory();
        // 多次延迟刷新
        UUID uid = player.getUniqueId();
        for (long delay : new long[]{1, 5, 10, 20}) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> {
                Player p = Bukkit.getPlayer(uid);
                if (p != null) p.updateInventory();
            }, delay);
        }
    }
}
