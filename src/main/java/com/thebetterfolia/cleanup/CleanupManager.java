package com.thebetterfolia.cleanup;

import com.thebetterfolia.MessageUtil;
import com.thebetterfolia.TheBetterFoliaPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CleanupManager {

    private static CleanupManager instance;
    private final TheBetterFoliaPlugin plugin;
    private final List<WarningEntry> warnings = new ArrayList<>();
    private final List<ItemStack> rubbishBin = new ArrayList<>();

    private boolean enabled;
    private int interval;
    private int secondsPassed = 0;
    private int maxPages;
    private int autoClearInterval;
    private int binClearSecondsPassed = 0;
    private int pendingWorldTasks = 0;
    private int binVersion = 0;
    private int lastCleanAmount = 0;
    private int lastCleanStacks = 0;
    private String cleanupMessage;

    public CleanupManager(TheBetterFoliaPlugin plugin) {
        this.plugin = plugin;
        instance = this;
    }

    public static CleanupManager getInstance() {
        return instance;
    }

    public void loadConfig() {
        enabled = plugin.getConfig().getBoolean("cleanup.enabled", false);
        interval = plugin.getConfig().getInt("cleanup.interval", 300);
        maxPages = plugin.getConfig().getInt("cleanup.bin.max_pages", 0);
        autoClearInterval = plugin.getConfig().getInt("cleanup.bin.auto_clear_interval", 0);
        cleanupMessage = plugin.getConfig().getString("cleanup.message",
                "<yellow>已清理 <green>{amount}</green> 个掉落物，点击 <click:run_command:/rubish><hover:show_text:'<yellow>点击打开垃圾桶'><green>此处</green></hover></click> <yellow>打开垃圾桶");
        binClearSecondsPassed = 0;
        warnings.clear();

        var warningsSection = plugin.getConfig().getConfigurationSection("cleanup.warnings");
        if (warningsSection != null) {
            for (String key : warningsSection.getKeys(false)) {
                try {
                    int time = Integer.parseInt(key);
                    String message = warningsSection.getString(key);
                    if (message != null) {
                        warnings.add(new WarningEntry(time, message));
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        warnings.sort((a, b) -> b.time - a.time);

        if (enabled) {
            startCleanupTask();
            preventVanillaDespawn();
        }
    }

    private void startCleanupTask() {
        if (plugin.isFolia()) {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, (task) -> {
                tick();
            }, 20, 20);
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        }
    }

    private void tick() {
        secondsPassed++;
        binClearSecondsPassed++;

        for (int i = 0; i < warnings.size(); i++) {
            if (secondsPassed == interval - warnings.get(i).time) {
                Component component = MessageUtil.parse(warnings.get(i).message);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendMessage(component);
                }
            }
        }

        if (secondsPassed >= interval) {
            secondsPassed = 0;
            performCleanup();
        }

        if (autoClearInterval > 0 && binClearSecondsPassed >= autoClearInterval) {
            binClearSecondsPassed = 0;
            clearBin();
        }
    }

    public void executeCleanup() {
        performCleanup();
    }

    private void performCleanup() {
        List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) return;

        lastCleanAmount = 0;
        lastCleanStacks = 0;
        pendingWorldTasks = worlds.size();

        for (World world : worlds) {
            Runnable task = () -> {
                collectWorldItems(world);
                synchronized (this) {
                    pendingWorldTasks--;
                    if (pendingWorldTasks == 0) {
                        checkBinPages();
                    }
                }
            };

        if (plugin.isFolia()) {
            Bukkit.getRegionScheduler().run(plugin, world.getSpawnLocation(), (t) -> task.run());
        } else {
            task.run();
        }
    }

    if (plugin.isFolia()) {
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, (task) -> {
            broadcastCleanup();
            incrementBinVersion();
        }, 3);
    } else {
        broadcastCleanup();
        incrementBinVersion();
    }
    }

    private void broadcastCleanup() {
        Component component = MessageUtil.parse(cleanupMessage,
                "{amount}", String.valueOf(lastCleanAmount));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(component);
        }
    }

    private void checkBinPages() {
        if (maxPages <= 0) return;
        int pages;
        synchronized (rubbishBin) {
            pages = (rubbishBin.size() + 44) / 45;
        }
        if (pages > maxPages) {
            clearBin();
        }
    }

    private void collectWorldItems(World world) {
        List<Item> items = new ArrayList<>(world.getEntitiesByClass(Item.class));
        if (items.isEmpty()) {
            synchronized (this) {
                pendingWorldTasks--;
                if (pendingWorldTasks == 0) checkBinPages();
            }
            return;
        }

        if (plugin.isFolia()) {
            collectWorldItemsFolia(world, items);
        } else {
            processItemsDirect(items);
            synchronized (this) {
                pendingWorldTasks--;
                if (pendingWorldTasks == 0) checkBinPages();
            }
        }
    }

    private void collectWorldItemsFolia(World world, List<Item> items) {
        Map<Long, List<Item>> byChunk = new HashMap<>();
        for (Item item : items) {
            Location loc = item.getLocation();
            int chunkX = loc.getBlockX() >> 4;
            int chunkZ = loc.getBlockZ() >> 4;
            long key = (long) chunkX << 32 | (chunkZ & 0xFFFFFFFFL);
            byChunk.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        }

        AtomicInteger remaining = new AtomicInteger(byChunk.size());

        for (Map.Entry<Long, List<Item>> entry : byChunk.entrySet()) {
            List<Item> chunkItems = entry.getValue();
            Location center = chunkItems.get(0).getLocation();

            Bukkit.getRegionScheduler().run(plugin, center, (t) -> {
                synchronized (rubbishBin) {
                    for (Item item : chunkItems) {
                        try {
                            ItemStack stack = item.getItemStack();
                            if (stack != null && !stack.isEmpty()) {
                                lastCleanAmount += stack.getAmount();
                                lastCleanStacks++;
                                addToBin(stack);
                            }
                        } catch (Exception ignored) {}
                    }
                    for (Item item : chunkItems) {
                        try {
                            item.remove();
                        } catch (Exception ignored) {}
                    }
                }
                synchronized (this) {
                    if (remaining.decrementAndGet() == 0) {
                        pendingWorldTasks--;
                        if (pendingWorldTasks == 0) checkBinPages();
                    }
                }
            });
        }
    }

    private void processItemsDirect(List<Item> items) {
        synchronized (rubbishBin) {
            for (Item item : items) {
                ItemStack stack = item.getItemStack();
                if (stack != null && !stack.isEmpty()) {
                    lastCleanAmount += stack.getAmount();
                    lastCleanStacks++;
                    addToBin(stack);
                }
            }
            for (Item item : items) {
                item.remove();
            }
        }
    }

    private void addToBin(ItemStack stack) {
        for (ItemStack existing : rubbishBin) {
            if (existing.isSimilar(stack) && existing.getAmount() < existing.getMaxStackSize()) {
                int space = existing.getMaxStackSize() - existing.getAmount();
                int toAdd = Math.min(space, stack.getAmount());
                existing.setAmount(existing.getAmount() + toAdd);
                stack.setAmount(stack.getAmount() - toAdd);
                if (stack.getAmount() <= 0) return;
            }
        }
        rubbishBin.add(stack.clone());
    }

    public void clearBin() {
        synchronized (rubbishBin) {
            rubbishBin.clear();
            binVersion++;
        }
        binClearSecondsPassed = 0;
    }

    public ItemStack takeItem(int index) {
        synchronized (rubbishBin) {
            if (index >= 0 && index < rubbishBin.size()) {
                ItemStack item = rubbishBin.remove(index);
                binVersion++;
                return item;
            }
            return null;
        }
    }

    public void incrementBinVersion() {
        synchronized (rubbishBin) {
            binVersion++;
        }
    }

    public int getBinVersion() {
        return binVersion;
    }

    public int getLastCleanAmount() {
        return lastCleanAmount;
    }

    public int getLastCleanStacks() {
        return lastCleanStacks;
    }

    public List<ItemStack> getRubbishBin() {
        return rubbishBin;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void shutdown() {}

    public void preventVanillaDespawn() {
        if (!enabled) return;
        for (World world : Bukkit.getWorlds()) {
            List<Item> items = new ArrayList<>(world.getEntitiesByClass(Item.class));
            if (items.isEmpty()) continue;

            if (plugin.isFolia()) {
                Map<Long, List<Item>> byChunk = new HashMap<>();
                for (Item item : items) {
                    Location loc = item.getLocation();
                    int cx = loc.getBlockX() >> 4;
                    int cz = loc.getBlockZ() >> 4;
                    long key = (long) cx << 32 | (cz & 0xFFFFFFFFL);
                    byChunk.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
                }
                for (Map.Entry<Long, List<Item>> entry : byChunk.entrySet()) {
                    Location center = entry.getValue().get(0).getLocation();
                    Bukkit.getRegionScheduler().run(plugin, center, (t) -> {
                        for (Item item : entry.getValue()) {
                            try { item.setUnlimitedLifetime(true); } catch (Exception ignored) {}
                        }
                    });
                }
            } else {
                for (Item item : items) {
                    try { item.setUnlimitedLifetime(true); } catch (Exception ignored) {}
                }
            }
        }
    }

    public static class WarningEntry {
        public final int time;
        public final String message;

        public WarningEntry(int time, String message) {
            this.time = time;
            this.message = message;
        }
    }
}
