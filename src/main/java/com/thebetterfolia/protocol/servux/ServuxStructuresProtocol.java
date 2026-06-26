package com.thebetterfolia.protocol.servux;

import com.thebetterfolia.TheBetterFoliaPlugin;
import com.thebetterfolia.protocol.NmsNbtHelper;
import com.thebetterfolia.protocol.PacketSender;
import com.thebetterfolia.protocol.ProtocolManager;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ServuxStructuresProtocol implements ProtocolManager.ProtocolBase {

    public static final String CHANNEL = "servux:structures";
    private static final int PROTOCOL_VERSION = 2;

    private final Map<Integer, UUID> players = new ConcurrentHashMap<>();

    @Override
    public String getChannel() {
        return CHANNEL;
    }

    @Override
    public void onPlayerQuit(Player bukkitPlayer) {
        players.remove(bukkitPlayer.getEntityId());
    }

    @Override
    public void onMessageReceived(Player bukkitPlayer, String channel, byte[] message) {
        if (!channel.equals(CHANNEL)) return;
        if (message.length < 1) return;
        int packetType = message[0] & 0xFF;
        
        TheBetterFoliaPlugin plugin = TheBetterFoliaPlugin.getInstance();
        
        switch (packetType) {
            case 3 -> { // PACKET_C2S_STRUCTURES_REGISTER
                players.put(bukkitPlayer.getEntityId(), bukkitPlayer.getUniqueId());
                if (plugin.isDebugEnabled()) {
                    plugin.debug("[ServuxStructures] " + bukkitPlayer.getName() + " registered");
                }
                sendMetadata(bukkitPlayer);
            }
            case 4 -> { // PACKET_C2S_STRUCTURES_UNREGISTER
                players.remove(bukkitPlayer.getEntityId());
                if (plugin.isDebugEnabled()) {
                    plugin.debug("[ServuxStructures] " + bukkitPlayer.getName() + " unregistered");
                }
            }
        }
    }

    private void sendMetadata(Player bukkitPlayer) {
        try {
            NmsNbtHelper.sendMetadata(bukkitPlayer, CHANNEL, "structure_bounding_boxes", CHANNEL, PROTOCOL_VERSION, ServuxBase.SERVUX_VERSION);
            
            TheBetterFoliaPlugin plugin = TheBetterFoliaPlugin.getInstance();
            if (plugin.isDebugEnabled()) {
                plugin.debug("[ServuxStructures] Sent metadata to " + bukkitPlayer.getName());
            }
        } catch (Exception e) {
            TheBetterFoliaPlugin.getInstance().getLogger().warning("[ServuxStructures] Sending metadata failed: " + e.getMessage());
            if (TheBetterFoliaPlugin.getInstance().isDebugEnabled()) {
                e.printStackTrace();
            }
        }
    }
}