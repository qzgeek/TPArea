package net.qzgeek.tparea;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.ListenerPriority;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * B：包引擎
 * - 为缓存中的玩家持续发 SET_SLOT 欺骗包覆盖快捷栏（所有9格标记为菜单物品）
 * - 拦截 SET_SLOT 阻止服务端真实数据到达客户端
 * - 离开时：发 9 个真实的 SET_SLOT 包（读取玩家真实库存）+ 取消拦截
 */
public class TPAreaPacketEngine {

    private final TPAreaPlugin plugin;
    private final Map<UUID, String> cache;
    private final ProtocolManager pm;
    private PacketAdapter slotBlocker;

    public TPAreaPacketEngine(TPAreaPlugin plugin, Map<UUID, String> cache) {
        this.plugin = plugin;
        this.cache = cache;
        this.pm = ProtocolLibrary.getProtocolManager();
    }

    public void start() {
        // 拦截 SET_SLOT — 只拦截快捷栏槽位(36-44)
        slotBlocker = new PacketAdapter(plugin, ListenerPriority.HIGHEST,
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

        // 每 250ms 遍历缓存，发欺骗包
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
        if (slotBlocker != null) {
            pm.removePacketListener(slotBlocker);
            slotBlocker = null;
        }
    }

    /** 发 9 个欺骗 SET_SLOT */
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

    /** 离开时：先取消拦截，再逐个真实 SET_SLOT 恢复背包 */
    public void notifyLeave(Player player) {
        // 1. 先移除拦截器（确保真实数据能到达）
        if (slotBlocker != null) {
            pm.removePacketListener(slotBlocker);
            slotBlocker = null;
        }

        // 2. 清空客户端快捷栏（发 9 个 AIR）
        for (int i = 0; i < 9; i++) {
            PacketContainer clear = pm.createPacket(PacketType.Play.Server.SET_SLOT);
            clear.getIntegers().write(0, 0);
            clear.getIntegers().write(1, 36 + i);
            clear.getItemModifier().write(0, new ItemStack(Material.AIR));
            pm.sendServerPacket(player, clear, false);
        }

        // 3. 重新注册拦截器（因为还有别的玩家在缓存中要拦截）
        if (slotBlocker == null && !cache.isEmpty()) {
            slotBlocker = new PacketAdapter(plugin, ListenerPriority.HIGHEST,
                    PacketType.Play.Server.SET_SLOT) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    Player p = event.getPlayer();
                    if (!cache.containsKey(p.getUniqueId())) return;
                    try {
                        int slot = event.getPacket().getIntegers().read(1);
                        if (slot >= 36 && slot <= 44) event.setCancelled(true);
                    } catch (Exception ignored) {}
                }
            };
            pm.addPacketListener(slotBlocker);
        }

        // 4. 读取玩家真实库存，逐个发真实 SET_SLOT
        for (int i = 0; i < 9; i++) {
            ItemStack real = player.getInventory().getItem(i);  // 注意：这里读取未修改的库存
            PacketContainer realPacket = pm.createPacket(PacketType.Play.Server.SET_SLOT);
            realPacket.getIntegers().write(0, 0);
            realPacket.getIntegers().write(1, 36 + i);
            realPacket.getItemModifier().write(0, real != null ? real : new ItemStack(Material.AIR));
            pm.sendServerPacket(player, realPacket, false);
        }

        // 5. 延迟多次重新读取库存并发送（防其他插件后续修改）
        UUID uid = player.getUniqueId();
        for (long delay : new long[]{2, 5, 10, 20}) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> {
                Player p = Bukkit.getPlayer(uid);
                if (p == null || !p.isOnline()) return;
                // 确保该玩家已不在缓存
                if (cache.containsKey(uid)) return;
                for (int i = 0; i < 9; i++) {
                    ItemStack ri = p.getInventory().getItem(i);
                    PacketContainer rp = pm.createPacket(PacketType.Play.Server.SET_SLOT);
                    rp.getIntegers().write(0, 0);
                    rp.getIntegers().write(1, 36 + i);
                    rp.getItemModifier().write(0, ri != null ? ri : new ItemStack(Material.AIR));
                    pm.sendServerPacket(p, rp, false);
                }
            }, delay);
        }
    }
}
