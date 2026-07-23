package net.qzgeek.tparea;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * 监听玩家在区域内对快捷栏物品的操作 → 触发传送
 */
public class TPAreaListener implements Listener {

    private final TPAreaPlugin plugin;

    public TPAreaListener(TPAreaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 玩家交互（左键/右键物品）触发传送 */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getTracker().isInAnyArea(player)) return;
        event.setCancelled(true); // 阻止原版交互（放置/挖掘等）

        ItemStack item = event.getItem();
        int slot = findSlot(player, item);
        if (slot >= 0) {
            teleportToSlot(player, slot);
        }
        event.setCancelled(false);
    }

    /** 丢弃物品触发传送 */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getTracker().isInAnyArea(player)) return;
        event.setCancelled(true);

        ItemStack item = event.getItemDrop().getItemStack();
        int slot = findSlot(player, item);
        if (slot >= 0) {
            teleportToSlot(player, slot);
        } else {
            player.sendMessage("§c[传送区] 无法丢弃物品！");
        }
    }

    /** 背包点击（移动物品等）触发传送 */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!plugin.getTracker().isInAnyArea(player)) return;
        event.setCancelled(true);

        ItemStack current = event.getCurrentItem();
        int slot = findSlot(player, current);
        if (slot >= 0) {
            teleportToSlot(player, slot);
        }
    }

    /** 拖拽物品 */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!plugin.getTracker().isInAnyArea(player)) return;
        event.setCancelled(true);
    }

    /** 换手（F键） */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getTracker().isInAnyArea(player)) return;
        event.setCancelled(true);
    }

    /** 在快捷栏中查找物品对应的传送槽位 */
    private int findSlot(Player player, ItemStack item) {
        if (item == null) return -1;
        String currentMenu = plugin.getTracker().getCurrentMenu(player);
        if (currentMenu == null) return -1;
        TPAreaMenu menu = plugin.getMenus().get(currentMenu);
        if (menu == null) return -1;
        for (int i = 1; i <= 9; i++) {
            TPAreaMenu.TPAreaPoint pt = menu.getPoint(i);
            if (pt != null && pt.display != null && pt.display.isSimilar(item)) {
                return i;
            }
        }
        return -1;
    }

    private void teleportToSlot(Player player, int slot) {
        String currentMenu = plugin.getTracker().getCurrentMenu(player);
        if (currentMenu == null) return;
        TPAreaMenu menu = plugin.getMenus().get(currentMenu);
        if (menu == null) return;
        TPAreaMenu.TPAreaPoint pt = menu.getPoint(slot);
        if (pt == null) return;
        pt.teleport(player);
        player.sendMessage("§a[传送] 已传送到 " + currentMenu + " 的传送点 " + slot + "。");
    }
}
