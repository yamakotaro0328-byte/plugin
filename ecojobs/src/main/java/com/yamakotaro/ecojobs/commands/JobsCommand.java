package com.yamakotaro.ecojobs.commands;

import com.yamakotaro.ecojobs.ActionReward;
import com.yamakotaro.ecojobs.BoosterManager;
import com.yamakotaro.ecojobs.EcoJobsPlugin;
import com.yamakotaro.ecojobs.JobDefinition;
import com.yamakotaro.ecojobs.JobManager;
import com.yamakotaro.ecojobs.JobOverrides;
import com.yamakotaro.ecojobs.Messages;
import com.yamakotaro.ecojobs.PlayerJobManager;
import com.yamakotaro.ecojobs.PlayerJobProgress;
import com.yamakotaro.ecojobs.TabCompleteUtil;
import com.yamakotaro.ecojobs.menu.AdminMenuHolder;
import com.yamakotaro.ecojobs.menu.HubMenuHolder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class JobsCommand implements CommandExecutor, TabCompleter {

    private final EcoJobsPlugin plugin;
    private final JobManager jobManager;
    private final PlayerJobManager playerJobManager;
    private final JobOverrides jobOverrides;
    private final BoosterManager boosterManager;
    private final Messages messages;

    public JobsCommand(EcoJobsPlugin plugin, JobManager jobManager, PlayerJobManager playerJobManager,
                        JobOverrides jobOverrides, BoosterManager boosterManager, Messages messages) {
        this.plugin = plugin;
        this.jobManager = jobManager;
        this.playerJobManager = playerJobManager;
        this.jobOverrides = jobOverrides;
        this.boosterManager = boosterManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            // Bare /jobs (or /job) opens the menu directly rather than dumping a usage line -
            // handleMenu already covers the players-only/permission checks itself.
            handleMenu(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "join" -> handleJoin(sender, args);
            case "leave" -> handleLeave(sender, args);
            case "list" -> handleList(sender);
            case "stats" -> handleStats(sender, args);
            case "top" -> handleTop(sender, args);
            case "menu" -> handleMenu(sender);
            case "info" -> handleInfo(sender, args);
            case "prestige" -> handlePrestige(sender, args);
            case "admin" -> handleAdmin(sender);
            case "booster" -> handleBooster(sender, args);
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
            case JOB_DISABLED -> player.sendMessage(messages.get("jobs.job-disabled", Map.of("job", messages.jobName(jobId))));
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
        Player player = sender instanceof Player p ? p : null;
        Map<String, PlayerJobProgress> allProgress = player != null
                ? playerJobManager.allProgress(player.getUniqueId()) : Map.of();
        sender.sendMessage(messages.get("jobs.list-header", Map.of()));
        for (String jobId : jobManager.all().keySet()) {
            PlayerJobProgress progress = allProgress.get(jobId);
            if (progress == null) {
                sender.sendMessage(messages.get("jobs.list-entry-not-joined", Map.of("job", messages.jobName(jobId))));
                continue;
            }
            String key = player != null && playerJobManager.isJoined(player.getUniqueId(), jobId)
                    ? "jobs.list-entry-joined" : "jobs.list-entry-left";
            sender.sendMessage(messages.get(key, Map.of(
                    "job", messages.jobName(jobId),
                    "level", String.valueOf(progress.getLevel()),
                    "prestige", String.valueOf(progress.getPrestige()),
                    "xp", String.format("%.0f", progress.getXp()),
                    "next_xp", String.format("%.0f", playerJobManager.xpToNextLevel(progress.getLevel())))));
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
        Map<String, PlayerJobProgress> allProgress = playerJobManager.allProgress(target.getUniqueId());
        if (allProgress.isEmpty()) {
            sender.sendMessage(messages.get("jobs.stats-none", Map.of()));
            return;
        }
        sender.sendMessage(messages.get("jobs.stats-header", Map.of()));
        for (Map.Entry<String, PlayerJobProgress> entry : allProgress.entrySet()) {
            PlayerJobProgress progress = entry.getValue();
            boolean active = playerJobManager.isJoined(target.getUniqueId(), entry.getKey());
            sender.sendMessage(messages.get(active ? "jobs.stats-entry" : "jobs.stats-entry-inactive", Map.of(
                    "job", messages.jobName(entry.getKey()),
                    "level", String.valueOf(progress.getLevel()),
                    "prestige", String.valueOf(progress.getPrestige()),
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
                    "prestige", String.valueOf(entry.prestige()),
                    "xp", String.format("%.0f", entry.xp()))));
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ecojobs.use")) {
            sender.sendMessage(messages.get("general.no-permission", Map.of()));
            return;
        }
        if (args.length != 2) {
            sender.sendMessage(messages.get("jobs.usage", Map.of()));
            return;
        }
        String jobId = args[1].toLowerCase();
        JobDefinition job = jobManager.get(jobId);
        if (job == null) {
            sender.sendMessage(messages.get("jobs.unknown-job", Map.of("job", jobId)));
            return;
        }
        sender.sendMessage(messages.get("jobs.info-header", Map.of("job", messages.jobName(jobId))));
        // TreeMap for a stable, alphabetical order regardless of the underlying HashMap's order.
        Map<String, Map<String, ActionReward>> actions = new TreeMap<>(job.getActionsByType());
        boolean any = false;
        for (Map.Entry<String, Map<String, ActionReward>> actionType : actions.entrySet()) {
            for (Map.Entry<String, ActionReward> rewardEntry : new TreeMap<>(actionType.getValue()).entrySet()) {
                any = true;
                ActionReward reward = rewardEntry.getValue();
                sender.sendMessage(messages.get("jobs.info-entry", Map.of(
                        "key", rewardEntry.getKey(),
                        "money", formatReward(reward.money(), reward.moneyPerLevel()),
                        "xp", formatReward(reward.xp(), reward.xpPerLevel()))));
            }
        }
        if (!any) {
            sender.sendMessage(messages.get("jobs.info-empty", Map.of()));
        }
    }

    private static String formatReward(double flat, double perLevel) {
        if (perLevel > 0) {
            return String.format("%.2f/enchant-level", perLevel);
        }
        return String.format("%.2f", flat);
    }

    private void handlePrestige(CommandSender sender, String[] args) {
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
        switch (playerJobManager.prestige(player, jobId)) {
            case UNKNOWN_JOB -> player.sendMessage(messages.get("jobs.unknown-job", Map.of("job", jobId)));
            case NOT_JOINED -> player.sendMessage(messages.get("jobs.not-joined", Map.of("job", messages.jobName(jobId))));
            case NOT_MAX_LEVEL -> player.sendMessage(messages.get("jobs.prestige-not-max-level",
                    Map.of("job", messages.jobName(jobId), "max", String.valueOf(jobManager.maxLevel()))));
            case SUCCESS -> {
                // The broadcast (sent by PlayerJobManager.prestige itself) already tells everyone,
                // this player included, so there's nothing more to send here.
            }
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
        HubMenuHolder holder = new HubMenuHolder(messages);
        holder.render(player.hasPermission("ecojobs.admin"));
        player.openInventory(holder.getInventory());
    }

    private void handleAdmin(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("general.players-only", Map.of()));
            return;
        }
        if (!player.hasPermission("ecojobs.admin")) {
            player.sendMessage(messages.get("general.no-permission", Map.of()));
            return;
        }
        AdminMenuHolder holder = new AdminMenuHolder(messages);
        holder.render(jobManager, jobOverrides, boosterManager);
        player.openInventory(holder.getInventory());
    }

    private void handleBooster(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ecojobs.admin")) {
            sender.sendMessage(messages.get("general.no-permission", Map.of()));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(messages.get("jobs.booster-usage", Map.of()));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "start" -> handleBoosterStart(sender, args);
            case "stop" -> handleBoosterStop(sender, args);
            case "list" -> handleBoosterList(sender);
            default -> sender.sendMessage(messages.get("jobs.booster-usage", Map.of()));
        }
    }

    private void handleBoosterStart(CommandSender sender, String[] args) {
        if (args.length != 6) {
            sender.sendMessage(messages.get("jobs.booster-usage", Map.of()));
            return;
        }
        String scope = resolveBoosterScope(args[2]);
        if (scope == null) {
            sender.sendMessage(messages.get("jobs.unknown-job", Map.of("job", args[2])));
            return;
        }
        double moneyMultiplier;
        double xpMultiplier;
        int minutes;
        try {
            moneyMultiplier = Double.parseDouble(args[3]);
            xpMultiplier = Double.parseDouble(args[4]);
            minutes = Integer.parseInt(args[5]);
        } catch (NumberFormatException e) {
            sender.sendMessage(messages.get("jobs.booster-invalid-number", Map.of()));
            return;
        }
        boosterManager.start(scope, moneyMultiplier, xpMultiplier, minutes * 60_000L, sender.getName());
        Bukkit.getServer().sendMessage(messages.get("jobs.booster-started", Map.of(
                "scope", boosterScopeLabel(scope),
                "money", String.format("%.2f", moneyMultiplier),
                "xp", String.format("%.2f", xpMultiplier),
                "minutes", String.valueOf(minutes),
                "player", sender.getName())));
    }

    private void handleBoosterStop(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(messages.get("jobs.booster-usage", Map.of()));
            return;
        }
        String scope = resolveBoosterScope(args[2]);
        if (scope == null) {
            sender.sendMessage(messages.get("jobs.unknown-job", Map.of("job", args[2])));
            return;
        }
        if (boosterManager.stop(scope)) {
            Bukkit.getServer().sendMessage(messages.get("jobs.booster-stopped",
                    Map.of("scope", boosterScopeLabel(scope), "player", sender.getName())));
        } else {
            sender.sendMessage(messages.get("jobs.booster-not-active", Map.of("scope", boosterScopeLabel(scope))));
        }
    }

    private void handleBoosterList(CommandSender sender) {
        Collection<BoosterManager.ActiveBooster> active = boosterManager.active();
        sender.sendMessage(messages.get("jobs.booster-list-header", Map.of()));
        if (active.isEmpty()) {
            sender.sendMessage(messages.get("jobs.booster-list-empty", Map.of()));
            return;
        }
        for (BoosterManager.ActiveBooster booster : active) {
            long minutesLeft = Math.max(0, (booster.expiresAtMillis() - System.currentTimeMillis()) / 60_000);
            sender.sendMessage(messages.get("jobs.booster-list-entry", Map.of(
                    "scope", boosterScopeLabel(booster.scope()),
                    "money", String.format("%.2f", booster.moneyMultiplier()),
                    "xp", String.format("%.2f", booster.xpMultiplier()),
                    "minutes", String.valueOf(minutesLeft))));
        }
    }

    private String resolveBoosterScope(String arg) {
        if (arg.equalsIgnoreCase(BoosterManager.GLOBAL_SCOPE)) {
            return BoosterManager.GLOBAL_SCOPE;
        }
        String jobId = arg.toLowerCase();
        return jobManager.get(jobId) != null ? jobId : null;
    }

    private String boosterScopeLabel(String scope) {
        return scope.equals(BoosterManager.GLOBAL_SCOPE) ? messages.raw("jobs.booster-scope-all", Map.of()) : messages.jobName(scope);
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("ecojobs.admin")) {
            sender.sendMessage(messages.get("general.no-permission", Map.of()));
            return;
        }
        plugin.reloadPluginConfig();
        jobManager.load();
        sender.sendMessage(messages.get("general.reloaded", Map.of()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompleteUtil.filterPrefix(
                    List.of("join", "leave", "list", "stats", "top", "menu", "info", "prestige", "admin", "booster", "reload"), args[0]);
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "join", "leave", "top", "info", "prestige" ->
                        TabCompleteUtil.filterPrefix(new ArrayList<>(jobManager.all().keySet()), args[1]);
                case "stats" -> TabCompleteUtil.onlinePlayerNames(args[1], null);
                case "booster" -> TabCompleteUtil.filterPrefix(List.of("start", "stop", "list"), args[1]);
                default -> Collections.emptyList();
            };
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("booster")
                && (args[1].equalsIgnoreCase("start") || args[1].equalsIgnoreCase("stop"))) {
            List<String> scopes = new ArrayList<>(jobManager.all().keySet());
            scopes.add(BoosterManager.GLOBAL_SCOPE);
            return TabCompleteUtil.filterPrefix(scopes, args[2]);
        }
        return Collections.emptyList();
    }
}
