package net.qzgeek.tparea;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 监听玩家在区域内的一切操作 — 全部拦截（菜单状态仅响应传送）。
 * 玩家在菜单状态下：所有交互、破坏、攻击、拾取等均被取消，
 * 仅允许我们对快捷栏物品的操作触发传送。
 */
public class TPAreaListener implements Listener {

    private static final List<String> INTERACT_BLOCKED_ACTIONS = List.of(
        "left_click_block", "right_click_block", "left_click_air", "right_click_air",
        "physical"
    );

    private final TPAreaPlugin plugin;

    public TPAreaListener(TPAreaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 玩家交互（左键/右键空气或方块）— 拦截全部，若物品匹配则触发传送 */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getTracker().isInAnyArea(player)) return;
        event.setCancelled(true);

        ItemStack item = event.getItem();
        int slot = findSlot(player, item);
        if (slot >= 0) {
            teleportToSlot(player, slot);
        }
    }

    /** 丢弃物品 */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getTracker().isInAnyArea(player)) return;
        event.setCancelled(true);

        ItemStack item = event.getItemDrop().getItemStack();
        int slot = findSlot(player, item);
        if (slot >= 0) {
            teleportToSlot(player, slot);
        }
    }

    /** 背包内点击 */
    @EventHandler(priority = EventPriority.LOWEST)
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

    /** 背包拖拽 */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!plugin.getTracker().isInAnyArea(player)) return;
        event.setCancelled(true);
    }

    /** 换手（F键） */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getTracker().isInAnyArea(player)) return;
        event.setCancelled(true);
    }

    // ====== 全面拦截：区域内玩家不能做任何事 ======

    /** 禁止破坏方块 */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (plugin.getTracker().isInAnyArea(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** 禁止放置方块 */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.getTracker().isInAnyArea(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** 禁止攻击任何实体（包含玩家、怪物、掉落物） */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && plugin.getTracker().isInAnyArea(player)) {
            event.setCancelled(true);
        }
    }

    /** 禁止拾取物品 */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && plugin.getTracker().isInAnyArea(player)) {
            event.setCancelled(true);
        }
    }

    /** 禁止打开任何容器 */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && plugin.getTracker().isInAnyArea(player)) {
            event.setCancelled(true);
        }
    }

    /** 禁止吃东西/饥饿变化 */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && plugin.getTracker().isInAnyArea(player)) {
            event.setCancelled(true);
        }
    }

    /** 禁止与实体交互 */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (plugin.getTracker().isInAnyArea(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** 禁止物品掉落物被漏斗/漏斗矿车拾取（防止刷物品） */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        // 不限制此事件，仅用于记录
    }

    /** 禁止创造模式从背包拿物品 */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCreative(InventoryCreativeEvent event) {
        if (event.getWhoClicked() instanceof Player player && plugin.getTracker().isInAnyArea(player)) {
            event.setCancelled(true);
        }
    }

    /** 禁止任何伤害来源导致玩家受伤/死亡（防掉落物因死亡丢失） */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && plugin.getTracker().isInAnyArea(player)) {
            event.setCancelled(true);
        }
    }

    /** 禁止玩家使用床/重生锚 */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBed(PlayerBedEnterEvent event) {
        if (plugin.getTracker().isInAnyArea(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** 禁止钓鱼 */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onFish(PlayerFishEvent event) {
        if (plugin.getTracker().isInAnyArea(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** 禁止射箭/投掷三叉戟 */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onProjectileLaunch(org.bukkit.event.entity.ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player player && plugin.getTracker().isInAnyArea(player)) {
            event.setCancelled(true);
        }
    }

    /** 禁止玩家剪切/挤奶等实体交互 */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (plugin.getTracker().isInAnyArea(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** 禁止附魔/铁砧等界面 */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPrepareItemCraft(org.bukkit.event.inventory.PrepareItemCraftEvent event) {
        if (event.getView().getPlayer() instanceof Player player && plugin.getTracker().isInAnyArea(player)) {
            event.getInventory().setResult(null);
        }
    }

    // ====== 工具方法 ======

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
