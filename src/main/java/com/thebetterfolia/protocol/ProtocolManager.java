package com.thebetterfolia.protocol;

import com.thebetterfolia.TheBetterFoliaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.ArrayList;
import java.util.List;

public class ProtocolManager implements Listener, PluginMessageListener {

    private static ProtocolManager instance;
    private final List<ProtocolBase> protocols = new ArrayList<>();
    private boolean paperOnlyMode = false;
    private long internalTick = 0;

    public ProtocolManager() {
        instance = this;
    }

    public static ProtocolManager getInstance() {
        return instance;
    }

    public void initialize() {
        if (!TheBetterFoliaPlugin.getInstance().isPaper()) {
            Bukkit.getLogger().warning("[ProtocolManager] Protocol support requires Paper/Folia server");
            paperOnlyMode = true;
            return;
        }

        TheBetterFoliaPlugin plugin = TheBetterFoliaPlugin.getInstance();

        if (plugin.isProtocolEnabled("appleskin")) {
            registerProtocol(new com.thebetterfolia.protocol.appleskin.AppleSkinProtocol());
            Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, "appleskin:saturation");
            Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, "appleskin:exhaustion");
            plugin.debug("[ProtocolManager] AppleSkin protocol enabled");
        }

        if (plugin.isProtocolEnabled("schematica.easy_place")) {
            registerProtocol(new LitematicaEasyPlaceProtocol());
            plugin.debug("[ProtocolManager] Litematica EasyPlace protocol enabled");
        }

        if (plugin.isProtocolEnabled("servux.entity_sync")) {
            registerProtocol(new com.thebetterfolia.protocol.servux.ServuxEntityDataProtocol());
            Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, "servux:entity_data");
            Bukkit.getMessenger().registerIncomingPluginChannel(plugin, "servux:entity_data", this);
            plugin.debug("[ProtocolManager] Servux entity sync protocol enabled");
        }

        if (plugin.isProtocolEnabled("servux.hud_sync")) {
            registerProtocol(new com.thebetterfolia.protocol.servux.ServuxHudDataProtocol());
            Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, "servux:hud_metadata");
            Bukkit.getMessenger().registerIncomingPluginChannel(plugin, "servux:hud_metadata", this);
            plugin.debug("[ProtocolManager] Servux HUD sync protocol enabled");
        }

        if (plugin.isProtocolEnabled("schematica.litematics")) {
            registerProtocol(new com.thebetterfolia.protocol.servux.ServuxLitematicsProtocol());
            Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, "servux:litematics");
            Bukkit.getMessenger().registerIncomingPluginChannel(plugin, "servux:litematics", this);
            plugin.debug("[ProtocolManager] Litematica schematic protocol enabled");
        }

        if (plugin.isProtocolEnabled("servux.structures")) {
            registerProtocol(new com.thebetterfolia.protocol.servux.ServuxStructuresProtocol());
            Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, "servux:structures");
            Bukkit.getMessenger().registerIncomingPluginChannel(plugin, "servux:structures", this);
            plugin.debug("[ProtocolManager] Servux structures protocol enabled");
        }

        if (plugin.isContainerPreviewEnabled()) {
            registerProtocol(new com.thebetterfolia.protocol.servux.ServuxTweaksProtocol());
            Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, "servux:tweaks");
            Bukkit.getMessenger().registerIncomingPluginChannel(plugin, "servux:tweaks", this);
            plugin.debug("[ProtocolManager] Container preview protocol enabled");
        }

        protocols.forEach(ProtocolBase::init);

        Bukkit.getPluginManager().registerEvents(this, plugin);

        startTickTask();
    }

    private void startTickTask() {
        if (TheBetterFoliaPlugin.getInstance().isFolia()) {
            io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler scheduler =
                    Bukkit.getGlobalRegionScheduler();
            scheduler.runAtFixedRate(TheBetterFoliaPlugin.getInstance(), (task) -> {
                internalTick++;
                long tick = internalTick;
                for (ProtocolBase p : protocols) {
                    p.tick(tick);
                }
            }, 1, 1);
        } else {
            Bukkit.getScheduler().runTaskTimer(TheBetterFoliaPlugin.getInstance(), () -> {
                internalTick++;
                long tick = internalTick;
                for (ProtocolBase p : protocols) {
                    p.tick(tick);
                }
            }, 1L, 1L);
        }
    }

    public void registerProtocol(ProtocolBase protocol) {
        protocols.add(protocol);
    }

    public void shutdown() {
        protocols.forEach(ProtocolBase::disable);
        protocols.clear();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (paperOnlyMode) return;
        Player player = event.getPlayer();
        for (ProtocolBase p : protocols) {
            p.onPlayerJoin(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (paperOnlyMode) return;
        Player player = event.getPlayer();
        for (ProtocolBase p : protocols) {
            p.onPlayerQuit(player);
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (paperOnlyMode) return;
        TheBetterFoliaPlugin plugin = TheBetterFoliaPlugin.getInstance();
        
        // Only print detailed info in debug mode
        if (plugin.isDebugEnabled()) {
            plugin.debug(
                    "[ProtocolManager DEBUG] Received from " + player.getName()
                            + " | channel: " + channel
                            + " | length: " + message.length
                            + " | first byte: " + (message.length > 0 ? (message[0] & 0xFF) : "N/A")
            );
        }
        
        for (ProtocolBase p : protocols) {
            if (channel.equals(p.getChannel()) || channel.startsWith(p.getChannel() + ":")) {
                p.onMessageReceived(player, channel, message);
            }
        }
    }

    public interface ProtocolBase {
        default void init() {}
        default void disable() {}
        default void tick(long tick) {}
        default void onPlayerJoin(Player player) {}
        default void onPlayerQuit(Player player) {}
        default void onMessageReceived(Player player, String channel, byte[] message) {}
        String getChannel();
    }
}