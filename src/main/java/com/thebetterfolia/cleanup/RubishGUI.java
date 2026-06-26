package com.thebetterfolia.cleanup;

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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RubishGUI implements Listener {

    private static final int ITEMS_PER_PAGE = 45;
    private static final int PREV_BUTTON_SLOT = 45;
    private static final int CLOSE_BUTTON_SLOT = 49;
    private static final int NEXT_BUTTON_SLOT = 53;
    private static final long CLICK_COOLDOWN_MS = 150L;

    private final Map<UUID, Long> lastClickTime = new HashMap<>();

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int page) {
        CleanupManager cm = CleanupManager.getInstance();
        List<ItemStack> bin = cm.getRubbishBin();
        int version = cm.getBinVersion();

        int totalPages;
        synchronized (bin) {
            totalPages = Math.max(1, (int) Math.ceil((double) bin.size() / ITEMS_PER_PAGE));
        }
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        RubishHolder holder = new RubishHolder(page, version);
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

        if (page > 0) {
            inv.setItem(PREV_BUTTON_SLOT, createNavItem(Material.ARROW, "§a◀ 上一页"));
        } else {
            inv.setItem(PREV_BUTTON_SLOT, createNavItem(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        inv.setItem(CLOSE_BUTTON_SLOT, createNavItem(Material.BARRIER, "§c✕ 关闭"));

        if (page < totalPages - 1) {
            inv.setItem(NEXT_BUTTON_SLOT, createNavItem(Material.ARROW, "§a下一页 ▶"));
        } else {
            inv.setItem(NEXT_BUTTON_SLOT, createNavItem(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        player.openInventory(inv);
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

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof RubishHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        event.setCancelled(true);

        long now = System.currentTimeMillis();
        Long last = lastClickTime.get(player.getUniqueId());
        if (last != null && now - last < CLICK_COOLDOWN_MS) return;
        lastClickTime.put(player.getUniqueId(), now);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        CleanupManager cm = CleanupManager.getInstance();
        List<ItemStack> bin = cm.getRubbishBin();
        int page = holder.getPage();

        if (slot < ITEMS_PER_PAGE) {
            int index = page * ITEMS_PER_PAGE + slot;
            synchronized (bin) {
                if (holder.getBinVersion() != cm.getBinVersion()) {
                    player.closeInventory();
                    open(player, page);
                    return;
                }
                if (index >= bin.size()) return;
                ItemStack item = bin.remove(index);
                if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) return;
                player.getInventory().addItem(item.clone()).values().forEach(drop ->
                        player.getWorld().dropItemNaturally(player.getLocation(), drop));
            }
            closeOtherViewers(player);
            open(player, page);
        } else if (slot == PREV_BUTTON_SLOT && page > 0) {
            if (holder.getBinVersion() != cm.getBinVersion()) {
                open(player, page);
                return;
            }
            open(player, page - 1);
        } else if (slot == NEXT_BUTTON_SLOT) {
            if (holder.getBinVersion() != cm.getBinVersion()) {
                open(player, page);
                return;
            }
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
        lastClickTime.remove(event.getPlayer().getUniqueId());
    }

    private void closeOtherViewers(Player exclude) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.equals(exclude)) continue;
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof RubishHolder) {
                p.closeInventory();
                p.sendMessage("§e垃圾桶内容已变动，请重新打开 §a/rubish §e查看");
            }
        }
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
