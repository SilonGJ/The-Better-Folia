package com.thebetterfolia.protocol;

import com.thebetterfolia.TheBetterFoliaPlugin;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

public final class NmsNbtHelper {

    private static volatile boolean coreInitialized;
    private static Constructor<?> compoundTagCtor;
    private static Class<?> compoundTagClass;
    private static Method putStringMethod;
    private static Method putIntMethod;
    private static Method putLongMethod;
    private static Method putByteArrayMethod;
    private static Method putIntArrayMethod;
    private static Method putLongArrayMethod;
    private static Method putTagMethod;
    private static Method getStringMethod;
    private static Method getIntMethod;
    private static Method getLongMethod;
    private static Method getByteArrayMethod;
    private static Method getIntArrayMethod;
    private static Method getLongArrayMethod;
    private static Method getCompoundMethod;
    private static Method getListMethod;
    private static Method removeMethod;
    private static Method getAllKeysMethod;
    private static Constructor<?> friendlyByteBufCtor;
    private static Method friendlyByteBufWriteVarIntMethod;
    private static Method friendlyByteBufWriteNbtMethod;
    private static Method friendlyByteBufReadNbtMethod;
    private static Method friendlyByteBufReadNbtUnlimitedMethod;
    private static Method nbtAccounterUnlimitedHeapMethod;
    private static Method friendlyByteBufReadableBytesMethod;
    private static Method doubleTagValueOfMethod;
    private static Method listTagSetMethod;
    private static Method friendlyByteBufGetBytesMethod;
    private static Method unpooledBufferMethod;
    private static Method unpooledWrappedBufferMethod;
    private static Method byteBufReleaseMethod;
    private static Method byteBufWriteBytesMethod;
    private static Method byteBufSetIndexMethod;
    private static Class<?> byteBufClass;
    private static Class<?> tagClass;
    private static Class<?> listTagClass;
    private static Method listTagGetMethod;
    private static Method listTagSizeMethod;
    private static Class<?> blockPosClass;
    private static Class<?> craftWorldClass;
    private static Method craftWorldGetHandleMethod;
    private static Class<?> levelClass;
    private static Method levelGetBlockEntityMethod;
    private static Method levelGetEntityMethod;
    private static Method levelRegistryAccessMethod;
    private static Method blockEntitySaveMethod;
    private static Method entitySaveWithoutIdMethod;
    private static Method entitySaveValueOutputMethod;
    private static Method friendlyByteBufWriteBlockPosMethod;
    private static Constructor<?> blockPosCtor;
    private static Class<?> entityTypeClass;
    private static Method entityGetTypeMethod;
    private static Method entityTypeGetIdMethod;
    private static Class<?> identifierClass;
    private static Method identifierToStringMethod;
    private static Class<?> tagValueOutputClass;
    private static Method tagValueOutputCreateWithContextMethod;
    private static Method tagValueOutputBuildResultMethod;
    private static Class<?> problemReporterClass;
    private static Class<?> scopedCollectorClass;
    private static Constructor<?> scopedCollectorCtor;
    private static boolean useTagValueOutput;
    
    // Entity spawn support
    private static Method listTagGetDoubleMethod;
    private static Method entityTypeLoadEntityRecursiveMethod;
    private static Method entityTypeByStringMethod;
    private static Object entitySpawnReasonLoad;
    private static Object entityProcessorNop;
    private static Method levelTryAddFreshEntityWithPassengersMethod;
    private static Method blockEntityLoadMethod;
    private static Method levelSetBlockEntityMethod;
    private static Method levelGetBlockStateMethod;
    private static Method blockEntityLoadStaticMethod;
    
    // World edit support
    private static Class<?> craftBlockStateClass;
    private static Method craftBlockStateGetDataMethod;
    private static Class<?> blockStateClass;
    private static Method blockStateGetBlockMethod;
    private static Method blockStateWithMethod;
    private static Method levelSetBlockMethod;
    private static Method blockStateGetPropertiesMethod;
    private static Method propertiesGetMethod;
    private static Method propertiesPutMethod;
    private static Method blockGetStateDefinitionMethod;
    private static Method stateDefinitionGetPossibleStatesMethod;

    private static void ensureCoreInit() {
        if (coreInitialized) return;
        try {
            Class<?> ctClass = Class.forName("net.minecraft.nbt.CompoundTag");
            compoundTagClass = ctClass;
            tagClass = Class.forName("net.minecraft.nbt.Tag");
            compoundTagCtor = ctClass.getConstructor();
            
            // Put methods
            putStringMethod = ctClass.getMethod("putString", String.class, String.class);
            putIntMethod = ctClass.getMethod("putInt", String.class, int.class);
            putLongMethod = ctClass.getMethod("putLong", String.class, long.class);
            putByteArrayMethod = ctClass.getMethod("putByteArray", String.class, byte[].class);
            putIntArrayMethod = ctClass.getMethod("putIntArray", String.class, int[].class);
            putLongArrayMethod = ctClass.getMethod("putLongArray", String.class, long[].class);
            
            // Get methods
            getStringMethod = ctClass.getMethod("getString", String.class);
            getIntMethod = ctClass.getMethod("getInt", String.class);
            getLongMethod = ctClass.getMethod("getLong", String.class);
            getByteArrayMethod = ctClass.getMethod("getByteArray", String.class);
            getIntArrayMethod = ctClass.getMethod("getIntArray", String.class);
            getLongArrayMethod = ctClass.getMethod("getLongArray", String.class);
            getCompoundMethod = ctClass.getMethod("getCompound", String.class);
            
            // Find getList method - try different signatures
            try {
                // Try first with two args
                getListMethod = ctClass.getMethod("getList", String.class, int.class);
            } catch (Exception e1) {
                // Try with one arg
                getListMethod = ctClass.getMethod("getList", String.class);
            }
            
            removeMethod = ctClass.getMethod("remove", String.class);
            
            // Find getAllKeys method
            try {
                getAllKeysMethod = ctClass.getMethod("getAllKeys");
            } catch (Exception e) {
                try {
                    getAllKeysMethod = ctClass.getMethod("keySet");
                } catch (Exception e2) {
                    TheBetterFoliaPlugin.getInstance().debug("NmsNbtHelper: Could not find getAllKeys or keySet method!");
                }
            }

            // ListTag
            listTagClass = Class.forName("net.minecraft.nbt.ListTag");
            listTagGetMethod = listTagClass.getMethod("getCompound", int.class);
            listTagSizeMethod = listTagClass.getMethod("size");

            // PutTag
            putTagMethod = ctClass.getMethod("put", String.class, tagClass);

            byteBufClass = Class.forName("io.netty.buffer.ByteBuf");
            Class<?> unpooledClass = Class.forName("io.netty.buffer.Unpooled");
            unpooledBufferMethod = unpooledClass.getMethod("buffer");
            unpooledWrappedBufferMethod = unpooledClass.getMethod("wrappedBuffer", byte[].class);
            byteBufReleaseMethod = byteBufClass.getMethod("release");
            byteBufWriteBytesMethod = byteBufClass.getMethod("writeBytes", byte[].class);
            byteBufSetIndexMethod = byteBufClass.getMethod("setIndex", int.class, int.class);

            Class<?> fbbClass = Class.forName("net.minecraft.network.FriendlyByteBuf");
            friendlyByteBufCtor = fbbClass.getConstructor(byteBufClass);
            friendlyByteBufWriteVarIntMethod = fbbClass.getMethod("writeVarInt", int.class);
            friendlyByteBufWriteNbtMethod = fbbClass.getMethod("writeNbt", tagClass);
            friendlyByteBufReadNbtMethod = fbbClass.getMethod("readNbt");
            friendlyByteBufReadNbtUnlimitedMethod = fbbClass.getMethod("readNbt", Class.forName("net.minecraft.nbt.NbtAccounter"));
            nbtAccounterUnlimitedHeapMethod = Class.forName("net.minecraft.nbt.NbtAccounter").getMethod("unlimitedHeap");
            friendlyByteBufReadableBytesMethod = fbbClass.getMethod("readableBytes");
            friendlyByteBufGetBytesMethod = fbbClass.getMethod("getBytes", int.class, byte[].class);

            // Entity NBT position manipulation
            doubleTagValueOfMethod = Class.forName("net.minecraft.nbt.DoubleTag").getMethod("valueOf", double.class);
            listTagSetMethod = listTagClass.getMethod("set", int.class, tagClass);

            coreInitialized = true;
        } catch (Exception e) {
            TheBetterFoliaPlugin.getInstance().getLogger().severe("NmsNbtHelper core init failed: " + e.getMessage());
            if (TheBetterFoliaPlugin.getInstance().isDebugEnabled()) {
                e.printStackTrace();
            }
            throw new RuntimeException("NmsNbtHelper core init failed", e);
        }
    }

    private static void tryInitBlockEntity() {
        ensureCoreInit();
        TheBetterFoliaPlugin plugin = TheBetterFoliaPlugin.getInstance();
        try {
            blockPosClass = Class.forName("net.minecraft.core.BlockPos");
            blockPosCtor = blockPosClass.getConstructor(int.class, int.class, int.class);

            craftWorldClass = Class.forName("org.bukkit.craftbukkit.CraftWorld");
            levelClass = Class.forName("net.minecraft.world.level.Level");
            craftWorldGetHandleMethod = craftWorldClass.getMethod("getHandle");
            levelGetBlockEntityMethod = levelClass.getMethod("getBlockEntity", blockPosClass);

            try {
                levelRegistryAccessMethod = levelClass.getMethod("registryAccess");
            } catch (Exception e) {
                plugin.debug("tryInitBlockEntity: registryAccess not found: " + e.getMessage());
            }

            levelGetEntityMethod = levelClass.getMethod("getEntity", int.class);

            Class<?> blockEntityClass = Class.forName("net.minecraft.world.level.block.entity.BlockEntity");
            blockStateClass = Class.forName("net.minecraft.world.level.block.state.BlockState");
            blockEntitySaveMethod = findSaveMethod(blockEntityClass, "saveWithFullMetadata", "save", "saveToTag");

            Class<?> entityClass = Class.forName("net.minecraft.world.entity.Entity");
            
            for (Method m : entityClass.getMethods()) {
                if (m.getName().equals("saveWithoutId")) {
                    m.setAccessible(true);
                    entitySaveWithoutIdMethod = m;
                    break;
                }
            }

            try {
                for (Method m : entityClass.getMethods()) {
                    if (m.getName().equals("save") && m.getParameterCount() == 1) {
                        Class<?> paramType = m.getParameterTypes()[0];
                        if (paramType.getName().equals("net.minecraft.world.level.storage.ValueOutput")) {
                            entitySaveValueOutputMethod = m;
                            m.setAccessible(true);
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                plugin.debug("tryInitBlockEntity: save(ValueOutput) not found: " + e.getMessage());
            }

            try {
                tagValueOutputClass = Class.forName("net.minecraft.world.level.storage.TagValueOutput");
                
                for (Method m : tagValueOutputClass.getMethods()) {
                    if (m.getName().equals("createWithContext") && m.getParameterCount() == 2) {
                        tagValueOutputCreateWithContextMethod = m;
                        m.setAccessible(true);
                        break;
                    }
                }
                
                try {
                    tagValueOutputBuildResultMethod = tagValueOutputClass.getMethod("buildResult");
                    tagValueOutputBuildResultMethod.setAccessible(true);
                } catch (Exception e) {
                    plugin.debug("tryInitBlockEntity: buildResult not found: " + e.getMessage());
                }
                
                problemReporterClass = Class.forName("net.minecraft.util.ProblemReporter");
                scopedCollectorClass = Class.forName("net.minecraft.util.ProblemReporter$ScopedCollector");
                scopedCollectorCtor = scopedCollectorClass.getConstructor(org.slf4j.Logger.class);
                
                useTagValueOutput = ((entitySaveValueOutputMethod != null || entitySaveWithoutIdMethod != null) && tagValueOutputCreateWithContextMethod != null && tagValueOutputBuildResultMethod != null);
            } catch (Exception e) {
                plugin.debug("tryInitBlockEntity: TagValueOutput not available: " + e.getMessage());
            }

            try {
                entityTypeClass = Class.forName("net.minecraft.world.entity.EntityType");
                entityGetTypeMethod = entityClass.getMethod("getType");
                
                String[] possibleMethods = {"getKey", "getId", "key", "id", "builtInRegistryHolder", "getRegistryKey"};
                for (String methodName : possibleMethods) {
                    try {
                        entityTypeGetIdMethod = entityTypeClass.getMethod(methodName);
                        break;
                    } catch (NoSuchMethodException ignored) {}
                }
                
                if (entityTypeGetIdMethod == null) {
                    for (Method m : entityTypeClass.getMethods()) {
                        plugin.debug("tryInitBlockEntity: EntityType method: " + m.getName() + " - " + m.getReturnType());
                    }
                }
                
                try {
                    identifierClass = Class.forName("net.minecraft.resources.ResourceLocation");
                    identifierToStringMethod = identifierClass.getMethod("toString");
                } catch (Exception e) {
                    plugin.debug("tryInitBlockEntity: ResourceLocation not available, will use toString fallback");
                }

                try {
                    listTagGetDoubleMethod = listTagClass.getMethod("getDouble", int.class);
                } catch (Exception e) {
                    if (plugin.isDebugEnabled()) plugin.debug("tryInitBlockEntity: ListTag.getDouble not found: " + e.getMessage());
                }

                try {
                    Class<?> spawnReasonClass = Class.forName("net.minecraft.world.entity.EntitySpawnReason");
                    Class<?> entityProcessorClass = Class.forName("net.minecraft.world.entity.EntityProcessor");

                    for (Method m : entityTypeClass.getMethods()) {
                        if (m.getName().equals("byString") && m.getParameterCount() == 1
                            && m.getParameterTypes()[0] == String.class) {
                            entityTypeByStringMethod = m;
                            entityTypeByStringMethod.setAccessible(true);
                            break;
                        }
                    }

                    for (Method m : entityTypeClass.getMethods()) {
                        if (m.getName().equals("loadEntityRecursive") && m.getParameterCount() == 5
                            && m.getParameterTypes()[0] == entityTypeClass
                            && m.getParameterTypes()[1] == compoundTagClass) {
                            entityTypeLoadEntityRecursiveMethod = m;
                            entityTypeLoadEntityRecursiveMethod.setAccessible(true);
                            break;
                        }
                    }
                    if (entityTypeLoadEntityRecursiveMethod == null) {
                        for (Method m : entityTypeClass.getMethods()) {
                            if (m.getName().equals("loadEntityRecursive") && m.getParameterCount() == 4
                                && m.getParameterTypes()[0] == compoundTagClass) {
                                entityTypeLoadEntityRecursiveMethod = m;
                                entityTypeLoadEntityRecursiveMethod.setAccessible(true);
                                break;
                            }
                        }
                    }
                    try {
                        java.lang.reflect.Field nopField = entityProcessorClass.getField("NOP");
                        entityProcessorNop = nopField.get(null);
                    } catch (Exception e) {
                        if (plugin.isDebugEnabled()) plugin.debug("tryInitBlockEntity: EntityProcessor.NOP not found: " + e.getMessage());
                    }
                    for (java.lang.reflect.Field f : spawnReasonClass.getFields()) {
                        if (f.getName().equals("LOAD")) {
                            entitySpawnReasonLoad = f.get(null);
                            break;
                        }
                    }
                    if (entitySpawnReasonLoad == null) {
                        for (Object constant : spawnReasonClass.getEnumConstants()) {
                            if (constant.toString().equals("LOAD")) {
                                entitySpawnReasonLoad = constant;
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    if (plugin.isDebugEnabled()) plugin.debug("tryInitBlockEntity: Entity loading reflection failed: " + e.getMessage());
                }

                try {
                    Class<?> serverLevelClass = Class.forName("net.minecraft.server.level.ServerLevel");
                    for (Method m : serverLevelClass.getMethods()) {
                        if (m.getName().equals("tryAddFreshEntityWithPassengers") && m.getParameterCount() == 1) {
                            levelTryAddFreshEntityWithPassengersMethod = m;
                            levelTryAddFreshEntityWithPassengersMethod.setAccessible(true);
                            break;
                        }
                    }
                } catch (Exception e) {
                    if (plugin.isDebugEnabled()) plugin.debug("tryInitBlockEntity: tryAddFreshEntityWithPassengers not found: " + e.getMessage());
                }

                try {
                    Class<?> holderLookupProviderClass = Class.forName("net.minecraft.core.HolderLookup$Provider");
                    blockEntityLoadStaticMethod = blockEntityClass.getMethod("loadStatic", blockPosClass, blockStateClass, compoundTagClass, holderLookupProviderClass);
                } catch (NoSuchMethodException e2) {
                    if (plugin.isDebugEnabled()) plugin.debug("tryInitBlockEntity: BlockEntity.loadStatic not found: " + e2.getMessage());
                } catch (ClassNotFoundException e2) {
                    if (plugin.isDebugEnabled()) plugin.debug("tryInitBlockEntity: HolderLookup.Provider not found: " + e2.getMessage());
                }

                try {
                    levelSetBlockEntityMethod = levelClass.getMethod("setBlockEntity", blockEntityClass);
                } catch (NoSuchMethodException e2) {
                    if (plugin.isDebugEnabled()) plugin.debug("tryInitBlockEntity: Level.setBlockEntity not found: " + e2.getMessage());
                }

                try {
                    levelGetBlockStateMethod = levelClass.getMethod("getBlockState", blockPosClass);
                } catch (NoSuchMethodException e2) {
                    if (plugin.isDebugEnabled()) plugin.debug("tryInitBlockEntity: Level.getBlockState not found: " + e2.getMessage());
                }
            } catch (Exception e) {
                if (plugin.isDebugEnabled()) {
                    plugin.debug("tryInitBlockEntity: Entity type methods failed: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            Class<?> fbbClass = Class.forName("net.minecraft.network.FriendlyByteBuf");
            friendlyByteBufWriteBlockPosMethod = null;
            for (java.lang.reflect.Method m : fbbClass.getMethods()) {
                if (m.getName().equals("writeBlockPos") && m.getParameterCount() == 1) {
                    friendlyByteBufWriteBlockPosMethod = m;
                    break;
                }
            }

            if (blockEntitySaveMethod != null) {
                plugin.debug("BlockEntity support initialized");
            } else {
                plugin.getLogger().warning("BlockEntity save method not found, will send empty NBT");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("BlockEntity init failed: " + e.getMessage());
            if (plugin.isDebugEnabled()) {
                e.printStackTrace();
            }
        }
    }

    private static Method findSaveMethod(Class<?> targetClass, String... methodNames) {
        TheBetterFoliaPlugin plugin = TheBetterFoliaPlugin.getInstance();
        java.util.List<Method> allMethods = new java.util.ArrayList<>();
        allMethods.addAll(java.util.Arrays.asList(targetClass.getDeclaredMethods()));
        allMethods.addAll(java.util.Arrays.asList(targetClass.getMethods()));

        for (String name : methodNames) {
            for (Method m : allMethods) {
                if (m.getName().equals(name)) {
                    m.setAccessible(true);
                    Class<?> returnType = m.getReturnType();
                    if (returnType.getSimpleName().equals("CompoundTag") ||
                        returnType.getSimpleName().equals("Tag") ||
                        (tagClass != null && tagClass.isAssignableFrom(returnType))) {
                        return m;
                    }
                }
            }
        }

        for (Method m : allMethods) {
            m.setAccessible(true);
            Class<?> returnType = m.getReturnType();
            if (returnType.getSimpleName().equals("CompoundTag") ||
                returnType.getSimpleName().equals("Tag") ||
                (tagClass != null && tagClass.isAssignableFrom(returnType))) {
                if (m.getName().contains("save") || m.getName().contains("write")) {
                    return m;
                }
            }
        }

        return null;
    }

    public static Object createCompoundTag() {
        ensureCoreInit();
        try {
            return compoundTagCtor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create CompoundTag", e);
        }
    }

    public static void putString(Object tag, String key, String value) {
        ensureCoreInit();
        try {
            putStringMethod.invoke(tag, key, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to putString", e);
        }
    }

    public static void putInt(Object tag, String key, int value) {
        ensureCoreInit();
        try {
            putIntMethod.invoke(tag, key, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to putInt", e);
        }
    }

    private static Object unwrap(Object obj) {
        if (obj instanceof Optional) {
            Optional<?> opt = (Optional<?>) obj;
            return opt.orElse(null);
        }
        return obj;
    }
    
    public static String getString(Object tag, String key) {
        ensureCoreInit();
        try {
            Object result = getStringMethod.invoke(tag, key);
            result = unwrap(result);
            return (String) result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to getString", e);
        }
    }

    public static void removeTag(Object tag, String key) {
        ensureCoreInit();
        try {
            removeMethod.invoke(tag, key);
        } catch (Exception e) {
            throw new RuntimeException("Failed to remove tag key: " + key, e);
        }
    }

    public static boolean hasTag(Object tag, String key) {
        ensureCoreInit();
        try {
            Method containsMethod = tag.getClass().getMethod("contains", String.class);
            return (boolean) containsMethod.invoke(tag, key);
        } catch (Exception e) {
            return false;
        }
    }

    public static Object deserializeNbt(byte[] data) {
        ensureCoreInit();
        try {
            Object byteBuf = unpooledWrappedBufferMethod.invoke(null, data);
            Object fbb = friendlyByteBufCtor.newInstance(byteBuf);

            // Use unlimited NBT accounter for large schematics (e.g. world eaters)
            Object unlimitedAccounter = nbtAccounterUnlimitedHeapMethod.invoke(null);
            Object nbt = friendlyByteBufReadNbtUnlimitedMethod.invoke(fbb, unlimitedAccounter);
            byteBufReleaseMethod.invoke(byteBuf);

            return nbt;
        } catch (Exception e) {
            TheBetterFoliaPlugin.getInstance().debug("Failed to deserialize NBT: " + e.getMessage());
            throw new RuntimeException("Failed to deserialize NBT", e);
        }
    }

    public static byte[] serializePacket(int packetType, Object compoundTag) {
        ensureCoreInit();
        try {
            Object byteBuf = unpooledBufferMethod.invoke(null);
            Object fbb = friendlyByteBufCtor.newInstance(byteBuf);

            friendlyByteBufWriteVarIntMethod.invoke(fbb, packetType);
            friendlyByteBufWriteNbtMethod.invoke(fbb, compoundTag);

            int readableBytes = (int) friendlyByteBufReadableBytesMethod.invoke(fbb);
            byte[] bytes = new byte[readableBytes];
            friendlyByteBufGetBytesMethod.invoke(fbb, 0, bytes);
            byteBufReleaseMethod.invoke(byteBuf);

            return bytes;
        } catch (Exception e) {
            TheBetterFoliaPlugin.getInstance().debug("Failed to serialize packet: " + e.getMessage());
            throw new RuntimeException("Failed to serialize packet", e);
        }
    }

    public static Object createBlockPos(int x, int y, int z) {
        tryInitBlockEntity();
        try {
            return blockPosCtor.newInstance(x, y, z);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create BlockPos", e);
        }
    }

    public static Object getBlockEntityNbt(World world, Object pos) {
        tryInitBlockEntity();
        try {
            Object level = craftWorldGetHandleMethod.invoke(world);
            Object blockEntity = levelGetBlockEntityMethod.invoke(level, pos);
            if (blockEntity == null) {
                return createCompoundTag();
            }

            if (blockEntitySaveMethod == null) {
                return createCompoundTag();
            }

            Object[] args = prepareSaveMethodArgs(level, blockEntitySaveMethod);
            Object result = blockEntitySaveMethod.invoke(blockEntity, args);

            if (result != null) {
                return result;
            }

            return createCompoundTag();
        } catch (Exception e) {
            TheBetterFoliaPlugin.getInstance().debug("Failed to get BlockEntity NBT: " + e.getMessage());
            return createCompoundTag();
        }
    }

    private static Object[] prepareSaveMethodArgs(Object level, Method saveMethod) {
        int paramCount = saveMethod.getParameterCount();
        if (paramCount == 0) {
            return new Object[0];
        }

        Class<?>[] paramTypes = saveMethod.getParameterTypes();
        Object[] args = new Object[paramCount];
        Object registryAccess = null;

        if (levelRegistryAccessMethod != null) {
            try {
                registryAccess = levelRegistryAccessMethod.invoke(level);
            } catch (Exception e) {
                // registryAccess not available
            }
        }

        for (int i = 0; i < paramCount; i++) {
            Class<?> paramType = paramTypes[i];
            if (registryAccess != null && paramType.isInstance(registryAccess)) {
                args[i] = registryAccess;
            } else if (paramType.isPrimitive()) {
                if (paramType == int.class) args[i] = 0;
                else if (paramType == boolean.class) args[i] = false;
                else if (paramType == long.class) args[i] = 0L;
                else if (paramType == float.class) args[i] = 0.0f;
                else if (paramType == double.class) args[i] = 0.0;
                else args[i] = 0;
            } else {
                args[i] = null;
            }
        }

        return args;
    }

    public static byte[] serializeBlockEntityPacket(Object pos, Object nbt) {
        ensureCoreInit();
        tryInitBlockEntity();
        try {
            Object byteBuf = unpooledBufferMethod.invoke(null);
            Object fbb = friendlyByteBufCtor.newInstance(byteBuf);

            friendlyByteBufWriteVarIntMethod.invoke(fbb, 5);

            if (friendlyByteBufWriteBlockPosMethod != null) {
                friendlyByteBufWriteBlockPosMethod.invoke(fbb, pos);
            } else {
                int x = (int) pos.getClass().getMethod("getX").invoke(pos);
                int y = (int) pos.getClass().getMethod("getY").invoke(pos);
                int z = (int) pos.getClass().getMethod("getZ").invoke(pos);
                friendlyByteBufWriteVarIntMethod.invoke(fbb, x);
                friendlyByteBufWriteVarIntMethod.invoke(fbb, y);
                friendlyByteBufWriteVarIntMethod.invoke(fbb, z);
            }
            friendlyByteBufWriteNbtMethod.invoke(fbb, nbt);

            int readableBytes = (int) friendlyByteBufReadableBytesMethod.invoke(fbb);
            byte[] bytes = new byte[readableBytes];
            friendlyByteBufGetBytesMethod.invoke(fbb, 0, bytes);
            byteBufReleaseMethod.invoke(byteBuf);
            return bytes;
        } catch (Exception e) {
            TheBetterFoliaPlugin.getInstance().debug("Failed to serialize block entity packet: " + e.getMessage());
            throw new RuntimeException("Failed to serialize block entity packet", e);
        }
    }

    public static Object getEntityNbt(World world, int entityId) {
        tryInitBlockEntity();
        TheBetterFoliaPlugin plugin = TheBetterFoliaPlugin.getInstance();
        try {
            Object level = craftWorldGetHandleMethod.invoke(world);

            Object nmsEntity = levelGetEntityMethod.invoke(level, entityId);
            if (nmsEntity == null) {
                plugin.debug("getEntityNbt: Entity not found in level!");
                Object fallback = createCompoundTag();
                putString(fallback, "id", "minecraft:pig");
                return fallback;
            }

            if (useTagValueOutput) {
                try {
                    return saveEntityWithTagValueOutput(nmsEntity, level);
                } catch (Exception e) {
                    plugin.debug("getEntityNbt: TagValueOutput failed: " + e.getMessage());
                }
            }

            String entityIdStr = getEntityTypeId(nmsEntity);
            Object fallbackTag = createCompoundTag();
            if (entityIdStr != null) {
                putString(fallbackTag, "id", entityIdStr);
            } else {
                putString(fallbackTag, "id", "minecraft:pig");
            }
            return fallbackTag;
        } catch (Exception e) {
            plugin.debug("Failed to get Entity NBT: " + e.getMessage());
            if (plugin.isDebugEnabled()) {
                e.printStackTrace();
            }
            Object fallback = createCompoundTag();
            putString(fallback, "id", "minecraft:pig");
            return fallback;
        }
    }

    private static Object saveEntityWithTagValueOutput(Object nmsEntity, Object level) throws Exception {
        Object registryAccess = levelRegistryAccessMethod.invoke(level);

        Object problemReporter = scopedCollectorCtor.newInstance(LoggerFactory.getLogger(TheBetterFoliaPlugin.class));

        Object tagValueOutput = tagValueOutputCreateWithContextMethod.invoke(null, problemReporter, registryAccess);

        entitySaveWithoutIdMethod.invoke(nmsEntity, tagValueOutput, true, true, true);

        Object nbt = tagValueOutputBuildResultMethod.invoke(tagValueOutput);

        String entityIdStr = getEntityTypeId(nmsEntity);
        if (entityIdStr != null) {
            putString(nbt, "id", entityIdStr);
        }

        // If problemReporter is AutoCloseable, close it
        if (problemReporter instanceof AutoCloseable) {
            try {
                ((AutoCloseable) problemReporter).close();
            } catch (Exception ignored) {}
        }

        return nbt;
    }

    // Entity type ID extraction methods =====
    
    private static String getEntityTypeId(Object nmsEntity) throws Exception {
        if (entityGetTypeMethod == null) {
            return null;
        }
        
        Object entityType = entityGetTypeMethod.invoke(nmsEntity);
        
        if (entityTypeGetIdMethod != null) {
            try {
                Object id = entityTypeGetIdMethod.invoke(entityType);
                if (id != null) {
                    if (identifierClass != null && identifierClass.isInstance(id)) {
                        return (String) identifierToStringMethod.invoke(id);
                    }
                    String extracted = extractEntityTypeFromHolder(id);
                    if (extracted != null) {
                        return extracted;
                    }
                }
            } catch (Exception e) {
                // fall through to holder reflection
            }
        }
        
        try {
            Object entityType2 = entityGetTypeMethod.invoke(nmsEntity);
            Method holderMethod = null;
            try {
                holderMethod = entityType2.getClass().getMethod("builtInRegistryHolder");
            } catch (NoSuchMethodException ignored) {}
            if (holderMethod == null) {
                try {
                    holderMethod = entityType2.getClass().getMethod("getKey");
                } catch (NoSuchMethodException ignored) {}
            }
            if (holderMethod != null) {
                Object holder = holderMethod.invoke(entityType2);
                if (holder != null) {
                    String extracted = extractEntityTypeFromHolder(holder);
                    if (extracted != null) {
                        return extracted;
                    }
                }
            }
        } catch (Exception e) {
            // fall through
        }
        
        return null;
    }
    
    private static String extractEntityTypeFromHolder(Object holder) {
        try {
            // holder.key() -> Optional<ResourceKey>
            Method keyMethod = holder.getClass().getMethod("key");
            Object optionalResult = keyMethod.invoke(holder);
            if (optionalResult instanceof Optional) {
                Optional<?> opt = (Optional<?>) optionalResult;
                if (opt.isPresent()) {
                    Object resourceKey = opt.get();
                    // resourceKey.location() -> ResourceLocation
                    try {
                        Method locationMethod = resourceKey.getClass().getMethod("location");
                        Object location = locationMethod.invoke(resourceKey);
                        String result = location.toString();
                        if (result != null && !result.isEmpty() && result.contains(":")) {
                            return result;
                        }
                    } catch (Exception e) {
                        String keyStr = resourceKey.toString();
                        int sep = keyStr.indexOf(" / ");
                        if (sep >= 0) {
                            String after = keyStr.substring(sep + 3).trim();
                            if (after.endsWith("]")) {
                                after = after.substring(0, after.length() - 1);
                            }
                            return after;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        String str = holder.toString();
        int start = str.indexOf("[");
        int end = str.indexOf("]");
        if (start >= 0 && end > start) {
            String inner = str.substring(start + 1, end);
            int sep = inner.indexOf(" / ");
            if (sep >= 0) {
                String after = inner.substring(sep + 3).trim();
                if (after.endsWith("]")) after = after.substring(0, after.length() - 1);
                return after;
            }
        }
        return null;
    }
    
    public static byte[] serializeEntityPacket(int entityId, Object nbt) {
        ensureCoreInit();
        tryInitBlockEntity();
        try {
            Object byteBuf = unpooledBufferMethod.invoke(null);
            Object fbb = friendlyByteBufCtor.newInstance(byteBuf);

            friendlyByteBufWriteVarIntMethod.invoke(fbb, 6);
            friendlyByteBufWriteVarIntMethod.invoke(fbb, entityId);
            friendlyByteBufWriteNbtMethod.invoke(fbb, nbt);

            int readableBytes = (int) friendlyByteBufReadableBytesMethod.invoke(fbb);
            byte[] bytes = new byte[readableBytes];
            friendlyByteBufGetBytesMethod.invoke(fbb, 0, bytes);
            byteBufReleaseMethod.invoke(byteBuf);
            return bytes;
        } catch (Exception e) {
            TheBetterFoliaPlugin.getInstance().debug("Failed to serialize entity packet: " + e.getMessage());
            throw new RuntimeException("Failed to serialize entity packet", e);
        }
    }

    public static void sendMetadata(Player player, String channel, String name, String id, int version, String servuxVersion) {
        try {
            Object tag = createCompoundTag();
            putString(tag, "name", name);
            putString(tag, "id", id);
            putInt(tag, "version", version);
            putString(tag, "servux", servuxVersion);

            byte[] bytes = serializePacket(1, tag);
            PacketSender.sendPayload(player, channel, bytes);
        } catch (Exception e) {
            TheBetterFoliaPlugin.getInstance().debug("Failed to send metadata: " + e.getMessage());
        }
    }

    public static byte[] serializeTweaksBlockEntityPacket(Object pos, Object nbt) {
        return serializeBlockEntityPacket(pos, nbt);
    }

    public static byte[] serializeTweaksEntityPacket(int entityId, Object nbt) {
        return serializeEntityPacket(entityId, nbt);
    }
    
    // ===== New helper methods for schematic paste =====
    
    public static void putLong(Object tag, String key, long value) {
        ensureCoreInit();
        try {
            putLongMethod.invoke(tag, key, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to putLong", e);
        }
    }
    
    public static void putIntArray(Object tag, String key, int[] value) {
        ensureCoreInit();
        try {
            putIntArrayMethod.invoke(tag, key, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to putIntArray", e);
        }
    }
    
    public static int getInt(Object tag, String key) {
        ensureCoreInit();
        try {
            Object result = getIntMethod.invoke(tag, key);
            result = unwrap(result);
            if (result == null) return 0;
            return (int) result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to getInt", e);
        }
    }
    
    public static long getLong(Object tag, String key) {
        ensureCoreInit();
        try {
            Object result = getLongMethod.invoke(tag, key);
            result = unwrap(result);
            if (result == null) return 0L;
            return (long) result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to getLong", e);
        }
    }
    
    public static int[] getIntArray(Object tag, String key) {
        ensureCoreInit();
        try {
            Object result = getIntArrayMethod.invoke(tag, key);
            result = unwrap(result);
            return (int[]) result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to getIntArray", e);
        }
    }
    
    public static long[] getLongArray(Object tag, String key) {
        ensureCoreInit();
        try {
            Object result = getLongArrayMethod.invoke(tag, key);
            result = unwrap(result);
            return (long[]) result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to getLongArray", e);
        }
    }
    
    public static Object getCompound(Object tag, String key) {
        ensureCoreInit();
        try {
            Object result = getCompoundMethod.invoke(tag, key);
            result = unwrap(result);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to getCompound", e);
        }
    }
    
    public static Object getList(Object tag, String key, int type) {
        ensureCoreInit();
        try {
            Object result;
            if (getListMethod.getParameterCount() == 2) {
                result = getListMethod.invoke(tag, key, type);
            } else {
                result = getListMethod.invoke(tag, key);
            }
            result = unwrap(result);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to getList", e);
        }
    }
    
    public static int getListSize(Object listTag) {
        ensureCoreInit();
        try {
            return (int) listTagSizeMethod.invoke(listTag);
        } catch (Exception e) {
            throw new RuntimeException("Failed to getListSize", e);
        }
    }
    
    public static Object getListCompound(Object listTag, int index) {
        ensureCoreInit();
        try {
            Object result = listTagGetMethod.invoke(listTag, index);
            result = unwrap(result);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to getListCompound", e);
        }
    }
    
    @SuppressWarnings("unchecked")
    public static java.util.Set<String> getAllKeys(Object tag) {
        ensureCoreInit();
        try {
            if (getAllKeysMethod == null) {
                return new java.util.HashSet<>();
            }
            Object result = getAllKeysMethod.invoke(tag);
            if (result instanceof Optional) {
                Optional<java.util.Set<String>> opt = (Optional<java.util.Set<String>>) result;
                return opt.orElse(new java.util.HashSet<>());
            }
            return (java.util.Set<String>) result;
        } catch (Exception e) {
            return new java.util.HashSet<>();
        }
    }
    
    public static void putTag(Object tag, String key, Object value) {
        ensureCoreInit();
        try {
            putTagMethod.invoke(tag, key, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to putTag", e);
        }
    }
    
    // ===== Block state parsing =====
    private static void tryInitBlockState() {
        if (craftBlockStateClass != null) return;
        
        try {
            craftBlockStateClass = Class.forName("org.bukkit.craftbukkit.block.data.CraftBlockData");
            craftBlockStateGetDataMethod = craftBlockStateClass.getMethod("getState");
        } catch (Exception e) {
            // Block state parsing not available
        }
    }
    
    // ===== Block placement =====
    public static void setBlock(World world, int x, int y, int z, String blockId, java.util.Map<String, String> properties) {
        tryInitBlockEntity();
        tryInitBlockState();
        TheBetterFoliaPlugin plugin = TheBetterFoliaPlugin.getInstance();
        
        try {
            // Get NMS level
            Object level = craftWorldGetHandleMethod.invoke(world);
            
            // Create BlockPos
            Object blockPos = createBlockPos(x, y, z);
            
            // Parse block from id and properties
            Object blockState = parseBlockState(level, blockId, properties);
            
            if (blockState != null) {
                // Set block in world (flags: 11 = block update + send to client)
                if (levelSetBlockMethod == null) {
                    levelSetBlockMethod = level.getClass().getMethod("setBlock", blockPosClass, blockStateClass, int.class);
                }
                
                levelSetBlockMethod.invoke(level, blockPos, blockState, 11);
            }
        } catch (Exception e) {
            plugin.debug("Failed to set block: " + e.getMessage());
            if (plugin.isDebugEnabled()) {
                e.printStackTrace();
            }
        }
    }
    
    private static Object parseBlockState(Object level, String blockId, java.util.Map<String, String> properties) throws Exception {
        TheBetterFoliaPlugin plugin = TheBetterFoliaPlugin.getInstance();
        
        try {
            // Try to use Bukkit's BlockData
            org.bukkit.Material material = org.bukkit.Material.matchMaterial(blockId.replace("minecraft:", ""));
            if (material == null) {
                plugin.debug("parseBlockState: unknown material: " + blockId);
                return null;
            }
            
            org.bukkit.block.data.BlockData blockData = material.createBlockData();
            
            // Apply properties
            for (java.util.Map.Entry<String, String> entry : properties.entrySet()) {
                try {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    
                    // Common property types
                    if (blockData instanceof org.bukkit.block.data.Directional && key.equals("facing")) {
                        ((org.bukkit.block.data.Directional) blockData).setFacing(org.bukkit.block.BlockFace.valueOf(value.toUpperCase()));
                    } else if (blockData instanceof org.bukkit.block.data.Ageable && key.equals("age")) {
                        ((org.bukkit.block.data.Ageable) blockData).setAge(Integer.parseInt(value));
                    } else if (blockData instanceof org.bukkit.block.data.Levelled && key.equals("level")) {
                        ((org.bukkit.block.data.Levelled) blockData).setLevel(Integer.parseInt(value));
                    } else if (blockData instanceof org.bukkit.block.data.Powerable && key.equals("powered")) {
                        ((org.bukkit.block.data.Powerable) blockData).setPowered(Boolean.parseBoolean(value));
                    } else if (blockData instanceof org.bukkit.block.data.Openable && key.equals("open")) {
                        ((org.bukkit.block.data.Openable) blockData).setOpen(Boolean.parseBoolean(value));
                    } else if (blockData instanceof org.bukkit.block.data.Attachable && key.equals("attached")) {
                        ((org.bukkit.block.data.Attachable) blockData).setAttached(Boolean.parseBoolean(value));
                    } else if (blockData instanceof org.bukkit.block.data.Lightable && key.equals("lit")) {
                        ((org.bukkit.block.data.Lightable) blockData).setLit(Boolean.parseBoolean(value));
                    } else if (blockData instanceof org.bukkit.block.data.Waterlogged && key.equals("waterlogged")) {
                        ((org.bukkit.block.data.Waterlogged) blockData).setWaterlogged(Boolean.parseBoolean(value));
                    }
                } catch (Exception e) {
                    plugin.debug("parseBlockState: failed to apply property " + entry.getKey() + "=" + entry.getValue() + ": " + e.getMessage());
                }
            }
            
            // Convert Bukkit BlockData to NMS BlockState
            try {
                Object nmsData = craftBlockStateGetDataMethod.invoke(blockData);
                return nmsData;
            } catch (Exception e) {
                plugin.debug("parseBlockState: failed to convert to NMS: " + e.getMessage());
                // Fallback: just use Bukkit API
                return null;
            }
        } catch (Exception e) {
            plugin.debug("parseBlockState: failed: " + e.getMessage());
            return null;
        }
    }

    // ===== Schematic paste support =====

    public static double getListDouble(Object listTag, int index) {
        ensureCoreInit();
        tryInitBlockEntity();
        try {
            Object result = listTagGetDoubleMethod.invoke(listTag, index);
            result = unwrap(result);
            return result instanceof Double ? (double) result : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    public static void applyTileEntityNbt(org.bukkit.World world, int x, int y, int z, Object teNbt) {
        tryInitBlockEntity();
        TheBetterFoliaPlugin plugin = TheBetterFoliaPlugin.getInstance();
        if (blockEntityLoadStaticMethod == null || levelSetBlockEntityMethod == null || levelGetBlockStateMethod == null || levelRegistryAccessMethod == null) {
            plugin.debug("applyTileEntityNbt: Required methods not initialized, skipping");
            return;
        }
        try {
            Object pos = blockPosCtor.newInstance(x, y, z);
            Object level = craftWorldGetHandleMethod.invoke(world);
            Object blockState = levelGetBlockStateMethod.invoke(level, pos);
            Object registries = levelRegistryAccessMethod.invoke(level);

            // Create new block entity from NBT: BlockEntity.loadStatic(BlockPos, BlockState, CompoundTag, HolderLookup.Provider)
            Object blockEntity = blockEntityLoadStaticMethod.invoke(null, pos, blockState, teNbt, registries);
            if (blockEntity != null) {
                // Set block entity on level
                levelSetBlockEntityMethod.invoke(level, blockEntity);
                // Mark dirty so changes are saved
                Method setChangedMethod = blockEntity.getClass().getMethod("setChanged");
                setChangedMethod.invoke(blockEntity);
            }
        } catch (Exception e) {
            plugin.debug("applyTileEntityNbt: Failed: " + e.getMessage());
            if (plugin.isDebugEnabled()) {
                e.printStackTrace();
            }
        }
    }

    public static void spawnEntityFromNbt(org.bukkit.World world, Object entityNbt, double x, double y, double z) {
        tryInitBlockEntity();
        TheBetterFoliaPlugin plugin = TheBetterFoliaPlugin.getInstance();
        if (entityTypeLoadEntityRecursiveMethod == null || levelTryAddFreshEntityWithPassengersMethod == null) {
            plugin.debug("spawnEntityFromNbt: Entity loading not initialized, skipping");
            return;
        }
        try {
            // Remove UUID so the game generates a new one
            removeTag(entityNbt, "UUID");

            // Get NMS level (ServerLevel)
            Object level = craftWorldGetHandleMethod.invoke(world);

            Object nmsEntity;
            if (entityTypeLoadEntityRecursiveMethod.getParameterCount() == 5 && entityTypeByStringMethod != null) {
                // 5-param version: loadEntityRecursive(EntityType<?>, CompoundTag, Level, EntitySpawnReason, EntityProcessor)
                String id = getString(entityNbt, "id");
                if (id == null || id.isEmpty()) {
                    plugin.debug("spawnEntityFromNbt: Entity has no 'id' tag, skipping");
                    return;
                }
                Optional<?> optEntityType = (Optional<?>) entityTypeByStringMethod.invoke(null, id);
                if (optEntityType.isEmpty()) {
                    plugin.debug("spawnEntityFromNbt: Unknown entity type: " + id);
                    return;
                }
                Object entityType = optEntityType.get();
                nmsEntity = entityTypeLoadEntityRecursiveMethod.invoke(null, entityType, entityNbt, level, entitySpawnReasonLoad, entityProcessorNop);
            } else {
                // 4-param fallback: loadEntityRecursive(CompoundTag, Level, EntitySpawnReason, EntityProcessor)
                nmsEntity = entityTypeLoadEntityRecursiveMethod.invoke(null, entityNbt, level, entitySpawnReasonLoad, entityProcessorNop);
            }

            if (nmsEntity != null) {
                // Set exact position as final safety measure
                Method setPosMethod = nmsEntity.getClass().getMethod("setPos", double.class, double.class, double.class);
                setPosMethod.invoke(nmsEntity, x, y, z);

                levelTryAddFreshEntityWithPassengersMethod.invoke(level, nmsEntity);
            } else {
                plugin.debug("spawnEntityFromNbt: loadEntityRecursive returned null");
            }
        } catch (Exception e) {
            plugin.debug("spawnEntityFromNbt: Failed to spawn entity: " + e.getMessage());
            if (plugin.isDebugEnabled()) {
                e.printStackTrace();
            }
        }
    }

    public static void setPosInTag(Object entityNbt, double x, double y, double z) {
        ensureCoreInit();
        try {
            Object posList = getList(entityNbt, "Pos", 6);
            if (posList == null) return;
            Object dx = doubleTagValueOfMethod.invoke(null, x);
            Object dy = doubleTagValueOfMethod.invoke(null, y);
            Object dz = doubleTagValueOfMethod.invoke(null, z);
            listTagSetMethod.invoke(posList, 0, dx);
            listTagSetMethod.invoke(posList, 1, dy);
            listTagSetMethod.invoke(posList, 2, dz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set entity Pos in NBT", e);
        }
    }
}
