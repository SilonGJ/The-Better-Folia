package com.thebetterfolia;

import com.thebetterfolia.cleanup.CleanCommand;
import com.thebetterfolia.cleanup.CleanVoteManager;
import com.thebetterfolia.cleanup.CleanupManager;
import com.thebetterfolia.cleanup.RubishCommand;
import com.thebetterfolia.cleanup.RubishGUI;
import com.thebetterfolia.protocol.ProtocolManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class TheBetterFoliaPlugin extends JavaPlugin {

    private static TheBetterFoliaPlugin instance;
    private ServerEnvironment serverEnvironment;
    private ProtocolManager protocolManager;
    private CleanupManager cleanupManager;
    private CleanVoteManager voteManager;

    public enum ServerEnvironment {
        FOLIA,
        PAPER,
        SPIGOT,
        BUKKIT,
        UNKNOWN
    }

    public static TheBetterFoliaPlugin getInstance() {
        return instance;
    }

    public ServerEnvironment getServerEnvironment() {
        return serverEnvironment;
    }

    public ProtocolManager getProtocolManager() {
        return protocolManager;
    }

    @Override
    public void onEnable() {
        instance = this;
        detectServerEnvironment();

        saveDefaultConfigManually();

        getLogger().info("§a☀ The-Better-Folia enabled | Environment: " + serverEnvironment);
        getLogger().info("§7服务器类型: " + Bukkit.getName() + " " + Bukkit.getVersion());

        initProtocols();
        getLogger().info("§a✅ Protocol module initialized");

        initCleanup();
    }

    public boolean isProtocolEnabled(String configPath) {
        return getConfig().getBoolean(configPath, true);
    }

    public boolean isContainerPreviewEnabled() {
        return getConfig().getBoolean("servux.container_preview.enabled", true);
    }

    public boolean isPreviewInventoryEnabled() {
        return getConfig().getBoolean("servux.container_preview.preview_inventory", true);
    }

    public boolean isPreviewEntitiesEnabled() {
        return getConfig().getBoolean("servux.container_preview.preview_entities", true);
    }

    public boolean isLitematicsEnabled() {
        return getConfig().getBoolean("schematica.litematics", true);
    }

    public boolean isDebugEnabled() {
        return getConfig().getBoolean("debug", false);
    }

    public void debug(String message) {
        if (isDebugEnabled()) {
            getLogger().info(message);
        }
    }

    private void saveDefaultConfigManually() {
        File dataFolder = getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        File configFile = new File(dataFolder, "config.yml");
        if (!configFile.exists()) {
            try (InputStream in = getResource("config.yml")) {
                if (in != null) {
                    Files.copy(in, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } else {
                    getLogger().warning("config.yml not found in JAR!");
                }
            } catch (IOException e) {
                getLogger().severe("Failed to save config.yml: " + e.getMessage());
            }
        }
    }

    @Override
    public void onDisable() {
        if (protocolManager != null) {
            protocolManager.shutdown();
        }
        if (cleanupManager != null) {
            cleanupManager.shutdown();
        }
        getLogger().info("§c☀ The-Better-Folia disabled");
        instance = null;
    }

    private void initProtocols() {
        if (!isPaper()) {
            getLogger().info("§eNon-Paper/Folia environment, protocol support skipped");
            return;
        }
        protocolManager = new ProtocolManager();
        protocolManager.initialize();
    }

    private void initCleanup() {
        cleanupManager = new CleanupManager(this);
        cleanupManager.loadConfig();

        RubishGUI rubishGUI = new RubishGUI();
        Bukkit.getPluginManager().registerEvents(rubishGUI, this);

        RubishCommand rubishCommand = new RubishCommand(rubishGUI);
        getCommand("rubish").setExecutor(rubishCommand);

        voteManager = new CleanVoteManager(this);
        voteManager.loadConfig();

        CleanCommand cleanCommand = new CleanCommand(voteManager);
        getCommand("clean").setExecutor(cleanCommand);

        getLogger().info("§a✅ Item cleanup module initialized");
    }

    private void detectServerEnvironment() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            serverEnvironment = ServerEnvironment.FOLIA;
        } catch (ClassNotFoundException e) {
            try {
                Class.forName("com.destroystokyo.paper.PaperConfig");
                serverEnvironment = ServerEnvironment.PAPER;
            } catch (ClassNotFoundException ex) {
                try {
                    Class.forName("org.spigotmc.SpigotConfig");
                    serverEnvironment = ServerEnvironment.SPIGOT;
                } catch (ClassNotFoundException exc) {
                    serverEnvironment = ServerEnvironment.BUKKIT;
                }
            }
        }
    }

    public boolean isFolia() {
        return serverEnvironment == ServerEnvironment.FOLIA;
    }

    public boolean isPaper() {
        return serverEnvironment == ServerEnvironment.PAPER
                || serverEnvironment == ServerEnvironment.FOLIA;
    }

    public boolean isFoliaSupported() {
        return isFolia();
    }
}