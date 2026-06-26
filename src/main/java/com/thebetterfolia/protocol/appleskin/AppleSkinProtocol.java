package com.thebetterfolia.protocol.appleskin;

import com.thebetterfolia.TheBetterFoliaPlugin;
import com.thebetterfolia.protocol.ProtocolManager;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class AppleSkinProtocol implements ProtocolManager.ProtocolBase, Listener {

    public static final String SATURATION_CHANNEL = "appleskin:saturation";
    public static final String EXHAUSTION_CHANNEL = "appleskin:exhaustion";
    public static final String NATURAL_REGENERATION_CHANNEL = "appleskin:natural_regeneration";

    private static final float MINIMUM_EXHAUSTION_CHANGE_THRESHOLD = 0.01F;
    private static final long DELAY_TICKS = 1;
    private static final long PERIOD_TICKS = 1;

    private final Map<UUID, AtomicReference<ScheduledTask>> playerTasks = new ConcurrentHashMap<>();

    @Override
    public void init() {
        TheBetterFoliaPlugin.getInstance().getServer().getPluginManager().registerEvents(this, TheBetterFoliaPlugin.getInstance());
    }

    @Override
    public void disable() {
        playerTasks.clear();
    }

    @Override
    public void onPlayerQuit(Player player) {
        AtomicReference<ScheduledTask> ref = playerTasks.remove(player.getUniqueId());
        if (ref != null) {
            ScheduledTask task = ref.get();
            if (task != null) {
                task.cancel();
            }
        }
    }

    @Override
    public String getChannel() {
        return SATURATION_CHANNEL;
    }

    @EventHandler
    private void onPlayerRegisterChannel(final PlayerRegisterChannelEvent event) {
        if (event.getChannel().equals(SATURATION_CHANNEL)) {
            Player player = event.getPlayer();
            UUID uuid = player.getUniqueId();
            
            PlayerSyncRunnable runnable = new PlayerSyncRunnable(player);
            
            AtomicReference<ScheduledTask> cancelRef = new AtomicReference<>();
            playerTasks.put(uuid, cancelRef);
            
            trySchedule(player, runnable, cancelRef);
        }
    }

    private void trySchedule(Player player, PlayerSyncRunnable runnable, AtomicReference<ScheduledTask> cancelRef) {
        if (!player.isOnline()) {
            playerTasks.remove(player.getUniqueId());
            return;
        }
        
        ScheduledTask task = player.getScheduler().runAtFixedRate(
            TheBetterFoliaPlugin.getInstance(),
            ignored -> runnable.run(),
            () -> trySchedule(player, runnable, cancelRef),
            DELAY_TICKS,
            PERIOD_TICKS
        );
        
        cancelRef.set(task);
    }

    private static class PlayerSyncRunnable {
        private final Player player;
        private float previousSaturation = -1;
        private float previousExhaustion = -1;

        public PlayerSyncRunnable(Player player) {
            this.player = player;
        }

        public void run() {
            if (!player.isOnline()) {
                return;
            }

            float saturation = player.getSaturation();
            if (saturation != previousSaturation) {
                ByteBuffer buffer = ByteBuffer.allocate(Float.BYTES);
                buffer.order(ByteOrder.BIG_ENDIAN);
                buffer.putFloat(saturation);
                player.sendPluginMessage(TheBetterFoliaPlugin.getInstance(), SATURATION_CHANNEL, buffer.array());
                previousSaturation = saturation;
            }

            float exhaustion = player.getExhaustion();
            if (Math.abs(exhaustion - previousExhaustion) >= MINIMUM_EXHAUSTION_CHANGE_THRESHOLD) {
                ByteBuffer buffer = ByteBuffer.allocate(Float.BYTES);
                buffer.order(ByteOrder.BIG_ENDIAN);
                buffer.putFloat(exhaustion);
                player.sendPluginMessage(TheBetterFoliaPlugin.getInstance(), EXHAUSTION_CHANNEL, buffer.array());
                previousExhaustion = exhaustion;
            }
        }
    }
}
