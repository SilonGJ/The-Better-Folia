package com.thebetterfolia.protocol;

import com.thebetterfolia.TheBetterFoliaPlugin;
import org.bukkit.entity.Player;

public class PacketSender {

    public static void sendPayload(Player player, String channel, byte[] data) {
        try {
            player.sendPluginMessage(TheBetterFoliaPlugin.getInstance(), channel, data);
            return;
        } catch (Throwable e) {
            TheBetterFoliaPlugin.getInstance().getLogger().warning("[PacketSender] Bukkit API send failed: " + e.getMessage());
        }
    }

    public static boolean isAvailable() {
        return true;
    }
}