package com.yamakotaro.ecojobs;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks every player's joined jobs, levels, xp, and prestige; applies action rewards (with the
 * per-level and per-prestige pay bonuses, and level-up/max-level handling); and persists it all
 * to player-jobs.yml. Actions fire far more often than admin edits in the other plugins'
 * managers, so this saves on a timer (see {@link EcoJobsPlugin}) rather than synchronously on
 * every single change.
 */
public class PlayerJobManager {

    public enum JoinResult { SUCCESS, UNKNOWN_JOB, ALREADY_JOINED, MAX_JOBS_REACHED }

    public enum LeaveResult { SUCCESS, UNKNOWN_JOB, NOT_JOINED }

    public enum PrestigeResult { SUCCESS, UNKNOWN_JOB, NOT_JOINED, NOT_MAX_LEVEL }

    public record TopEntry(String name, int level, double xp, int prestige) {
    }

    private final EcoJobsPlugin plugin;
    private final JobManager jobManager;
    private final EconomyHolder economyHolder;
    private final Messages messages;
    private final Map<UUID, PlayerJobData> data = new HashMap<>();
    private final File file;
    private boolean dirty;
    private boolean noEconomyWarned;

    public PlayerJobManager(EcoJobsPlugin plugin, JobManager jobManager, EconomyHolder economyHolder, Messages messages) {
        this.plugin = plugin;
        this.jobManager = jobManager;
        this.economyHolder = economyHolder;
        this.messages = messages;
        this.file = new File(plugin.getDataFolder(), "player-jobs.yml");
        load();
    }

    public boolean isJoined(UUID uuid, String jobId) {
        PlayerJobData playerData = data.get(uuid);
        return playerData != null && playerData.getJoined().contains(jobId);
    }

    /**
     * Currently-active jobs only (eligible for rewards right now) - used for reward eligibility,
     * the /jobs list join-state, and the GUI menu's join/leave toggle. For a player's permanent
     * record, including jobs they've since left, see {@link #allProgress}.
     */
    public Map<String, PlayerJobProgress> joinedJobs(UUID uuid) {
        PlayerJobData playerData = data.get(uuid);
        if (playerData == null) {
            return Map.of();
        }
        Map<String, PlayerJobProgress> result = new LinkedHashMap<>();
        for (String jobId : playerData.getJoined()) {
            PlayerJobProgress progress = playerData.getProgress().get(jobId);
            if (progress != null) {
                result.put(jobId, progress);
            }
        }
        return result;
    }

    /**
     * Every job this player has ever joined, with its permanent level/xp/prestige, regardless of
     * whether they're still actively in it - used for /jobs stats and leaderboards, since leaving
     * a job only stops future rewards, it never resets progress already earned.
     */
    public Map<String, PlayerJobProgress> allProgress(UUID uuid) {
        PlayerJobData playerData = data.get(uuid);
        return playerData != null ? playerData.getProgress() : Map.of();
    }

    public JoinResult join(Player player, String jobId) {
        JobDefinition job = jobManager.get(jobId);
        if (job == null) {
            return JoinResult.UNKNOWN_JOB;
        }
        PlayerJobData playerData = dataFor(player);
        if (playerData.getJoined().contains(job.getId())) {
            return JoinResult.ALREADY_JOINED;
        }
        boolean bypassLimit = player.hasPermission("ecojobs.bypass.maxjobs");
        if (!bypassLimit && playerData.getJoined().size() >= jobManager.maxConcurrentJobs()) {
            return JoinResult.MAX_JOBS_REACHED;
        }
        // computeIfAbsent so rejoining a job left earlier resumes its retained level/xp/prestige
        // instead of restarting at level 1.
        playerData.getProgress().computeIfAbsent(job.getId(), k -> new PlayerJobProgress(1, 0));
        playerData.getJoined().add(job.getId());
        dirty = true;
        return JoinResult.SUCCESS;
    }

    public LeaveResult leave(UUID uuid, String jobId) {
        JobDefinition job = jobManager.get(jobId);
        if (job == null) {
            return LeaveResult.UNKNOWN_JOB;
        }
        PlayerJobData playerData = data.get(uuid);
        if (playerData == null || !playerData.getJoined().remove(job.getId())) {
            return LeaveResult.NOT_JOINED;
        }
        // Deliberately keeps the entry in playerData.getProgress() - leaving only stops future
        // rewards, it never discards level/xp/prestige already earned (see PlayerJobData's docs).
        dirty = true;
        return LeaveResult.SUCCESS;
    }

    /**
     * Resets a job from max level back to level 1 (keeping the job joined) in exchange for a
     * permanent extra pay-bonus stack (see {@link JobManager#prestigeBonusPerPrestige()}). Only
     * allowed once the job has actually reached max level.
     */
    public PrestigeResult prestige(Player player, String jobId) {
        JobDefinition job = jobManager.get(jobId);
        if (job == null) {
            return PrestigeResult.UNKNOWN_JOB;
        }
        PlayerJobProgress progress = joinedJobs(player.getUniqueId()).get(job.getId());
        if (progress == null) {
            return PrestigeResult.NOT_JOINED;
        }
        if (progress.getLevel() < jobManager.maxLevel()) {
            return PrestigeResult.NOT_MAX_LEVEL;
        }
        progress.setLevel(1);
        progress.setXp(0);
        progress.setPrestige(progress.getPrestige() + 1);
        dirty = true;
        Bukkit.getServer().sendMessage(messages.get("jobs.prestige-broadcast", Map.of(
                "player", player.getName(),
                "job", messages.jobName(job.getId()),
                "prestige", String.valueOf(progress.getPrestige()))));
        return PrestigeResult.SUCCESS;
    }

    /**
     * Applies one action's reward for the given job to the player, if (and only if) they've
     * joined that job: deposits money (scaled by level/prestige bonus, via Vault if available),
     * adds xp, handles any level-ups, and shows an action-bar confirmation. A no-op if the player
     * hasn't joined this job, or if the job has no reward configured for this action at all.
     */
    public void reward(Player player, String jobId, String actionType, String key, double scale) {
        JobDefinition job = jobManager.get(jobId);
        if (job == null) {
            return;
        }
        ActionReward actionReward = job.getReward(actionType, key);
        if (actionReward == null) {
            return;
        }
        PlayerJobProgress progress = joinedJobs(player.getUniqueId()).get(job.getId());
        if (progress == null) {
            return;
        }
        applyReward(player, job, progress, actionReward.moneyFor(scale), actionReward.xpFor(scale));
    }

    /**
     * Called by ExplorerListener whenever a player's farthest-from-spawn distance (tracked
     * separately per world, so a fresh world always has new milestones to reach) crosses one or
     * more new milestones; pays out once per milestone crossed (usually just one).
     */
    public void checkExplorerMilestones(Player player, String worldName, double currentDistance) {
        JobDefinition explorer = jobManager.get("explorer");
        if (explorer == null) {
            return;
        }
        PlayerJobProgress progress = joinedJobs(player.getUniqueId()).get("explorer");
        if (progress == null) {
            return;
        }
        PlayerJobData playerData = data.get(player.getUniqueId());
        double perMilestone = jobManager.explorerDistancePerMilestone();
        if (perMilestone <= 0) {
            return;
        }
        double farthest = playerData.getExplorerFarthestDistance(worldName);
        double previousMilestones = Math.floor(farthest / perMilestone);
        double currentMilestones = Math.floor(currentDistance / perMilestone);
        if (currentDistance > farthest) {
            playerData.setExplorerFarthestDistance(worldName, currentDistance);
            dirty = true;
        }
        int crossed = (int) (currentMilestones - previousMilestones);
        for (int i = 0; i < crossed; i++) {
            applyReward(player, explorer, progress,
                    jobManager.explorerMoneyPerMilestone(), jobManager.explorerXpPerMilestone());
        }
    }

    private void applyReward(Player player, JobDefinition job, PlayerJobProgress progress, double baseMoney, double baseXp) {
        double levelMultiplier = 1
                + progress.getLevel() * jobManager.payBonusPerLevel()
                + progress.getPrestige() * jobManager.prestigeBonusPerPrestige();
        double money = baseMoney * levelMultiplier;

        if (money > 0) {
            Economy economy = economyHolder.get();
            if (economy != null) {
                economy.depositPlayer(player, money);
                player.sendActionBar(messages.get("jobs.earned", Map.of(
                        "money", String.format("%.2f", money),
                        "job", messages.jobName(job.getId()))));
            } else if (!noEconomyWarned) {
                noEconomyWarned = true;
                plugin.getLogger().warning("No economy plugin found (Vault) - job levels/xp still work, but no money will be paid out.");
            }
        }

        if (baseXp > 0) {
            progress.setXp(progress.getXp() + baseXp);
            checkLevelUp(player, job, progress);
        }
        dirty = true;
    }

    private void checkLevelUp(Player player, JobDefinition job, PlayerJobProgress progress) {
        int maxLevel = jobManager.maxLevel();
        while (progress.getLevel() < maxLevel) {
            double required = xpToNextLevel(progress.getLevel());
            if (progress.getXp() < required) {
                break;
            }
            progress.setXp(progress.getXp() - required);
            progress.setLevel(progress.getLevel() + 1);
            player.sendMessage(messages.get("jobs.level-up", Map.of(
                    "job", messages.jobName(job.getId()),
                    "level", String.valueOf(progress.getLevel()))));
            if (progress.getLevel() >= maxLevel) {
                player.sendMessage(messages.get("jobs.max-level-reached", Map.of("job", messages.jobName(job.getId()))));
            }
        }
        if (progress.getLevel() >= maxLevel) {
            progress.setXp(0);
        }
    }

    public double xpToNextLevel(int level) {
        return jobManager.baseXpToLevel() * Math.pow(level, jobManager.growthExponent());
    }

    public List<TopEntry> top(String jobId, int limit) {
        List<TopEntry> entries = new ArrayList<>();
        for (PlayerJobData playerData : data.values()) {
            // Ranks by permanent progress, not current join state, so someone's best-ever level
            // still shows on the leaderboard even after they've since left the job.
            PlayerJobProgress progress = playerData.getProgress().get(jobId);
            if (progress != null) {
                entries.add(new TopEntry(playerData.getName(), progress.getLevel(), progress.getXp(), progress.getPrestige()));
            }
        }
        entries.sort(Comparator.comparingInt(TopEntry::prestige).reversed()
                .thenComparing(Comparator.comparingInt(TopEntry::level).reversed())
                .thenComparing(Comparator.comparingDouble(TopEntry::xp).reversed()));
        return entries.size() > limit ? entries.subList(0, limit) : entries;
    }

    private PlayerJobData dataFor(Player player) {
        PlayerJobData playerData = data.computeIfAbsent(player.getUniqueId(), k -> new PlayerJobData(player.getName()));
        playerData.setName(player.getName());
        return playerData;
    }

    private void load() {
        data.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection playersSection = yaml.getConfigurationSection("players");
        if (playersSection == null) {
            return;
        }
        for (String uuidString : playersSection.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidString);
            } catch (IllegalArgumentException e) {
                continue;
            }
            ConfigurationSection playerSection = playersSection.getConfigurationSection(uuidString);
            if (playerSection == null) {
                continue;
            }
            PlayerJobData playerData = new PlayerJobData(playerSection.getString("name", "?"));
            ConfigurationSection explorerSection = playerSection.getConfigurationSection("explorer-distance");
            if (explorerSection != null) {
                for (String worldName : explorerSection.getKeys(false)) {
                    playerData.setExplorerFarthestDistance(worldName, explorerSection.getDouble(worldName, 0));
                }
            }
            ConfigurationSection jobsSection = playerSection.getConfigurationSection("jobs");
            if (jobsSection != null) {
                for (String jobId : jobsSection.getKeys(false)) {
                    ConfigurationSection jobSection = jobsSection.getConfigurationSection(jobId);
                    if (jobSection == null) {
                        continue;
                    }
                    playerData.getProgress().put(jobId, new PlayerJobProgress(
                            jobSection.getInt("level", 1), jobSection.getDouble("xp", 0), jobSection.getInt("prestige", 0)));
                    if (jobSection.getBoolean("joined", true)) {
                        playerData.getJoined().add(jobId);
                    }
                }
            }
            data.put(uuid, playerData);
        }
    }

    public void save() {
        if (!dirty) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerJobData> entry : data.entrySet()) {
            String base = "players." + entry.getKey();
            PlayerJobData playerData = entry.getValue();
            yaml.set(base + ".name", playerData.getName());
            for (Map.Entry<String, Double> distanceEntry : playerData.getExplorerDistanceByWorld().entrySet()) {
                yaml.set(base + ".explorer-distance." + distanceEntry.getKey(), distanceEntry.getValue());
            }
            for (Map.Entry<String, PlayerJobProgress> jobEntry : playerData.getProgress().entrySet()) {
                String jobBase = base + ".jobs." + jobEntry.getKey();
                yaml.set(jobBase + ".level", jobEntry.getValue().getLevel());
                yaml.set(jobBase + ".xp", jobEntry.getValue().getXp());
                yaml.set(jobBase + ".prestige", jobEntry.getValue().getPrestige());
                yaml.set(jobBase + ".joined", playerData.getJoined().contains(jobEntry.getKey()));
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save player-jobs.yml: " + e.getMessage());
        }
    }
}
