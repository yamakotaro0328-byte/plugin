package com.yamakotaro.ecojobs.commands;

import com.yamakotaro.ecojobs.EcoJobsPlugin;
import com.yamakotaro.ecojobs.JobManager;
import com.yamakotaro.ecojobs.Messages;
import com.yamakotaro.ecojobs.PlayerJobManager;
import com.yamakotaro.ecojobs.PlayerJobProgress;
import com.yamakotaro.ecojobs.TabCompleteUtil;
import com.yamakotaro.ecojobs.menu.JobsMenuHolder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class JobsCommand implements CommandExecutor, TabCompleter {

    private final EcoJobsPlugin plugin;
    private final JobManager jobManager;
    private final PlayerJobManager playerJobManager;
    private final Messages messages;

    public JobsCommand(EcoJobsPlugin plugin, JobManager jobManager, PlayerJobManager playerJobManager, Messages messages) {
        this.plugin = plugin;
        this.jobManager = jobManager;
        this.playerJobManager = playerJobManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(messages.get("jobs.usage", Map.of()));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "join" -> handleJoin(sender, args);
            case "leave" -> handleLeave(sender, args);
            case "list" -> handleList(sender);
            case "stats" -> handleStats(sender, args);
            case "top" -> handleTop(sender, args);
            case "menu" -> handleMenu(sender);
            case "reload" -> handleReload(sender);
            default -> sender.sendMessage(messages.get("jobs.usage", Map.of()));
        }
        return true;
    }

    private void handleJoin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("general.players-only", Map.of()));
            return;
        }
        if (!player.hasPermission("ecojobs.use")) {
            player.sendMessage(messages.get("general.no-permission", Map.of()));
            return;
        }
        if (args.length != 2) {
            player.sendMessage(messages.get("jobs.usage", Map.of()));
            return;
        }
        String jobId = args[1].toLowerCase();
        switch (playerJobManager.join(player, jobId)) {
            case UNKNOWN_JOB -> player.sendMessage(messages.get("jobs.unknown-job", Map.of("job", jobId)));
            case ALREADY_JOINED -> player.sendMessage(messages.get("jobs.already-joined", Map.of("job", messages.jobName(jobId))));
            case MAX_JOBS_REACHED -> player.sendMessage(messages.get("jobs.max-jobs-reached",
                    Map.of("max", String.valueOf(jobManager.maxConcurrentJobs()))));
            case SUCCESS -> player.sendMessage(messages.get("jobs.joined", Map.of("job", messages.jobName(jobId))));
        }
    }

    private void handleLeave(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("general.players-only", Map.of()));
            return;
        }
        if (!player.hasPermission("ecojobs.use")) {
            player.sendMessage(messages.get("general.no-permission", Map.of()));
            return;
        }
        if (args.length != 2) {
            player.sendMessage(messages.get("jobs.usage", Map.of()));
            return;
        }
        String jobId = args[1].toLowerCase();
        switch (playerJobManager.leave(player.getUniqueId(), jobId)) {
            case UNKNOWN_JOB -> player.sendMessage(messages.get("jobs.unknown-job", Map.of("job", jobId)));
            case NOT_JOINED -> player.sendMessage(messages.get("jobs.not-joined", Map.of("job", messages.jobName(jobId))));
            case SUCCESS -> player.sendMessage(messages.get("jobs.left", Map.of("job", messages.jobName(jobId))));
        }
    }

    private void handleList(CommandSender sender) {
        if (!sender.hasPermission("ecojobs.use")) {
            sender.sendMessage(messages.get("general.no-permission", Map.of()));
            return;
        }
        Map<String, PlayerJobProgress> joined = sender instanceof Player player
                ? playerJobManager.joinedJobs(player.getUniqueId()) : Map.of();
        sender.sendMessage(messages.get("jobs.list-header", Map.of()));
        for (String jobId : jobManager.all().keySet()) {
            PlayerJobProgress progress = joined.get(jobId);
            if (progress != null) {
                sender.sendMessage(messages.get("jobs.list-entry-joined", Map.of(
                        "job", messages.jobName(jobId),
                        "level", String.valueOf(progress.getLevel()),
                        "xp", String.format("%.0f", progress.getXp()),
                        "next_xp", String.format("%.0f", playerJobManager.xpToNextLevel(progress.getLevel())))));
            } else {
                sender.sendMessage(messages.get("jobs.list-entry-not-joined", Map.of("job", messages.jobName(jobId))));
            }
        }
    }

    private void handleStats(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ecojobs.use")) {
            sender.sendMessage(messages.get("general.no-permission", Map.of()));
            return;
        }
        Player target;
        if (args.length >= 2) {
            target = plugin.getServer().getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(messages.get("general.player-not-found", Map.of("player", args[1])));
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(messages.get("general.players-only", Map.of()));
            return;
        }
        Map<String, PlayerJobProgress> joined = playerJobManager.joinedJobs(target.getUniqueId());
        if (joined.isEmpty()) {
            sender.sendMessage(messages.get("jobs.stats-none", Map.of()));
            return;
        }
        sender.sendMessage(messages.get("jobs.stats-header", Map.of()));
        for (Map.Entry<String, PlayerJobProgress> entry : joined.entrySet()) {
            PlayerJobProgress progress = entry.getValue();
            sender.sendMessage(messages.get("jobs.stats-entry", Map.of(
                    "job", messages.jobName(entry.getKey()),
                    "level", String.valueOf(progress.getLevel()),
                    "xp", String.format("%.0f", progress.getXp()),
                    "next_xp", String.format("%.0f", playerJobManager.xpToNextLevel(progress.getLevel())))));
        }
    }

    private void handleTop(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ecojobs.top")) {
            sender.sendMessage(messages.get("general.no-permission", Map.of()));
            return;
        }
        if (args.length != 2) {
            sender.sendMessage(messages.get("jobs.usage", Map.of()));
            return;
        }
        String jobId = args[1].toLowerCase();
        if (jobManager.get(jobId) == null) {
            sender.sendMessage(messages.get("jobs.unknown-job", Map.of("job", jobId)));
            return;
        }
        List<PlayerJobManager.TopEntry> top = playerJobManager.top(jobId, 10);
        sender.sendMessage(messages.get("jobs.top-header", Map.of("job", messages.jobName(jobId))));
        if (top.isEmpty()) {
            sender.sendMessage(messages.get("jobs.top-empty", Map.of("job", messages.jobName(jobId))));
            return;
        }
        int rank = 1;
        for (PlayerJobManager.TopEntry entry : top) {
            sender.sendMessage(messages.get("jobs.top-entry", Map.of(
                    "rank", String.valueOf(rank++),
                    "player", entry.name(),
                    "level", String.valueOf(entry.level()),
                    "xp", String.format("%.0f", entry.xp()))));
        }
    }

    private void handleMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("general.players-only", Map.of()));
            return;
        }
        if (!player.hasPermission("ecojobs.use")) {
            player.sendMessage(messages.get("general.no-permission", Map.of()));
            return;
        }
        JobsMenuHolder holder = new JobsMenuHolder(messages);
        holder.render(jobManager, playerJobManager, player.getUniqueId());
        player.openInventory(holder.getInventory());
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("ecojobs.admin")) {
            sender.sendMessage(messages.get("general.no-permission", Map.of()));
            return;
        }
        plugin.reloadConfig();
        jobManager.load();
        sender.sendMessage(messages.get("general.reloaded", Map.of()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompleteUtil.filterPrefix(List.of("join", "leave", "list", "stats", "top", "menu", "reload"), args[0]);
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "join", "leave", "top" -> TabCompleteUtil.filterPrefix(new ArrayList<>(jobManager.all().keySet()), args[1]);
                case "stats" -> TabCompleteUtil.onlinePlayerNames(args[1], null);
                default -> Collections.emptyList();
            };
        }
        return Collections.emptyList();
    }
}
