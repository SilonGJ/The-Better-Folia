package com.thebetterfolia.cleanup;

import com.thebetterfolia.TheBetterFoliaPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class RubishGUI implements Listener {

    private static final int ITEMS_PER_PAGE = 45;
    private static final int PREV_BUTTON_SLOT = 45;
    private static final int CLOSE_BUTTON_SLOT = 49;
    private static final int NEXT_BUTTON_SLOT = 53;
    private static final long CLICK_COOLDOWN_MS = 150L;
    private static final long REFRESH_INTERVAL = 20L;

    private final Map<UUID, Long> lastClickTime = new HashMap<>();
    private final Map<UUID, ViewerData> viewers = new HashMap<>();
    private boolean refreshTaskStarted = false;

    private static class ViewerData {
        int page;
        int binVersion;

        ViewerData(int page, int binVersion) {
            this.page = page;
            this.binVersion = binVersion;
        }
    }

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int page) {
        CleanupManager cm = CleanupManager.getInstance();
        List<ItemStack> bin = cm.getRubbishBin();
        int version = cm.getBinVersion();
        int viewVersion = version;

        int totalPages;
        synchronized (bin) {
            totalPages = Math.max(1, (int) Math.ceil((double) bin.size() / ITEMS_PER_PAGE));
        }
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        RubishHolder holder = new RubishHolder(page, viewVersion);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("§l公共垃圾桶 - 第" + (page + 1) + "页"));
        holder.setInventory(inv);

        int startIndex = page * ITEMS_PER_PAGE;
        synchronized (bin) {
            for (int i = 0; i < ITEMS_PER_PAGE; i++) {
                int index = startIndex + i;
                if (index < bin.size()) {
                    inv.setItem(i, bin.get(index));
                } else {
                    break;
                }
            }
        }

        inv.setItem(PREV_BUTTON_SLOT, page > 0 ? createNavItem(Material.ARROW, "§a◀ 上一页") : createNavItem(Material.GRAY_STAINED_GLASS_PANE, " "));
        inv.setItem(CLOSE_BUTTON_SLOT, createNavItem(Material.BARRIER, "§c✕ 关闭"));
        inv.setItem(NEXT_BUTTON_SLOT, page < totalPages - 1 ? createNavItem(Material.ARROW, "§a下一页 ▶") : createNavItem(Material.GRAY_STAINED_GLASS_PANE, " "));

        player.openInventory(inv);
        viewers.put(player.getUniqueId(), new ViewerData(page, version));
        startRefreshTask();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof RubishHolder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        event.setCancelled(true);

        long now = System.currentTimeMillis();
        Long last = lastClickTime.get(player.getUniqueId());
        if (last != null && now - last < CLICK_COOLDOWN_MS) return;
        lastClickTime.put(player.getUniqueId(), now);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        CleanupManager cm = CleanupManager.getInstance();
        ViewerData data = viewers.get(player.getUniqueId());
        if (data == null) return;
        int page = data.page;

        if (slot < ITEMS_PER_PAGE) {
            int index = page * ITEMS_PER_PAGE + slot;
            ItemStack taken = cm.takeItem(index);
            if (taken == null || taken.getType() == Material.AIR || taken.getAmount() <= 0) return;

            player.getInventory().addItem(taken.clone()).values().forEach(drop ->
                    player.getWorld().dropItemNaturally(player.getLocation(), drop));

            refreshAllViewers();
        } else if (slot == PREV_BUTTON_SLOT && page > 0) {
            open(player, page - 1);
        } else if (slot == NEXT_BUTTON_SLOT) {
            List<ItemStack> bin = cm.getRubbishBin();
            int totalPages;
            synchronized (bin) {
                totalPages = Math.max(1, (int) Math.ceil((double) bin.size() / ITEMS_PER_PAGE));
            }
            if (page < totalPages - 1) {
                open(player, page + 1);
            }
        } else if (slot == CLOSE_BUTTON_SLOT) {
            player.closeInventory();
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof RubishHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastClickTime.remove(uuid);
        viewers.remove(uuid);
    }

    private void refreshAllViewers() {
        CleanupManager cm = CleanupManager.getInstance();
        List<ItemStack> bin = cm.getRubbishBin();

        viewers.entrySet().removeIf(entry -> {
            Player p = Bukkit.getPlayer(entry.getKey());
            return p == null || !p.isOnline() || !(p.getOpenInventory().getTopInventory().getHolder() instanceof RubishHolder);
        });

        for (Map.Entry<UUID, ViewerData> entry : viewers.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) continue;
            refreshViewer(player, entry.getValue(), bin, cm);
        }
    }

    private void refreshViewer(Player player, ViewerData data, List<ItemStack> bin, CleanupManager cm) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        data.binVersion = cm.getBinVersion();

        int totalPages;
        synchronized (bin) {
            totalPages = Math.max(1, (int) Math.ceil((double) bin.size() / ITEMS_PER_PAGE));
        }
        if (data.page >= totalPages) data.page = Math.max(0, totalPages - 1);
        if (data.page < 0) data.page = 0;

        int startIndex = data.page * ITEMS_PER_PAGE;
        synchronized (bin) {
            for (int i = 0; i < ITEMS_PER_PAGE; i++) {
                int index = startIndex + i;
                if (index < bin.size()) {
                    inv.setItem(i, bin.get(index));
                } else {
                    inv.setItem(i, null);
                }
            }
        }

        inv.setItem(PREV_BUTTON_SLOT, data.page > 0 ? createNavItem(Material.ARROW, "§a◀ 上一页") : createNavItem(Material.GRAY_STAINED_GLASS_PANE, " "));
        inv.setItem(NEXT_BUTTON_SLOT, data.page < totalPages - 1 ? createNavItem(Material.ARROW, "§a下一页 ▶") : createNavItem(Material.GRAY_STAINED_GLASS_PANE, " "));
    }

    private void startRefreshTask() {
        if (refreshTaskStarted) return;
        refreshTaskStarted = true;

        TheBetterFoliaPlugin plugin = TheBetterFoliaPlugin.getInstance();
        if (plugin.isFolia()) {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> refreshStaleViewers(), REFRESH_INTERVAL, REFRESH_INTERVAL);
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, this::refreshStaleViewers, REFRESH_INTERVAL, REFRESH_INTERVAL);
        }
    }

    private void refreshStaleViewers() {
        if (viewers.isEmpty()) return;

        CleanupManager cm = CleanupManager.getInstance();
        List<ItemStack> bin = cm.getRubbishBin();
        int currentVersion = cm.getBinVersion();

        viewers.entrySet().removeIf(entry -> {
            Player p = Bukkit.getPlayer(entry.getKey());
            return p == null || !p.isOnline() || !(p.getOpenInventory().getTopInventory().getHolder() instanceof RubishHolder);
        });

        for (Map.Entry<UUID, ViewerData> entry : viewers.entrySet()) {
            if (entry.getValue().binVersion != currentVersion) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null) {
                    refreshViewer(player, entry.getValue(), bin, cm);
                }
            }
        }
    }

    private ItemStack createNavItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    public static class RubishHolder implements InventoryHolder {
        private final int page;
        private final int binVersion;
        private Inventory inventory;

        public RubishHolder(int page, int binVersion) {
            this.page = page;
            this.binVersion = binVersion;
        }

        public int getPage() {
            return page;
        }

        public int getBinVersion() {
            return binVersion;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
