package com.thebetterfolia.protocol.servux;

import com.thebetterfolia.TheBetterFoliaPlugin;
import com.thebetterfolia.protocol.NmsNbtHelper;
import com.thebetterfolia.protocol.PacketSender;
import com.thebetterfolia.protocol.ProtocolManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ServuxHudDataProtocol implements ProtocolManager.ProtocolBase {

    public static final String CHANNEL = "servux:hud_metadata";
    private static final int PROTOCOL_VERSION = 2;
    
    private final Set<UUID> activePlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> loggers = ConcurrentHashMap.newKeySet();
    
    private long lastTickTime;
    private final long[] tickTimes = new long[100];
    private int tickIndex = 0;
    private int tickCounter = 0;

    @Override
    public String getChannel() {
        return CHANNEL;
    }

    @Override
    public void init() {
        lastTickTime = System.nanoTime();
    }

    @Override
    public void onPlayerJoin(Player bukkitPlayer) {
        sendMetadata(bukkitPlayer);
    }

    @Override
    public void onPlayerQuit(Player bukkitPlayer) {
        activePlayers.remove(bukkitPlayer.getUniqueId());
        loggers.remove(bukkitPlayer.getUniqueId());
    }

    @Override
    public void onMessageReceived(Player bukkitPlayer, String channel, byte[] message) {
        if (!channel.equals(CHANNEL)) return;
        if (message.length < 1) return;
        int packetType = message[0] & 0xFF;
        
        TheBetterFoliaPlugin plugin = TheBetterFoliaPlugin.getInstance();
        
        switch (packetType) {
            case 2 -> {
                activePlayers.add(bukkitPlayer.getUniqueId());
                if (plugin.isDebugEnabled()) {
                    plugin.debug("[ServuxHudData] " + bukkitPlayer.getName() + " subscribed to HUD data");
                }
                sendMetadata(bukkitPlayer);
            }
            case 8 -> {
                loggers.add(bukkitPlayer.getUniqueId());
                if (plugin.isDebugEnabled()) {
                    plugin.debug("[ServuxHudData] " + bukkitPlayer.getName() + " registered logger");
                }
            }
            case 9 -> {
                loggers.remove(bukkitPlayer.getUniqueId());
                if (plugin.isDebugEnabled()) {
                    plugin.debug("[ServuxHudData] " + bukkitPlayer.getName() + " unregistered logger");
                }
            }
        }
    }

    @Override
    public void tick(long tick) {
        if (activePlayers.isEmpty() && loggers.isEmpty()) return;
        
        tickCounter++;
        
        // Update tick time
        long now = System.nanoTime();
        long tickTime = now - lastTickTime;
        lastTickTime = now;
        tickTimes[tickIndex % tickTimes.length] = tickTime;
        tickIndex++;
        
        // Send data every ServuxBase.HUD_UPDATE_INTERVAL ticks
        if (tickCounter % ServuxBase.HUD_UPDATE_INTERVAL == 0) {
            sendHudUpdate();
        }
    }

    private void sendMetadata(Player bukkitPlayer) {
        try {
            NmsNbtHelper.sendMetadata(bukkitPlayer, CHANNEL, "hud_data", CHANNEL, PROTOCOL_VERSION, ServuxBase.SERVUX_VERSION);
            
            TheBetterFoliaPlugin plugin = TheBetterFoliaPlugin.getInstance();
            if (plugin.isDebugEnabled()) {
                plugin.debug("[ServuxHudData] Sent metadata to " + bukkitPlayer.getName());
            }
        } catch (Exception e) {
            TheBetterFoliaPlugin.getInstance().getLogger().warning("[ServuxHudData] Sending metadata failed: " + e.getMessage());
            if (TheBetterFoliaPlugin.getInstance().isDebugEnabled()) {
                e.printStackTrace();
            }
        }
    }

    private double calculateTps(int samples) {
        int count = Math.min(tickIndex, samples);
        if (count == 0) return 20.0;
        
        long totalTime = 0;
        for (int i = 0; i < count; i++) {
            int idx = (tickIndex - 1 - i + tickTimes.length) % tickTimes.length;
            totalTime += tickTimes[idx];
        }
        
        double avgTickTimeNs = totalTime / (double) count;
        double tps = 1e9 / avgTickTimeNs;
        return Math.min(20.0, tps);
    }

    private double calculateMspt(int samples) {
        int count = Math.min(tickIndex, samples);
        if (count == 0) return 0.0;
        
        long totalTime = 0;
        for (int i = 0; i < count; i++) {
            int idx = (tickIndex - 1 - i + tickTimes.length) % tickTimes.length;
            totalTime += tickTimes[idx];
        }
        
        return (totalTime / (double) count) / 1e6;
    }

    private void sendHudUpdate() {
        try {
            double tps5s = calculateTps(100);
            double mspt5s = calculateMspt(100);
            
            Object tag = NmsNbtHelper.createCompoundTag();
            
            NmsNbtHelper.putInt(tag, "tps", (int) Math.round(tps5s * 100));
            NmsNbtHelper.putInt(tag, "mspt", (int) Math.round(mspt5s * 100));
            
            long time = Bukkit.getWorlds().get(0).getTime();
            NmsNbtHelper.putInt(tag, "time", (int) time);
            
            byte[] data = NmsNbtHelper.serializePacket(3, tag);
            
            for (UUID uuid : activePlayers) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    PacketSender.sendPayload(player, CHANNEL, data);
                }
            }
        } catch (Exception e) {
            if (TheBetterFoliaPlugin.getInstance().getConfig().getBoolean("debug", false)) {
                TheBetterFoliaPlugin.getInstance().getLogger().warning("[ServuxHudData] Sending HUD update failed: " + e.getMessage());
            }
        }
    }
}
