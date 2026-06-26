package com.thebetterfolia.cleanup;

import com.thebetterfolia.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CleanCommand implements CommandExecutor {

    private final CleanVoteManager voteManager;

    public CleanCommand(CleanVoteManager voteManager) {
        this.voteManager = voteManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse("<red>只有玩家才能使用此命令"));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("yes")) {
            voteManager.tryVoteYes(player);
        } else {
            voteManager.tryStartVote(player);
        }
        return true;
    }
}
