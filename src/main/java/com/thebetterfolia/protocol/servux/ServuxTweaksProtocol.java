package com.thebetterfolia.protocol.servux;

import com.thebetterfolia.TheBetterFoliaPlugin;
import com.thebetterfolia.protocol.NmsNbtHelper;
import com.thebetterfolia.protocol.PacketSender;
import com.thebetterfolia.protocol.ProtocolManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ServuxTweaksProtocol implements ProtocolManager.ProtocolBase {
    public static final String CHANNEL = "servux:tweaks";
    private static final int PROTOCOL_VERSION = 1;

    private final Map<UUID, Long> readingSessionKeys = new HashMap<>();

    @Override
    public String getChannel() {
        return CHANNEL;
    }

    @Override
    public void init() {
    }

    @Override
    public void onPlayerJoin(Player player) {
        sendMetadata(player);
    }

    @Override
    public void onMessageReceived(Player player, String channel, byte[] message) {
        if (!channel.equals(CHANNEL)) return;
        if (message.length < 1) return;

        VarIntReader reader = new VarIntReader(message);
        int packetType = reader.readVarInt();

        TheBetterFoliaPlugin plugin = TheBetterFoliaPlugin.getInstance();
        if (plugin.isDebugEnabled()) {
            plugin.debug("[ServuxTweaks] " + player.getName() + " received packet type " + packetType);
        }

        switch (packetType) {
            case 2 -> { // PACKET_C2S_METADATA_REQUEST
                sendMetadata(player);
            }
            case 3 -> { // PACKET_C2S_BLOCK_ENTITY_REQUEST
                handleBlockEntityRequest(player, reader);
            }
            case 4 -> { // PACKET_C2S_ENTITY_REQUEST
                handleEntityRequest(player, reader);
            }
            case 13 -> { // PACKET_C2S_NBT_RESPONSE_DATA
                handleNbtResponseData(player);
            }
            default -> {
                if (plugin.isDebugEnabled()) {
                    plugin.getLogger().warning("[ServuxTweaks] Unknown packet type: " + packetType);
                }
            }
        }
    }

    private void handleBlockEntityRequest(Player player, VarIntReader reader) {
        try {
            reader.readVarInt(); // skip transaction id
            BlockPos pos = reader.readBlockPos();

            TheBetterFoliaPlugin plugin = TheBetterFoliaPlugin.getInstance();
            if (plugin.isDebugEnabled()) {
                plugin.debug("[ServuxTweaks] " + player.getName() + " requested block entity: " + pos.x + ", " + pos.y + ", " + pos.z);
            }

            if (TheBetterFoliaPlugin.getInstance().isFolia()) {
                player.getScheduler().execute(TheBetterFoliaPlugin.getInstance(), () -> {
                    sendBlockEntityResponse(player, player.getWorld(), pos);
                }, null, 1);
            } else {
                Bukkit.getScheduler().runTask(TheBetterFoliaPlugin.getInstance(), () -> {
                    sendBlockEntityResponse(player, player.getWorld(), pos);
                });
            }
        } catch (Exception e) {
            TheBetterFoliaPlugin.getInstance().getLogger().warning("[ServuxTweaks] Handling block entity request failed: " + e.getMessage());
            if (TheBetterFoliaPlugin.getInstance().isDebugEnabled()) {
                e.printStackTrace();
            }
        }
    }

    private void sendBlockEntityResponse(Player player, World world, BlockPos pos) {
        try {
            Object nmsPos = NmsNbtHelper.createBlockPos(pos.x, pos.y, pos.z);
            Object nbt = NmsNbtHelper.getBlockEntityNbt(world, nmsPos);
            byte[] data = NmsNbtHelper.serializeTweaksBlockEntityPacket(nmsPos, nbt);
            PacketSender.sendPayload(player, CHANNEL, data);
            
            TheBetterFoliaPlugin plugin = TheBetterFoliaPlugin.getInstance();
            if (plugin.isDebugEnabled()) {
                plugin.debug("[ServuxTweaks] Sent block entity NBT to " + player.getName());
            }
        } catch (Exception e) {
            TheBetterFoliaPlugin.getInstance().getLogger().warning("[ServuxTweaks] Sending block entity response failed: " + e.getMessage());
            if (TheBetterFoliaPlugin.getInstance().isDebugEnabled()) {
                e.printStackTrace();
            }
        }
    }

    private void handleEntityRequest(Player player, VarIntReader reader) {
        try {
            reader.readVarInt(); // skip transaction id
            int entityId = reader.readVarInt();

            TheBetterFoliaPlugin plugin = TheBetterFoliaPlugin.getInstance();
            
            // Check entity/player preview toggle
            if (!plugin.isPreviewEntitiesEnabled()) {
                if (plugin.isDebugEnabled()) {
                    plugin.debug("[ServuxTweaks] " + player.getName() + " entity request " + entityId + " rejected (preview_entities=false)");
                }
                return;
            }
            
            if (plugin.isDebugEnabled()) {
                plugin.debug("[ServuxTweaks] " + player.getName() + " requested entity: " + entityId);
            }

            if (TheBetterFoliaPlugin.getInstance().isFolia()) {
                player.getScheduler().execute(TheBetterFoliaPlugin.getInstance(), () -> {
                    sendEntityResponse(player, player.getWorld(), entityId);
                }, null, 1);
            } else {
                Bukkit.getScheduler().runTask(TheBetterFoliaPlugin.getInstance(), () -> {
                    sendEntityResponse(player, player.getWorld(), entityId);
                });
            }
        } catch (Exception e) {
            TheBetterFoliaPlugin.getInstance().getLogger().warning("[ServuxTweaks] Handling entity request failed: " + e.getMessage());
            if (TheBetterFoliaPlugin.getInstance().isDebugEnabled()) {
                e.printStackTrace();
            }
        }
    }

    private void sendEntityResponse(Player player, World world, int entityId) {
        try {
            Object nbt = NmsNbtHelper.getEntityNbt(world, entityId);
            
            // Check if player inventory preview is allowed
            TheBetterFoliaPlugin plugin = TheBetterFoliaPlugin.getInstance();
            if (!plugin.isPreviewInventoryEnabled()) {
                String entityType = NmsNbtHelper.getString(nbt, "id");
                if ("minecraft:player".equals(entityType)) {
                    NmsNbtHelper.removeTag(nbt, "Inventory");
                    NmsNbtHelper.removeTag(nbt, "EnderItems");
                    if (plugin.isDebugEnabled()) {
                        plugin.debug("[ServuxTweaks] Removed player inventory data (preview_inventory=false)");
                    }
                }
            }
            
            byte[] data = NmsNbtHelper.serializeTweaksEntityPacket(entityId, nbt);
            PacketSender.sendPayload(player, CHANNEL, data);
            
            if (plugin.isDebugEnabled()) {
                plugin.debug("[ServuxTweaks] Sent entity NBT to " + player.getName());
            }
        } catch (Exception e) {
            TheBetterFoliaPlugin.getInstance().getLogger().warning("[ServuxTweaks] Sending entity response failed: " + e.getMessage());
            if (TheBetterFoliaPlugin.getInstance().isDebugEnabled()) {
                e.printStackTrace();
            }
        }
    }

    private void handleNbtResponseData(Player player) {
        UUID uuid = player.getUniqueId();
        if (readingSessionKeys.containsKey(uuid)) {
            readingSessionKeys.remove(uuid);
        }
        
        TheBetterFoliaPlugin plugin = TheBetterFoliaPlugin.getInstance();
        if (plugin.isDebugEnabled()) {
            plugin.debug("[ServuxTweaks] " + player.getName() + " ended reading session");
        }
    }

    private void sendMetadata(Player player) {
        NmsNbtHelper.sendMetadata(player, CHANNEL, "tweaks_data", CHANNEL, PROTOCOL_VERSION, ServuxBase.SERVUX_VERSION);
        
        TheBetterFoliaPlugin plugin = TheBetterFoliaPlugin.getInstance();
        if (plugin.isDebugEnabled()) {
            plugin.debug("[ServuxTweaks] Sent metadata to " + player.getName());
        }
    }

    public static class BlockPos {
        public final int x;
        public final int y;
        public final int z;

        public BlockPos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static class VarIntReader {
        private final byte[] data;
        private int pos = 0;

        public VarIntReader(byte[] data) {
            this.data = data;
        }

        public int readVarInt() {
            int value = 0;
            int shift = 0;
            byte b;
            do {
                if (pos >= data.length) break;
                b = data[pos++];
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) break;
                shift += 7;
            } while (pos < data.length);
            return value;
        }

        public long readLong() {
            long val = 0L;
            for (int i = 0; i < 8; i++) {
                val = (val << 8) | (data[pos++] & 0xFF);
            }
            return val;
        }

        public BlockPos readBlockPos() {
            long val = readLong();
            int x = (int)(val >> 38);
            int y = (int)(val << 52 >> 52);
            int z = (int)(val << 26 >> 38);
            return new BlockPos(x, y, z);
        }
    }
}
