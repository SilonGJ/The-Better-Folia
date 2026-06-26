package com.thebetterfolia.cleanup;

import com.thebetterfolia.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RubishCommand implements CommandExecutor {

    private final RubishGUI gui;
    private final Map<UUID, Long> lastUse = new HashMap<>();
    private static final long COOLDOWN_MS = 1000;

    public RubishCommand(RubishGUI gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse("<red>只有玩家才能使用此命令"));
            return true;
        }

        long now = System.currentTimeMillis();
        Long last = lastUse.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_MS) return true;
        lastUse.put(player.getUniqueId(), now);

        gui.open(player);
        return true;
    }
}
