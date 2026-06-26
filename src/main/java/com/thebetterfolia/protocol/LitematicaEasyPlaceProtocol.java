package com.thebetterfolia.protocol;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.MovingObjectPositionBlock;
import com.thebetterfolia.TheBetterFoliaPlugin;
import com.thebetterfolia.protocol.ProtocolManager.ProtocolBase;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class LitematicaEasyPlaceProtocol implements ProtocolBase, Listener {

    public static final String CHANNEL = "litematica_easyplace";

    private static final Set<String> WHITELISTED_PROPERTIES = Set.of(
            "inverted", "open", "attachment", "axis", "half", "face",
            "type", "mode", "hinge", "facing", "orientation", "shape",
            "pose", "bites", "delay", "note", "rotation"
    );
    private static final Set<String> BLACKLISTED_PROPERTIES = Set.of("waterlogged", "powered");
    private static final List<String> DIRECTION_3D_ORDER = List.of(
            "down", "up", "north", "south", "west", "east"
    );

    private static final ConcurrentHashMap<String, List<PropertyDefinition>> PROPERTY_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> PROPERTY_FAILURES = ConcurrentHashMap.newKeySet();

    private final ConcurrentHashMap<UUID, ConcurrentLinkedDeque<PlacementEntry>> pendingPlacements = new ConcurrentHashMap<>();

    private int applyDelayTicks = 1;

    private ProtocolManager protocolManager;
    private boolean protocolLibReady;

    @Override
    public String getChannel() {
        return CHANNEL;
    }

    @Override
    public void init() {
        TheBetterFoliaPlugin plugin = TheBetterFoliaPlugin.getInstance();

        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
            plugin.getLogger().warning("[LitematicaEasyPlace] ProtocolLib not found, EasyPlace will be unavailable");
            return;
        }

        protocolManager = ProtocolLibrary.getProtocolManager();
        protocolLibReady = true;

        applyDelayTicks = plugin.getConfig().getInt("schematica.easy_place_apply_delay", 1);

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        registerPacketListener();

        plugin.getLogger().info("[LitematicaEasyPlace] Initialized successfully (ProtocolLib)");
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        if (protocolManager != null) {
            protocolManager.removePacketListeners(TheBetterFoliaPlugin.getInstance());
        }
        pendingPlacements.clear();
    }

    @Override
    public void onPlayerQuit(Player player) {
        pendingPlacements.remove(player.getUniqueId());
    }

    private void registerPacketListener() {
        if (protocolManager == null) return;

        protocolManager.removePacketListeners(TheBetterFoliaPlugin.getInstance());
        protocolManager.addPacketListener(new PacketAdapter(
                TheBetterFoliaPlugin.getInstance(),
                ListenerPriority.LOWEST,
                PacketType.Play.Client.USE_ITEM_ON
        ) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                handleUseItemOn(event);
            }
        });
    }

    private void handleUseItemOn(PacketEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        try {
            PacketContainer packet = event.getPacket();
            MovingObjectPositionBlock hit = packet.getMovingBlockPositions().read(0);
            BlockPosition clickedBlock = hit.getBlockPosition();
            Vector hitVector = hit.getPosVector();

            BlockPosition expectedPlaced = offset(clickedBlock, hit.getDirection());

            double relativeX = hitVector.getX() - expectedPlaced.getX();

            if (relativeX < 2.0D) {
                return;
            }

            int protocolValue = ((int) Math.floor(relativeX)) - 2;
            if (protocolValue < 0 || protocolValue > 1048575) {
                return;
            }

            queuePlacement(player.getUniqueId(),
                    new PlacementEntry(player.getWorld().getUID(), clickedBlock, expectedPlaced,
                            hit.getDirection(), protocolValue));

            hitVector.setX(clickedBlock.getX() + 0.5D);
            hit.setPosVector(hitVector);
            packet.getMovingBlockPositions().write(0, hit);

            TheBetterFoliaPlugin plugin = TheBetterFoliaPlugin.getInstance();
            if (plugin.isDebugEnabled()) {
                plugin.debug(
                        "[LitematicaEasyPlace] Decoded v3 protocolValue=" + protocolValue
                                + " player=" + player.getName()
                                + " clicked=" + clickedBlock
                                + " direction=" + hit.getDirection());
            }
        } catch (Exception e) {
            TheBetterFoliaPlugin plugin = TheBetterFoliaPlugin.getInstance();
            if (plugin.isDebugEnabled()) {
                plugin.getLogger().warning(
                        "[LitematicaEasyPlace] Handling USE_ITEM_ON failed: " + e.getMessage());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!protocolLibReady) return;

        Player player = event.getPlayer();
        Block placed = event.getBlock();
        Block against = event.getBlockAgainst();

        TheBetterFoliaPlugin plugin = TheBetterFoliaPlugin.getInstance();

        PlacementEntry entry = findMatchingPlacement(player.getUniqueId(), placed, against);
        if (entry == null || entry.isExpired()) {
            if (plugin.isDebugEnabled()) {
                plugin.debug("[LitematicaEasyPlace] BlockPlace no matching v3 entry: placed=" + placed.getX() + "," + placed.getY() + "," + placed.getZ()
                        + " type=" + placed.getType() + " player=" + player.getName());
            }
            return;
        }

        if (!isSamePlacement(entry, placed, against)) {
            if (plugin.isDebugEnabled()) {
                plugin.debug("[LitematicaEasyPlace] BlockPlace isSamePlacement recheck failed: placed=" + placed.getX() + "," + placed.getY() + "," + placed.getZ());
            }
            return;
        }

        if (plugin.isDebugEnabled()) {
                plugin.debug("[LitematicaEasyPlace] BlockPlace matched: protocolValue=" + entry.protocolValue
                        + " placed=" + placed.getX() + "," + placed.getY() + "," + placed.getZ()
                        + " type=" + placed.getType() + " direction=" + entry.clickedFace);
        }

        Material placedType = placed.getType();
        if (applyDelayTicks > 0) {
            final int protocolValue = entry.protocolValue;
            if (plugin.isFolia()) {
                player.getScheduler().runDelayed(plugin, task -> {
                    applyPlacementState(player, placed, placedType, protocolValue);
                }, null, applyDelayTicks);
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    applyPlacementState(player, placed, placedType, protocolValue);
                }, applyDelayTicks);
            }
        } else {
            applyPlacementState(player, placed, placedType, entry.protocolValue);
        }
    }

    private void applyPlacementState(Player player, Block placed, Material originalType, int protocolValue) {
        TheBetterFoliaPlugin plugin = TheBetterFoliaPlugin.getInstance();
        if (!player.isOnline()) {
            if (plugin.isDebugEnabled()) plugin.debug("[LitematicaEasyPlace] applyPlacementState skipped: player offline");
            return;
        }
        if (!placed.getWorld().getUID().equals(player.getWorld().getUID())) {
            if (plugin.isDebugEnabled()) plugin.debug("[LitematicaEasyPlace] applyPlacementState skipped: world changed");
            return;
        }
        if (placed.getType() != originalType) {
            if (plugin.isDebugEnabled()) plugin.debug("[LitematicaEasyPlace] applyPlacementState skipped: block type changed original=" + originalType + " current=" + placed.getType());
            return;
        }
        applyBlockState(placed, protocolValue);
    }

    private void applyBlockState(Block block, int protocolValue) {
        BlockData currentData = block.getBlockData();
        ParsedBlockData parsed = ParsedBlockData.parse(currentData.getAsString(false));
        TheBetterFoliaPlugin plugin = TheBetterFoliaPlugin.getInstance();
        if (parsed.properties.isEmpty()) {
            if (plugin.isDebugEnabled()) plugin.debug("[LitematicaEasyPlace] applyBlockState skipped: no properties block=" + parsed.id);
            return;
        }

        List<PropertyInfo> properties = resolveProperties(block, currentData, parsed);
        if (properties.isEmpty()) {
            if (plugin.isDebugEnabled()) plugin.debug("[LitematicaEasyPlace] applyBlockState skipped: could not resolve properties block=" + parsed.id);
            return;
        }

        LinkedHashMap<String, String> updated = new LinkedHashMap<>(parsed.properties);
        PropertyInfo directionProperty = firstDirectionProperty(properties);

        int bits = protocolValue;

        if (directionProperty != null) {
            String desired = decodeDirection(bits, directionProperty.currentValue);
            if (desired != null && directionProperty.possibleValues.contains(desired)) {
                updated.put(directionProperty.name, desired);
            }
            bits >>>= 3;
        }

        bits >>>= 1;

        List<PropertyInfo> sorted = new ArrayList<>(properties);
        sorted.sort(Comparator.comparing(p -> p.name));

        for (PropertyInfo property : sorted) {
            if (directionProperty != null && directionProperty.name.equals(property.name)) continue;
            if (!WHITELISTED_PROPERTIES.contains(property.name)
                    || BLACKLISTED_PROPERTIES.contains(property.name)) {
                continue;
            }

            List<String> values = property.possibleValues;
            if (values.isEmpty()) continue;

            int requiredBits = requiredBits(values.size());
            int bitMask = ~(0xFFFFFFFF << requiredBits);
            int valueIndex = bits & bitMask;
            if (valueIndex >= 0 && valueIndex < values.size()) {
                String value = values.get(valueIndex);
                if (!("type".equals(property.name) && "double".equals(value))) {
                    updated.put(property.name, value);
                }
                bits >>>= requiredBits;
            }
        }

        String outputString = parsed.toBlockDataString(updated);
        String inputString = currentData.getAsString(false);
        if (inputString.equals(outputString)) {
            if (plugin.isDebugEnabled()) plugin.debug("[LitematicaEasyPlace] applyBlockState no change: " + inputString);
            return;
        }

        try {
            BlockData newData = Bukkit.createBlockData(outputString);
            if (newData.getMaterial() != currentData.getMaterial()) {
                if (plugin.isDebugEnabled()) plugin.debug("[LitematicaEasyPlace] applyBlockState skipped: material changed input=" + inputString + " output=" + outputString);
                return;
            }
            if (plugin.isDebugEnabled()) plugin.debug("[LitematicaEasyPlace] applyBlockState applying: " + inputString + " -> " + outputString);
            block.setBlockData(newData, false);
        } catch (Exception e) {
            if (plugin.isDebugEnabled()) plugin.debug("[LitematicaEasyPlace] applyBlockState error: " + e.getMessage() + " output=" + outputString);
        }
    }

    private List<PropertyInfo> resolveProperties(Block block, BlockData blockData, ParsedBlockData parsed) {
        String cacheKey = parsed.id;
        List<PropertyDefinition> definitions = PROPERTY_CACHE.get(cacheKey);

        if (definitions == null && !PROPERTY_FAILURES.contains(cacheKey)) {
            try {
                definitions = readRuntimePropertyDefinitions(blockData);
                if (definitions != null) {
                    PROPERTY_CACHE.put(cacheKey, definitions);
                } else {
                    PROPERTY_FAILURES.add(cacheKey);
                }
            } catch (Exception e) {
                PROPERTY_FAILURES.add(cacheKey);
            }
        }

        if (definitions == null) {
            return fallbackProperties(parsed);
        }

        List<PropertyInfo> result = new ArrayList<>(definitions.size());
        for (PropertyDefinition def : definitions) {
            String currentValue = parsed.properties.get(def.name);
            if (currentValue == null && !def.possibleValues.isEmpty()) {
                currentValue = def.possibleValues.get(0);
            }
            result.add(new PropertyInfo(def.name, currentValue, def.possibleValues, def.isDirection));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<PropertyDefinition> readRuntimePropertyDefinitions(BlockData blockData) throws Exception {
        Object state = invokeNoArg(blockData, "getState");
        if (state == null) return null;

        Object rawProperties = invokeNoArg(state, "getProperties");
        if (!(rawProperties instanceof Collection<?> propertyCollection)) return null;

        List<PropertyDefinition> properties = new ArrayList<>();
        for (Object property : propertyCollection) {
            String name = String.valueOf(invokeNoArg(property, "getName"));
            Object valueClass = invokeNoArg(property, "getValueClass");
            String valueClassName = valueClass instanceof Class<?> clazz ? clazz.getName() : "";
            Object rawValues = invokeNoArg(property, "getPossibleValues");
            if (!(rawValues instanceof Collection<?> valuesCollection)) continue;

            List<Object> values = new ArrayList<>(valuesCollection);
            values.sort((a, b) -> ((Comparable<Object>) a).compareTo(b));

            List<String> valueNames = new ArrayList<>();
            for (Object value : values) {
                Object nameObj = invokeOneArg(property, "getName", value);
                valueNames.add(String.valueOf(nameObj).toLowerCase(Locale.ROOT));
            }

            properties.add(new PropertyDefinition(name, List.copyOf(valueNames),
                    valueClassName.endsWith(".Direction")));
        }

        return properties;
    }

    private List<PropertyInfo> fallbackProperties(ParsedBlockData parsed) {
        List<PropertyInfo> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : parsed.properties.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            List<String> values = fallbackValues(name, value);
            boolean direction = "facing".equals(name) && values.contains("north");
            result.add(new PropertyInfo(name, value, values, direction));
        }
        return result;
    }

    private static List<String> fallbackValues(String name, String currentValue) {
        return switch (name) {
            case "facing" -> List.of("down", "up", "north", "south", "west", "east");
            case "axis" -> List.of("x", "y", "z");
            case "half" -> List.of("top", "bottom");
            case "face" -> List.of("floor", "wall", "ceiling");
            case "type" -> {
                if ("left".equals(currentValue) || "right".equals(currentValue) || "single".equals(currentValue)) {
                    yield List.of("single", "left", "right");
                }
                yield List.of("top", "bottom", "double");
            }
            case "mode" -> List.of("compare", "subtract");
            case "hinge" -> List.of("left", "right");
            case "shape" -> {
                if ("ascending_east".equals(currentValue) || "north_south".equals(currentValue)) {
                    yield List.of("north_south", "east_west", "ascending_east", "ascending_west",
                            "ascending_north", "ascending_south", "south_east", "south_west",
                            "north_west", "north_east");
                }
                yield List.of("straight", "inner_left", "inner_right", "outer_left", "outer_right");
            }
            case "orientation" -> List.of("down_east", "down_north", "down_south", "down_west",
                    "up_east", "up_north", "up_south", "up_west",
                    "west_up", "east_up", "north_up", "south_up");
            case "attachment" -> List.of("floor", "ceiling", "single_wall", "double_wall");
            case "pose" -> List.of("standing", "sitting", "running", "spinning");
            case "open", "inverted" -> List.of("false", "true");
            case "bites" -> intValues(0, 6);
            case "delay" -> intValues(1, 4);
            case "note" -> intValues(0, 24);
            case "rotation" -> intValues(0, 15);
            default -> List.of(currentValue);
        };
    }

    private static List<String> intValues(int min, int max) {
        List<String> values = new ArrayList<>();
        for (int i = min; i <= max; i++) {
            values.add(Integer.toString(i));
        }
        return values;
    }

    private static PropertyInfo firstDirectionProperty(List<PropertyInfo> properties) {
        for (PropertyInfo p : properties) {
            if (p.isDirection && !"vertical_direction".equals(p.name)) return p;
        }
        return null;
    }

    private static String decodeDirection(int protocolValue, String current) {
        int decodedFacingIndex = (protocolValue & 0xF) >> 1;
        if (decodedFacingIndex == 6) {
            return switch (current) {
                case "down" -> "up";
                case "up" -> "down";
                case "north" -> "south";
                case "south" -> "north";
                case "west" -> "east";
                case "east" -> "west";
                default -> null;
            };
        }
        if (decodedFacingIndex >= 0 && decodedFacingIndex < DIRECTION_3D_ORDER.size()) {
            return DIRECTION_3D_ORDER.get(decodedFacingIndex);
        }
        return null;
    }

    private static int requiredBits(int size) {
        int power = 1, bits = 0;
        while (power < size) {
            power <<= 1;
            bits++;
        }
        return bits;
    }

    private static Object invokeNoArg(Object target, String name) throws Exception {
        Method method = findMethod(target.getClass(), name, 0);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Object invokeOneArg(Object target, String name, Object value) throws Exception {
        Method method = findMethod(target.getClass(), name, 1);
        method.setAccessible(true);
        return method.invoke(target, value);
    }

    private static Method findMethod(Class<?> type, String name, int paramCount) throws NoSuchMethodException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == paramCount) {
                    return m;
                }
            }
        }
        for (Method m : type.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == paramCount) {
                return m;
            }
        }
        throw new NoSuchMethodException(type.getName() + "#" + name + "/" + paramCount);
    }

    private static BlockPosition offset(BlockPosition pos, EnumWrappers.Direction direction) {
        return switch (direction) {
            case DOWN -> new BlockPosition(pos.getX(), pos.getY() - 1, pos.getZ());
            case UP -> new BlockPosition(pos.getX(), pos.getY() + 1, pos.getZ());
            case NORTH -> new BlockPosition(pos.getX(), pos.getY(), pos.getZ() - 1);
            case SOUTH -> new BlockPosition(pos.getX(), pos.getY(), pos.getZ() + 1);
            case WEST -> new BlockPosition(pos.getX() - 1, pos.getY(), pos.getZ());
            case EAST -> new BlockPosition(pos.getX() + 1, pos.getY(), pos.getZ());
        };
    }

    private void queuePlacement(UUID playerId, PlacementEntry entry) {
        ConcurrentLinkedDeque<PlacementEntry> queue = pendingPlacements.computeIfAbsent(
                playerId, k -> new ConcurrentLinkedDeque<>());
        queue.addLast(entry);
        while (queue.size() > 32) {
            queue.pollFirst();
        }
    }

    private PlacementEntry findMatchingPlacement(UUID playerId, Block placed, Block against) {
        ConcurrentLinkedDeque<PlacementEntry> queue = pendingPlacements.get(playerId);
        if (queue == null) return null;

        long now = System.currentTimeMillis();
        trimExpired(queue, now);

        for (PlacementEntry entry : queue) {
            if (isSamePlacement(entry, placed, against) && queue.remove(entry)) {
                if (queue.isEmpty()) pendingPlacements.remove(playerId, queue);
                return entry;
            }
        }

        if (queue.isEmpty()) pendingPlacements.remove(playerId, queue);
        return null;
    }

    private boolean isSamePlacement(PlacementEntry entry, Block placed, Block against) {
        if (entry.matchesPlacedBlock(placed) || entry.matchesClickedBlock(placed)) {
            return true;
        }
        return entry.matchesClickedBlock(against)
                || entry.isNearClickedBlock(placed);
    }

    private void trimExpired(ConcurrentLinkedDeque<PlacementEntry> queue, long now) {
        while (true) {
            PlacementEntry oldest = queue.peekFirst();
            if (oldest == null || !oldest.isExpired(now)) break;
            if (queue.pollFirst() == null) break;
        }
    }

    private record PropertyInfo(String name, String currentValue, List<String> possibleValues, boolean isDirection) {}

    private record PropertyDefinition(String name, List<String> possibleValues, boolean isDirection) {}

    private record ParsedBlockData(String id, LinkedHashMap<String, String> properties) {
        static ParsedBlockData parse(String blockDataString) {
            int bracket = blockDataString.indexOf('[');
            if (bracket < 0) {
                return new ParsedBlockData(blockDataString, new LinkedHashMap<>());
            }
            String id = blockDataString.substring(0, bracket);
            String propStr = blockDataString.substring(bracket + 1, blockDataString.length() - 1);
            LinkedHashMap<String, String> props = new LinkedHashMap<>();
            if (!propStr.isBlank()) {
                for (String part : propStr.split(",")) {
                    int eq = part.indexOf('=');
                    if (eq > 0 && eq < part.length() - 1) {
                        props.put(part.substring(0, eq), part.substring(eq + 1));
                    }
                }
            }
            return new ParsedBlockData(id, props);
        }

        String toBlockDataString(Map<String, String> newProperties) {
            if (newProperties.isEmpty()) return id;
            StringBuilder sb = new StringBuilder(id).append('[');
            boolean first = true;
            for (Map.Entry<String, String> e : newProperties.entrySet()) {
                if (!first) sb.append(',');
                sb.append(e.getKey()).append('=').append(e.getValue());
                first = false;
            }
            return sb.append(']').toString();
        }
    }

    private static class PlacementEntry {
        final UUID worldId;
        final BlockPosition clickedBlock;
        final BlockPosition expectedPlacedBlock;
        final EnumWrappers.Direction clickedFace;
        final int protocolValue;
        final long createdAt;

        PlacementEntry(UUID worldId, BlockPosition clickedBlock, BlockPosition expectedPlacedBlock,
                       EnumWrappers.Direction clickedFace, int protocolValue) {
            this.worldId = worldId;
            this.clickedBlock = clickedBlock;
            this.expectedPlacedBlock = expectedPlacedBlock;
            this.clickedFace = clickedFace;
            this.protocolValue = protocolValue;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isExpired(long now) {
            return now - createdAt > 2500;
        }

        boolean isExpired() {
            return isExpired(System.currentTimeMillis());
        }

        boolean matchesPlacedBlock(Block block) {
            return block.getWorld().getUID().equals(worldId)
                    && expectedPlacedBlock.getX() == block.getX()
                    && expectedPlacedBlock.getY() == block.getY()
                    && expectedPlacedBlock.getZ() == block.getZ();
        }

        boolean matchesClickedBlock(Block block) {
            return block.getWorld().getUID().equals(worldId)
                    && clickedBlock.getX() == block.getX()
                    && clickedBlock.getY() == block.getY()
                    && clickedBlock.getZ() == block.getZ();
        }

        boolean isNearClickedBlock(Block block) {
            return block.getWorld().getUID().equals(worldId)
                    && Math.abs(block.getX() - clickedBlock.getX())
                    + Math.abs(block.getY() - clickedBlock.getY())
                    + Math.abs(block.getZ() - clickedBlock.getZ()) <= 1;
        }
    }
}