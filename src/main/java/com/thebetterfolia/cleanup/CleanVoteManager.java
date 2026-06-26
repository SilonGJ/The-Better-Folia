package com.thebetterfolia.cleanup;

import com.thebetterfolia.MessageUtil;
import com.thebetterfolia.TheBetterFoliaPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class CleanVoteManager {

    private final TheBetterFoliaPlugin plugin;
    private VoteSession activeVote;
    private long lastVoteEndTime = 0;

    private boolean enabled;
    private int cooldown;
    private int duration;
    private double ratio;
    private int minPlayers;
    private String applyMessage;
    private String successMessage;
    private String failMessage;
    private String cooldownMessage;
    private String alreadyMessage;

    public CleanVoteManager(TheBetterFoliaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        enabled = plugin.getConfig().getBoolean("cleanup.vote.enabled", true);
        cooldown = plugin.getConfig().getInt("cleanup.vote.cooldown", 120);
        duration = plugin.getConfig().getInt("cleanup.vote.duration", 30);
        ratio = plugin.getConfig().getDouble("cleanup.vote.ratio", 0.5);
        minPlayers = plugin.getConfig().getInt("cleanup.vote.min_players", 1);
        applyMessage = plugin.getConfig().getString("cleanup.vote.apply_message",
                "<yellow>[投票] <green>{player}</green> <yellow>申请手动清理掉落物！点击 <click:run_command:/clean yes><hover:show_text:'<yellow>点击同意清理'><green>此处</green></hover></click> <yellow>同意（需 {ratio}% 玩家，当前 {votes}/{needed}）");
        successMessage = plugin.getConfig().getString("cleanup.vote.success_message",
                "<yellow>[投票] <green>投票通过！</green>");
        failMessage = plugin.getConfig().getString("cleanup.vote.fail_message",
                "<yellow>[投票] <red>投票未通过，清理申请已取消</red>");
        cooldownMessage = plugin.getConfig().getString("cleanup.vote.cooldown_message",
                "<red>清理投票冷却中，剩余 <green>{time}</green> <red>秒");
        alreadyMessage = plugin.getConfig().getString("cleanup.vote.already_message",
                "<red>已有清理投票正在进行中");
    }

    public boolean tryStartVote(Player player) {
        if (!enabled) return false;

        if (activeVote != null && !activeVote.isExpired()) {
            player.sendMessage(MessageUtil.parse(alreadyMessage));
            return true;
        }

        long now = System.currentTimeMillis();
        long remaining = (lastVoteEndTime + (cooldown * 1000L)) - now;
        if (remaining > 0) {
            player.sendMessage(MessageUtil.parse(cooldownMessage,
                    "{time}", String.valueOf((remaining / 1000) + 1)));
            return true;
        }

        int online = Bukkit.getOnlinePlayers().size();
        if (online < minPlayers) {
            player.sendMessage(MessageUtil.parse("<red>在线玩家不足 " + minPlayers + " 人，无法发起清理投票"));
            return true;
        }

        int needed = Math.max(1, (int) Math.ceil(online * ratio));

        activeVote = new VoteSession(player.getUniqueId());

        if (activeVote.yesVotes.size() >= needed) {
            activeVote.completed = true;
            CleanupManager cm = CleanupManager.getInstance();
            cm.executeCleanup();
            Component component = MessageUtil.parse(successMessage);
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(component);
            }
            lastVoteEndTime = System.currentTimeMillis();
            activeVote = null;
        } else {
            broadcastVoteMessage();
            scheduleVoteEnd();
        }
        return true;
    }

    private void broadcastVoteMessage() {
        int online = Bukkit.getOnlinePlayers().size();
        int needed = Math.max(1, (int) Math.ceil(online * ratio));
        Component component = MessageUtil.parse(applyMessage,
                "{player}", activeVote.initiatorName,
                "{ratio}", String.valueOf((int) (ratio * 100)),
                "{votes}", String.valueOf(activeVote.yesVotes.size()),
                "{needed}", String.valueOf(needed));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(component);
        }
    }

    private void scheduleVoteEnd() {
        Runnable endTask = () -> {
            if (activeVote == null || activeVote.completed) return;
            activeVote.completed = true;
            Component component = MessageUtil.parse(failMessage);
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(component);
            }
            lastVoteEndTime = System.currentTimeMillis();
            activeVote = null;
        };

        if (plugin.isFolia()) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, (task) -> endTask.run(), duration * 20L);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, endTask, duration * 20L);
        }
    }

    public boolean tryVoteYes(Player player) {
        if (activeVote == null || activeVote.isExpired() || activeVote.completed) {
            player.sendMessage(MessageUtil.parse("<red>当前没有进行中的清理投票"));
            return true;
        }

        if (activeVote.yesVotes.contains(player.getUniqueId())) {
            player.sendMessage(MessageUtil.parse("<red>你已经投过同意了"));
            return true;
        }

        activeVote.yesVotes.add(player.getUniqueId());
        int online = Bukkit.getOnlinePlayers().size();
        int needed = Math.max(1, (int) Math.ceil(online * ratio));
        int current = activeVote.yesVotes.size();

        if (current >= needed) {
            activeVote.completed = true;
            CleanupManager cm = CleanupManager.getInstance();
            cm.executeCleanup();
            Component component = MessageUtil.parse(successMessage);
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(component);
            }
            lastVoteEndTime = System.currentTimeMillis();
            activeVote = null;
        } else {
            Component component = MessageUtil.parse("<yellow>[投票] <green>" + player.getName() + "</green> <yellow>同意了清理（" + current + "/" + needed + "）");
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(component);
            }
        }
        return true;
    }

    private class VoteSession {
        final UUID initiator;
        final String initiatorName;
        final long startTime;
        final Set<UUID> yesVotes = new HashSet<>();
        boolean completed = false;

        VoteSession(UUID initiator) {
            this.initiator = initiator;
            this.initiatorName = Bukkit.getPlayer(initiator) != null ? Bukkit.getPlayer(initiator).getName() : "未知";
            this.startTime = System.currentTimeMillis();
            this.yesVotes.add(initiator);
        }

        boolean isExpired() {
            return System.currentTimeMillis() - startTime > duration * 1000L;
        }
    }
}
