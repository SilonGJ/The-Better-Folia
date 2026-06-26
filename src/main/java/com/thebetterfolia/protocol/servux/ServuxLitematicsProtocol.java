package com.thebetterfolia.protocol.servux;

import com.thebetterfolia.TheBetterFoliaPlugin;
import com.thebetterfolia.protocol.NmsNbtHelper;
import com.thebetterfolia.protocol.PacketSender;
import com.thebetterfolia.protocol.ProtocolManager;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class ServuxLitematicsProtocol implements ProtocolManager.ProtocolBase {

    public static final String CHANNEL = "servux:litematics";
    private static final int PROTOCOL_VERSION = 1;

    private static final int PACKET_S2C_METADATA = 1;
    private static final int PACKET_C2S_METADATA_REQUEST = 2;
    private static final int PACKET_C2S_BLOCK_ENTITY_REQUEST = 3;
    private static final int PACKET_C2S_ENTITY_REQUEST = 4;
    private static final int PACKET_S2C_BLOCK_NBT_RESPONSE_SIMPLE = 5;
    private static final int PACKET_S2C_ENTITY_NBT_RESPONSE_SIMPLE = 6;
    private static final int PACKET_C2S_BULK_ENTITY_NBT_REQUEST = 7;
    private static final int PACKET_S2C_NBT_RESPONSE_START = 10;
    private static final int PACKET_S2C_NBT_RESPONSE_DATA = 11;
    private static final int PACKET_C2S_NBT_RESPONSE_START = 12;
    private static final int PACKET_C2S_NBT_RESPONSE_DATA = 13;

    private final Map<UUID, Long> readingSessionKeys = new HashMap<>();
    private final Map<UUID, ReadingState> readingStates = new HashMap<>();

    @Override
    public String getChannel() {
        return CHANNEL;
    }

    @Override
    public void onPlayerQuit(Player bukkitPlayer) {
        readingSessionKeys.remove(bukkitPlayer.getUniqueId());
        readingStates.remove(bukkitPlayer.getUniqueId());
    }

    @Override
    public void onMessageReceived(Player bukkitPlayer, String channel, byte[] message) {
        if (!channel.equals(CHANNEL)) return;
        if (message.length < 1) return;

        TheBetterFoliaPlugin plugin = TheBetterFoliaPlugin.getInstance();
        try {
            FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(message));
            int packetType = buf.readVarInt();
            plugin.debug("[ServuxLitematics] Received packet type: " + packetType + " from " + bukkitPlayer.getName());

            switch (packetType) {
                case PACKET_C2S_METADATA_REQUEST -> sendMetadata(bukkitPlayer);
                case PACKET_C2S_BLOCK_ENTITY_REQUEST -> handleBlockEntityRequest(bukkitPlayer, buf);
                case PACKET_C2S_ENTITY_REQUEST -> handleEntityRequest(bukkitPlayer, buf);
                case PACKET_C2S_NBT_RESPONSE_DATA -> handleNbtResponseData(bukkitPlayer, message);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[ServuxLitematics] Error processing message: " + e.getMessage());
            if (plugin.isDebugEnabled()) {
                e.printStackTrace();
            }
        }
    }

    private void sendMetadata(Player bukkitPlayer) {
        try {
            Object metadata = NmsNbtHelper.createCompoundTag();
            NmsNbtHelper.putString(metadata, "name", "litematic_data");
            NmsNbtHelper.putString(metadata, "id", "servux:litematics");
            NmsNbtHelper.putInt(metadata, "version", PROTOCOL_VERSION);
            NmsNbtHelper.putString(metadata, "servux", "servux:litematics");

            byte[] bytes = NmsNbtHelper.serializePacket(PACKET_S2C_METADATA, metadata);
            PacketSender.sendPayload(bukkitPlayer, CHANNEL, bytes);

            TheBetterFoliaPlugin plugin = TheBetterFoliaPlugin.getInstance();
            if (plugin.isDebugEnabled()) {
                plugin.debug("[ServuxLitematics] Sent metadata to " + bukkitPlayer.getName());
            }
        } catch (Exception e) {
            TheBetterFoliaPlugin.getInstance().getLogger().warning("[ServuxLitematics] Sending metadata failed: " + e.getMessage());
        }
    }

    private void handleBlockEntityRequest(Player bukkitPlayer, FriendlyByteBuf buf) {
        TheBetterFoliaPlugin plugin = TheBetterFoliaPlugin.getInstance();
        try {
            int transactionId = buf.readVarInt();

            int x = 0, y = 0, z = 0;
            try {
                x = buf.readVarInt();
                y = buf.readVarInt();
                z = buf.readVarInt();
            } catch (Exception e) {
                plugin.debug("[ServuxLitematics] Reading BlockPos as VarInt failed, skipping");
            }

            Object nbt = NmsNbtHelper.getBlockEntityNbt(bukkitPlayer.getWorld(), NmsNbtHelper.createBlockPos(x, y, z));
            sendBlockEntityResponse(bukkitPlayer, x, y, z, nbt);

            if (plugin.isDebugEnabled()) {
                plugin.debug("[ServuxLitematics] Handled BlockEntityRequest for pos " + x + "," + y + "," + z);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[ServuxLitematics] Error handling BlockEntityRequest: " + e.getMessage());
        }
    }

    private void sendBlockEntityResponse(Player bukkitPlayer, int x, int y, int z, Object nbt) {
        try {
            io.netty.buffer.ByteBuf byteBuf = io.netty.buffer.Unpooled.buffer();
            FriendlyByteBuf fbb = new FriendlyByteBuf(byteBuf);
            fbb.writeVarInt(PACKET_S2C_BLOCK_NBT_RESPONSE_SIMPLE);
            fbb.writeVarInt(x);
            fbb.writeVarInt(y);
            fbb.writeVarInt(z);

            byte[] nbtBytes = NmsNbtHelper.serializePacket(0, nbt);
            int packetTypeSize = 1;
            while (packetTypeSize < nbtBytes.length && nbtBytes[packetTypeSize] == 0) {
                packetTypeSize++;
            }
            byte[] realNbtBytes = new byte[nbtBytes.length - packetTypeSize];
            System.arraycopy(nbtBytes, packetTypeSize, realNbtBytes, 0, realNbtBytes.length);
            fbb.writeBytes(realNbtBytes);

            int readableBytes = byteBuf.readableBytes();
            byte[] bytes = new byte[readableBytes];
            byteBuf.readBytes(bytes);
            byteBuf.release();

            PacketSender.sendPayload(bukkitPlayer, CHANNEL, bytes);
        } catch (Exception e) {
            TheBetterFoliaPlugin.getInstance().getLogger().warning("[ServuxLitematics] Sending BlockEntityResponse failed: " + e.getMessage());
        }
    }

    private void handleEntityRequest(Player bukkitPlayer, FriendlyByteBuf buf) {
        TheBetterFoliaPlugin plugin = TheBetterFoliaPlugin.getInstance();
        try {
            int transactionId = buf.readVarInt();
            int entityId = buf.readVarInt();

            Object nbt = NmsNbtHelper.getEntityNbt(bukkitPlayer.getWorld(), entityId);
            sendEntityResponse(bukkitPlayer, entityId, nbt);

            if (plugin.isDebugEnabled()) {
                plugin.debug("[ServuxLitematics] Handled EntityRequest for id " + entityId);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[ServuxLitematics] Error handling EntityRequest: " + e.getMessage());
        }
    }

    private void sendEntityResponse(Player bukkitPlayer, int entityId, Object nbt) {
        try {
            io.netty.buffer.ByteBuf byteBuf = io.netty.buffer.Unpooled.buffer();
            FriendlyByteBuf fbb = new FriendlyByteBuf(byteBuf);
            fbb.writeVarInt(PACKET_S2C_ENTITY_NBT_RESPONSE_SIMPLE);
            fbb.writeVarInt(entityId);

            byte[] nbtBytes = NmsNbtHelper.serializePacket(0, nbt);
            int packetTypeSize = 1;
            while (packetTypeSize < nbtBytes.length && nbtBytes[packetTypeSize] == 0) {
                packetTypeSize++;
            }
            byte[] realNbtBytes = new byte[nbtBytes.length - packetTypeSize];
            System.arraycopy(nbtBytes, packetTypeSize, realNbtBytes, 0, realNbtBytes.length);
            fbb.writeBytes(realNbtBytes);

            int readableBytes = byteBuf.readableBytes();
            byte[] bytes = new byte[readableBytes];
            byteBuf.readBytes(bytes);
            byteBuf.release();

            PacketSender.sendPayload(bukkitPlayer, CHANNEL, bytes);
        } catch (Exception e) {
            TheBetterFoliaPlugin.getInstance().getLogger().warning("[ServuxLitematics] Sending EntityResponse failed: " + e.getMessage());
        }
    }

    private void handleNbtResponseData(Player bukkitPlayer, byte[] message) {
        TheBetterFoliaPlugin plugin = TheBetterFoliaPlugin.getInstance();
        UUID uuid = bukkitPlayer.getUniqueId();

        try {
            long readingSessionKey;

            if (!readingSessionKeys.containsKey(uuid)) {
                readingSessionKey = ThreadLocalRandom.current().nextLong();
                readingSessionKeys.put(uuid, readingSessionKey);
                if (plugin.isDebugEnabled()) {
                    plugin.debug("[ServuxLitematics] Created new reading session for " + bukkitPlayer.getName() + ": " + readingSessionKey);
                }
            } else {
                readingSessionKey = readingSessionKeys.get(uuid);
                if (plugin.isDebugEnabled()) {
                    plugin.debug("[ServuxLitematics] Using existing reading session for " + bukkitPlayer.getName() + ": " + readingSessionKey);
                }
            }

            io.netty.buffer.ByteBuf dataBuf = io.netty.buffer.Unpooled.wrappedBuffer(message, 1, message.length - 1);
            FriendlyByteBuf data = new FriendlyByteBuf(dataBuf);

            if (plugin.isDebugEnabled()) {
                plugin.debug("[ServuxLitematics] Received NBT response data slice for " + bukkitPlayer.getName() + ", size: " + (message.length - 1) + " bytes");
            }

            FriendlyByteBuf fullPacket = PacketSplitter.receive(readingSessionKey, data);

            if (fullPacket != null) {
                plugin.debug("[ServuxLitematics] Received full NBT response packet from " + bukkitPlayer.getName());
                readingSessionKeys.remove(uuid);

                try {
                    int type = fullPacket.readVarInt();
                    byte[] nbtBytes = fullPacket.readBytes(fullPacket.readableBytes());
                    Object nbt = NmsNbtHelper.deserializeNbt(nbtBytes);

                    if (nbt != null) {
                        handlePasteRequest(bukkitPlayer, nbt);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("[ServuxLitematics] Error reading full packet: " + e.getMessage());
                    if (plugin.isDebugEnabled()) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[ServuxLitematics] Error handling NBT response data: " + e.getMessage());
            if (plugin.isDebugEnabled()) {
                e.printStackTrace();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void handlePasteRequest(Player bukkitPlayer, Object nbt) {
        TheBetterFoliaPlugin plugin = TheBetterFoliaPlugin.getInstance();
        if (plugin.isDebugEnabled()) {
            plugin.debug("[ServuxLitematics] Paste request received from " + bukkitPlayer.getName());
        }

        try {
            // Debug: print NBT structure
            plugin.debug("[ServuxLitematics] NBT class: " + nbt.getClass().getName());
            plugin.debug("[ServuxLitematics] --- Full NBT structure ---");
            printCompoundTag(nbt, "", 5);
            plugin.debug("[ServuxLitematics] --- End NBT structure ---");
            
            // Get origin
            int[] origin = NmsNbtHelper.getIntArray(nbt, "Origin");
            plugin.debug("[ServuxLitematics] Origin array class: " + (origin != null ? origin.getClass().getName() : "null"));
            int originX = origin[0];
            int originY = origin[1];
            int originZ = origin[2];
            
            // Get rotation and mirror
            int rotation = NmsNbtHelper.getInt(nbt, "Rotation");
            int mirror = NmsNbtHelper.getInt(nbt, "Mirror");
            
            plugin.debug("[ServuxLitematics] Origin: " + originX + ", " + originY + ", " + originZ + 
                          ", Rotation: " + rotation + ", Mirror: " + mirror);
            
            // Get schematics
            Object schematics = NmsNbtHelper.getCompound(nbt, "Schematics");
            plugin.debug("[ServuxLitematics] Schematics class: " + (schematics != null ? schematics.getClass().getName() : "null"));
            if (schematics == null) {
                plugin.debug("[ServuxLitematics] No Schematics found!");
                bukkitPlayer.sendMessage("§c[TheBetterFolia] No schematic data found!");
                return;
            }
            
            // Paste each region
            Object regions = NmsNbtHelper.getCompound(schematics, "Regions");
            plugin.debug("[ServuxLitematics] Regions class: " + (regions != null ? regions.getClass().getName() : "null"));
            if (regions == null) {
                plugin.debug("[ServuxLitematics] No Regions found!");
                bukkitPlayer.sendMessage("§c[TheBetterFolia] No region data found!");
                return;
            }
            
            // Get all region keys - we need to use reflection to get keys from CompoundTag
            java.util.Set<String> regionNames = new java.util.HashSet<>();
            try {
                Method getAllKeysMethod = regions.getClass().getMethod("getAllKeys");
                regionNames.addAll((java.util.Collection<String>) getAllKeysMethod.invoke(regions));
            } catch (Exception e) {
                // Fallback: try to get keySet
                try {
                    Method keySetMethod = regions.getClass().getMethod("keySet");
                    regionNames.addAll((java.util.Collection<String>) keySetMethod.invoke(regions));
                } catch (Exception e2) {
                    plugin.debug("[ServuxLitematics] Failed to get region names: " + e2.getMessage());
                }
            }
            
            if (regionNames.isEmpty()) {
                // Fallback: use toString to find keys
                String nbtStr = nbt.toString();
                plugin.debug("[ServuxLitematics] NBT: " + nbtStr);
                bukkitPlayer.sendMessage("§a[TheBetterFolia] Schematic received! (pasting in debug mode)");
                return;
            }
            
            org.bukkit.World world = bukkitPlayer.getWorld();
            int totalBlocks = 0;
            java.util.List<int[]> allPlacedPositions = new java.util.ArrayList<>();
            
            for (String regionName : regionNames) {
                Object region = NmsNbtHelper.getCompound(regions, regionName);
                if (region != null) {
                    java.util.List<int[]> placed = pasteRegion(world, region, originX, originY, originZ, rotation, mirror);
                    totalBlocks += placed.size();
                    allPlacedPositions.addAll(placed);
                    plugin.debug("[ServuxLitematics] Pasted region '" + regionName + "': " + placed.size() + " blocks");
                }
            }
            
            // Apply tile entities (chest contents, furnace data, etc.)
            for (String regionName : regionNames) {
                Object region = NmsNbtHelper.getCompound(regions, regionName);
                if (region != null) {
                    pasteTileEntities(world, region, originX, originY, originZ, rotation, mirror);
                }
            }
            
            // Spawn entities (minecarts, item frames, etc.)
            for (String regionName : regionNames) {
                Object region = NmsNbtHelper.getCompound(regions, regionName);
                if (region != null) {
                    pasteEntities(world, region, originX, originY, originZ, rotation, mirror);
                }
            }
            
            // Batch update neighbors for all placed blocks (to trigger correct rail connections, etc.)
            // Without physics to avoid breaking contraptions
            for (int[] pos : allPlacedPositions) {
                world.getBlockAt(pos[0], pos[1], pos[2]).getState().update(true, false);
            }
            
            bukkitPlayer.sendMessage("§a[TheBetterFolia] Pasted " + totalBlocks + " blocks!");
            
        } catch (Exception e) {
            plugin.getLogger().warning("[ServuxLitematics] Error in paste request: " + e.getMessage());
            if (plugin.isDebugEnabled()) {
                e.printStackTrace();
            }
            bukkitPlayer.sendMessage("§c[TheBetterFolia] Paste failed: " + e.getMessage());
        }
    }
    
    @SuppressWarnings("unchecked")
    private java.util.List<int[]> pasteRegion(org.bukkit.World world, Object region, int originX, int originY, int originZ, int rotation, int mirror) {
        TheBetterFoliaPlugin plugin = TheBetterFoliaPlugin.getInstance();
        java.util.List<int[]> placedPositions = new java.util.ArrayList<>();
        
        try {
            // Get block palette
            Object palette = NmsNbtHelper.getList(region, "BlockStatePalette", 10);
            int paletteSize = NmsNbtHelper.getListSize(palette);
            
            // Parse block palette
            java.util.List<BlockStateInfo> blockStates = new java.util.ArrayList<>();
            for (int i = 0; i < paletteSize; i++) {
                Object state = NmsNbtHelper.getListCompound(palette, i);
                String name = NmsNbtHelper.getString(state, "Name");
                
                java.util.Map<String, String> properties = new java.util.HashMap<>();
                if (NmsNbtHelper.hasTag(state, "Properties")) {
                    Object props = NmsNbtHelper.getCompound(state, "Properties");
                    try {
                        Method getAllKeysMethod = props.getClass().getMethod("getAllKeys");
                        java.util.Set<String> propKeys = (java.util.Set<String>) getAllKeysMethod.invoke(props);
                        for (String key : propKeys) {
                            properties.put(key, NmsNbtHelper.getString(props, key));
                        }
                    } catch (Exception e) {
                        try {
                            Method keySetMethod = props.getClass().getMethod("keySet");
                            java.util.Set<String> propKeys = (java.util.Set<String>) keySetMethod.invoke(props);
                            for (String key : propKeys) {
                                properties.put(key, NmsNbtHelper.getString(props, key));
                            }
                        } catch (Exception e2) {}
                    }
                }
                blockStates.add(new BlockStateInfo(name, properties));
            }
            
            // Get block data
            long[] rawBlockData = NmsNbtHelper.getLongArray(region, "BlockStates");
            
            // Get size
            Object size = NmsNbtHelper.getCompound(region, "Size");
            int sizeX = NmsNbtHelper.getInt(size, "x");
            int sizeY = NmsNbtHelper.getInt(size, "y");
            int sizeZ = NmsNbtHelper.getInt(size, "z");
            
            // Get region position
            Object position = NmsNbtHelper.getCompound(region, "Position");
            int posX = NmsNbtHelper.getInt(position, "x");
            int posY = NmsNbtHelper.getInt(position, "y");
            int posZ = NmsNbtHelper.getInt(position, "z");
            
            // Calculate min coordinates (the actual minimum corner of the region)
            // For negative sizes, the Position is the MAX corner, so min = pos + size + 1
            int minX = sizeX > 0 ? posX : posX + sizeX + 1;
            int minY = sizeY > 0 ? posY : posY + sizeY + 1;
            int minZ = sizeZ > 0 ? posZ : posZ + sizeZ + 1;
            int absSizeX = Math.abs(sizeX);
            int absSizeY = Math.abs(sizeY);
            int absSizeZ = Math.abs(sizeZ);
            
            // Calculate bits per block (same as LitematicaBlockStateContainer)
            int bitsPerBlock = Math.max(2, 32 - Integer.numberOfLeadingZeros(paletteSize - 1));
            long maxEntryValue = (1L << bitsPerBlock) - 1L;
            
            // Use LitematicaBitArray algorithm for correct decoding
            // Decode and place blocks (WITHOUT physics to prevent redstone/rail updates)
            for (int y = 0; y < absSizeY; y++) {
                for (int z = 0; z < absSizeZ; z++) {
                    for (int x = 0; x < absSizeX; x++) {
                        // Index in Litematica format: y * (sizeX * sizeZ) + z * sizeX + x
                        long index = (long) y * absSizeX * absSizeZ + (long) z * absSizeX + (long) x;
                        
                        // Use same bit extraction as LitematicaBitArray.getAt()
                        long startOffset = index * bitsPerBlock;
                        int startArrIndex = (int) (startOffset >> 6);
                        int endArrIndex = (int) (((index + 1L) * bitsPerBlock - 1L) >> 6);
                        int startBitOffset = (int) (startOffset & 0x3F);
                        
                        int stateIndex;
                        if (startArrIndex >= rawBlockData.length) {
                            continue;
                        }
                        
                        if (startArrIndex == endArrIndex) {
                            stateIndex = (int) ((rawBlockData[startArrIndex] >>> startBitOffset) & maxEntryValue);
                        } else {
                            int endOffset = 64 - startBitOffset;
                            if (endArrIndex < rawBlockData.length) {
                                stateIndex = (int) ((rawBlockData[startArrIndex] >>> startBitOffset | rawBlockData[endArrIndex] << endOffset) & maxEntryValue);
                            } else {
                                stateIndex = (int) ((rawBlockData[startArrIndex] >>> startBitOffset) & maxEntryValue);
                            }
                        }
                        
                        if (stateIndex < blockStates.size()) {
                            BlockStateInfo stateInfo = blockStates.get(stateIndex);
                            
                            if (stateInfo.name.equals("minecraft:air")) {
                                continue;
                            }
                            
                            // Calculate position in schematic coordinate space
                            int schemX = minX + x;
                            int schemY = minY + y;
                            int schemZ = minZ + z;
                            
                            // Transform the schematic position, NOT the region-relative position
                            // Rotation and mirror are applied around schematic origin (0,0,0)
                            int[] transformed = applyRotationAndMirror(schemX, schemY, schemZ, rotation, mirror);
                            
                            // Create copy of properties and transform
                            java.util.Map<String, String> transformedProps = new java.util.HashMap<>(stateInfo.properties);
                            transformProperties(transformedProps, rotation, mirror);
                            
                            // Calculate final world position
                            int worldX = originX + transformed[0];
                            int worldY = originY + transformed[1];
                            int worldZ = originZ + transformed[2];
                            
                            // Place block with physics=false to prevent cascading updates
                            try {
                                String blockStateStr = stateInfo.name;
                                if (!transformedProps.isEmpty()) {
                                    StringBuilder sb = new StringBuilder(stateInfo.name);
                                    sb.append('[');
                                    boolean first = true;
                                    for (java.util.Map.Entry<String, String> e : transformedProps.entrySet()) {
                                        if (!first) sb.append(',');
                                        sb.append(e.getKey()).append('=').append(e.getValue());
                                        first = false;
                                    }
                                    sb.append(']');
                                    blockStateStr = sb.toString();
                                }
                                
                                org.bukkit.block.data.BlockData blockData = org.bukkit.Bukkit.createBlockData(blockStateStr);
                                org.bukkit.block.Block block = world.getBlockAt(worldX, worldY, worldZ);
                                block.setBlockData(blockData, false);
                                placedPositions.add(new int[]{worldX, worldY, worldZ});
                            } catch (Exception e) {
                                // Fallback: try material match
                                try {
                                    org.bukkit.Material material = org.bukkit.Material.matchMaterial(stateInfo.name);
                                    if (material != null && material.isBlock()) {
                                        org.bukkit.block.data.BlockData blockData = material.createBlockData();
                                        for (java.util.Map.Entry<String, String> entry : transformedProps.entrySet()) {
                                            try {
                                                String key = entry.getKey();
                                                String value = entry.getValue();
                                                if (blockData instanceof org.bukkit.block.data.Directional && key.equals("facing")) {
                                                    ((org.bukkit.block.data.Directional) blockData).setFacing(org.bukkit.block.BlockFace.valueOf(value.toUpperCase()));
                                                } else if (blockData instanceof org.bukkit.block.data.Rotatable && key.equals("rotation")) {
                                                    ((org.bukkit.block.data.Rotatable) blockData).setRotation(org.bukkit.block.BlockFace.valueOf(value.toUpperCase()));
                                                } else if (blockData instanceof org.bukkit.block.data.Bisected && key.equals("half")) {
                                                    ((org.bukkit.block.data.Bisected) blockData).setHalf(org.bukkit.block.data.Bisected.Half.valueOf(value.toUpperCase()));
                                                } else if (blockData instanceof org.bukkit.block.data.Waterlogged && key.equals("waterlogged")) {
                                                    ((org.bukkit.block.data.Waterlogged) blockData).setWaterlogged(Boolean.parseBoolean(value));
                                                }
                                            } catch (Exception ignored) {}
                                        }
                                        world.getBlockAt(worldX, worldY, worldZ).setBlockData(blockData, false);
                                        placedPositions.add(new int[]{worldX, worldY, worldZ});
                                    }
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            plugin.debug("[ServuxLitematics] Error pasting region: " + e.getMessage());
            if (plugin.isDebugEnabled()) {
                e.printStackTrace();
            }
        }
        
        return placedPositions;
    }

    private void pasteTileEntities(org.bukkit.World world, Object region, int originX, int originY, int originZ, int rotation, int mirror) {
        if (!NmsNbtHelper.hasTag(region, "TileEntities")) return;
        TheBetterFoliaPlugin plugin = TheBetterFoliaPlugin.getInstance();
        
        try {
            Object teList = NmsNbtHelper.getList(region, "TileEntities", 10);
            int size = NmsNbtHelper.getListSize(teList);
            
            for (int i = 0; i < size; i++) {
                Object teNbt = NmsNbtHelper.getListCompound(teList, i);
                
                int teX = NmsNbtHelper.getInt(teNbt, "x");
                int teY = NmsNbtHelper.getInt(teNbt, "y");
                int teZ = NmsNbtHelper.getInt(teNbt, "z");
                
                // Transform position
                int[] transformed = applyRotationAndMirror(teX, teY, teZ, rotation, mirror);
                int worldX = originX + transformed[0];
                int worldY = originY + transformed[1];
                int worldZ = originZ + transformed[2];
                
                // Apply tile entity NBT
                NmsNbtHelper.applyTileEntityNbt(world, worldX, worldY, worldZ, teNbt);
            }
        } catch (Exception e) {
            plugin.debug("[ServuxLitematics] Error pasting tile entities: " + e.getMessage());
        }
    }

    private void pasteEntities(org.bukkit.World world, Object region, int originX, int originY, int originZ, int rotation, int mirror) {
        if (!NmsNbtHelper.hasTag(region, "Entities")) return;
        TheBetterFoliaPlugin plugin = TheBetterFoliaPlugin.getInstance();
        
        try {
            // Read region position offset (same logic as pasteRegion)
            Object regPosition = NmsNbtHelper.getCompound(region, "Position");
            int regPosX = 0, regPosY = 0, regPosZ = 0;
            if (regPosition != null) {
                regPosX = NmsNbtHelper.getInt(regPosition, "x");
                regPosY = NmsNbtHelper.getInt(regPosition, "y");
                regPosZ = NmsNbtHelper.getInt(regPosition, "z");
            }
            Object regSize = NmsNbtHelper.getCompound(region, "Size");
            int sizeX = 1, sizeY = 1, sizeZ = 1;
            if (regSize != null) {
                sizeX = NmsNbtHelper.getInt(regSize, "x");
                sizeY = NmsNbtHelper.getInt(regSize, "y");
                sizeZ = NmsNbtHelper.getInt(regSize, "z");
            }
            // For negative sizes, Position is the max corner, so min = pos + size + 1
            int regMinX = sizeX > 0 ? regPosX : regPosX + sizeX + 1;
            int regMinY = sizeY > 0 ? regPosY : regPosY + sizeY + 1;
            int regMinZ = sizeZ > 0 ? regPosZ : regPosZ + sizeZ + 1;

            Object entityList = NmsNbtHelper.getList(region, "Entities", 10);
            int size = NmsNbtHelper.getListSize(entityList);
            plugin.debug("[ServuxLitematics] pasteEntities: found " + size + " entities, origin=(" + originX + "," + originY + "," + originZ + "), regOffset=(" + regMinX + "," + regMinY + "," + regMinZ + "), rotation=" + rotation + ", mirror=" + mirror);

            for (int i = 0; i < size; i++) {
                Object entityNbt = NmsNbtHelper.getListCompound(entityList, i);
                
                // Read entity ID for debugging
                String entityId = "?";
                try { entityId = NmsNbtHelper.getString(entityNbt, "id"); } catch (Exception ignored) {}

                // Read position from entity NBT (doubles, relative to region min corner)
                Object posList = null;
                try {
                    posList = NmsNbtHelper.getList(entityNbt, "Pos", 6);
                } catch (Exception e) {
                    plugin.debug("[ServuxLitematics] pasteEntities: entity[" + i + "] id=" + entityId + " FAILED to get Pos list: " + e.getMessage());
                    continue;
                }
                if (posList == null) {
                    plugin.debug("[ServuxLitematics] pasteEntities: entity[" + i + "] id=" + entityId + " Pos list is null, skipping");
                    continue;
                }
                
                double px = NmsNbtHelper.getListDouble(posList, 0);
                double py = NmsNbtHelper.getListDouble(posList, 1);
                double pz = NmsNbtHelper.getListDouble(posList, 2);
                
                // Entity coords are relative to region min corner, add region offset
                double globalSchemX = px + regMinX;
                double globalSchemY = py + regMinY;
                double globalSchemZ = pz + regMinZ;

                // Transform position (round to int for rotation, keep fractional offset)
                int schemX = (int) Math.round(globalSchemX);
                int schemY = (int) Math.round(globalSchemY);
                int schemZ = (int) Math.round(globalSchemZ);
                double fracX = globalSchemX - schemX;
                double fracY = globalSchemY - schemY;
                double fracZ = globalSchemZ - schemZ;
                
                int[] transformed = applyRotationAndMirror(schemX, schemY, schemZ, rotation, mirror);
                double worldX = originX + transformed[0] + fracX;
                double worldY = originY + transformed[1] + fracY;
                double worldZ = originZ + transformed[2] + fracZ;

                plugin.debug("[ServuxLitematics] pasteEntities[" + i + "]: id=" + entityId +
                    " rawPos=(" + px + "," + py + "," + pz + ")" +
                    " global=(" + globalSchemX + "," + globalSchemY + "," + globalSchemZ + ")" +
                    " schem=(" + schemX + "," + schemY + "," + schemZ + ")" +
                    " xform=(" + transformed[0] + "," + transformed[1] + "," + transformed[2] + ")" +
                    " world=(" + worldX + "," + worldY + "," + worldZ + ")");

                // Write world coordinates into entity NBT so loadEntityRecursive
                // creates the entity at the correct position directly
                NmsNbtHelper.setPosInTag(entityNbt, worldX, worldY, worldZ);

                // Spawn entity from NBT at transformed position
                NmsNbtHelper.spawnEntityFromNbt(world, entityNbt, worldX, worldY, worldZ);
            }
        } catch (Exception e) {
            plugin.debug("[ServuxLitematics] Error pasting entities: " + e.getMessage());
            if (plugin.isDebugEnabled()) {
                e.printStackTrace();
            }
        }
    }
    
    private int[] applyRotationAndMirror(int schemX, int schemY, int schemZ, int rotation, int mirror) {
        int x = schemX;
        int z = schemZ;
        
        // Apply mirror first (matching LeavesMC's PositionUtils)
        // Mirror.LEFT_RIGHT (1) = NORTH_SOUTH mirror -> z = -z
        // Mirror.FRONT_BACK (2) = EAST_WEST mirror -> x = -x
        if (mirror == 1) {
            z = -z;
        } else if (mirror == 2) {
            x = -x;
        }
        
        // Apply rotation (matching LeavesMC's PositionUtils)
        // 0 = NONE, 1 = CLOCKWISE_90, 2 = CLOCKWISE_180, 3 = COUNTERCLOCKWISE_90
        switch (rotation) {
            case 1 -> { // CLOCKWISE_90: (-z, y, x)
                int temp = x;
                x = -z;
                z = temp;
            }
            case 2 -> { // CLOCKWISE_180: (-x, y, -z)
                x = -x;
                z = -z;
            }
            case 3 -> { // COUNTERCLOCKWISE_90: (z, y, -x)
                int temp = x;
                x = z;
                z = -temp;
            }
        }
        
        return new int[]{x, schemY, z};
    }
    
    private void transformProperties(java.util.Map<String, String> properties, int rotation, int mirror) {
        String facing = properties.get("facing");
        if (facing != null) {
            // Apply mirror (matching LeavesMC's state.mirror())
            if (mirror == 1) { // LEFT_RIGHT (NORTH_SOUTH): north<->south
                switch (facing) {
                    case "north": facing = "south"; break;
                    case "south": facing = "north"; break;
                }
            } else if (mirror == 2) { // FRONT_BACK (EAST_WEST): east<->west
                switch (facing) {
                    case "east": facing = "west"; break;
                    case "west": facing = "east"; break;
                }
            }
            
            // Apply rotation
            for (int i = 0; i < rotation; i++) {
                switch (facing) {
                    case "north": facing = "east"; break;
                    case "east": facing = "south"; break;
                    case "south": facing = "west"; break;
                    case "west": facing = "north"; break;
                }
            }
            properties.put("facing", facing);
        }
        
        // Also transform axis if present (for logs, pillars)
        String axis = properties.get("axis");
        if (axis != null) {
            for (int i = 0; i < rotation; i++) {
                switch (axis) {
                    case "x": axis = "z"; break;
                    case "z": axis = "x"; break;
                    // y stays y
                }
            }
            properties.put("axis", axis);
        }
    }
    
    private void printCompoundTag(Object tag, String indent, int maxDepth) {
        if (maxDepth <= 0) return;
        TheBetterFoliaPlugin plugin = TheBetterFoliaPlugin.getInstance();
        
        try {
            // Use reflection to get all keys
            Method getAllKeysMethod = null;
            try {
                getAllKeysMethod = tag.getClass().getMethod("getAllKeys");
            } catch (Exception e) {
                try {
                    getAllKeysMethod = tag.getClass().getMethod("keySet");
                } catch (Exception ignored) {}
            }
            
            if (getAllKeysMethod == null) {
                plugin.debug("[ServuxLitematics] " + indent + "Cannot get keys from compound tag");
                return;
            }
            
            @SuppressWarnings("unchecked")
            java.util.Set<String> keys = (java.util.Set<String>) getAllKeysMethod.invoke(tag);

            for (String key : keys) {
                try {
                    // Use reflection to get the tag directly, without NmsNbtHelper
                    Method getMethod = tag.getClass().getMethod("get", String.class);
                    Object value = getMethod.invoke(tag, key);
                    
                    // Handle Optional (since in 1.21.11, get() returns Optional)
                    if (value instanceof Optional) {
                        Optional<?> opt = (Optional<?>) value;
                        if (opt.isPresent()) {
                            value = opt.get();
                        } else {
                            plugin.debug("[ServuxLitematics] " + indent + key + ": Optional.empty");
                            continue;
                        }
                    }
                    
                    if (value == null) {
                        plugin.debug("[ServuxLitematics] " + indent + key + ": null");
                        continue;
                    }
                    
                    String className = value.getClass().getSimpleName();
                    
                    if (className.contains("CompoundTag")) {
                        plugin.debug("[ServuxLitematics] " + indent + key + ": CompoundTag");
                        printCompoundTag(value, indent + "  ", maxDepth - 1);
                    } else if (className.contains("ListTag")) {
                        Method sizeMethod = value.getClass().getMethod("size");
                        int size = (int) sizeMethod.invoke(value);
                        plugin.debug("[ServuxLitematics] " + indent + key + ": ListTag[" + size + "]");
                    } else if (className.contains("IntArrayTag")) {
                        Method getDataMethod = value.getClass().getMethod("getAsIntArray");
                        int[] data = (int[]) getDataMethod.invoke(value);
                        plugin.debug("[ServuxLitematics] " + indent + key + ": IntArray[" + data.length + "]");
                    } else if (className.contains("LongArrayTag")) {
                        Method getDataMethod = value.getClass().getMethod("getAsLongArray");
                        long[] data = (long[]) getDataMethod.invoke(value);
                        plugin.debug("[ServuxLitematics] " + indent + key + ": LongArray[" + data.length + "]");
                    } else {
                        plugin.debug("[ServuxLitematics] " + indent + key + ": " + className + " = " + value.toString());
                    }
                } catch (Exception e) {
                    plugin.debug("[ServuxLitematics] " + indent + key + ": Error getting value - " + e.getMessage());
                }
            }
        } catch (Exception e) {
            plugin.debug("[ServuxLitematics] Error printing CompoundTag: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static class BlockStateInfo {
        String name;
        java.util.Map<String, String> properties;
        
        BlockStateInfo(String name, java.util.Map<String, String> properties) {
            this.name = name;
            this.properties = properties;
        }
    }

    private static class ReadingState {
        int expectedSize = -1;
        FriendlyByteBuf buffer;
    }
}
