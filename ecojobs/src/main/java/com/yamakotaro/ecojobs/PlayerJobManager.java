package com.yamakotaro.ecojobs;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks every player's joined jobs, levels, and xp; applies action rewards (with the per-level
 * pay bonus and level-up handling); and persists it all to player-jobs.yml. Actions fire far more
 * often than admin edits in the other plugins' managers, so this saves on a timer (see
 * {@link EcoJobsPlugin}) rather than synchronously on every single change.
 */
public class PlayerJobManager {

    public enum JoinResult { SUCCESS, UNKNOWN_JOB, ALREADY_JOINED, MAX_JOBS_REACHED }

    public enum LeaveResult { SUCCESS, UNKNOWN_JOB, NOT_JOINED }

    public record TopEntry(String name, int level, double xp) {
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
        return playerData != null && playerData.getJobs().containsKey(jobId);
    }

    public Map<String, PlayerJobProgress> joinedJobs(UUID uuid) {
        PlayerJobData playerData = data.get(uuid);
        return playerData != null ? playerData.getJobs() : Map.of();
    }

    public JoinResult join(Player player, String jobId) {
        JobDefinition job = jobManager.get(jobId);
        if (job == null) {
            return JoinResult.UNKNOWN_JOB;
        }
        PlayerJobData playerData = dataFor(player);
        if (playerData.getJobs().containsKey(job.getId())) {
            return JoinResult.ALREADY_JOINED;
        }
        if (playerData.getJobs().size() >= jobManager.maxConcurrentJobs()) {
            return JoinResult.MAX_JOBS_REACHED;
        }
        playerData.getJobs().put(job.getId(), new PlayerJobProgress(1, 0));
        dirty = true;
        return JoinResult.SUCCESS;
    }

    public LeaveResult leave(UUID uuid, String jobId) {
        JobDefinition job = jobManager.get(jobId);
        if (job == null) {
            return LeaveResult.UNKNOWN_JOB;
        }
        PlayerJobData playerData = data.get(uuid);
        if (playerData == null || playerData.getJobs().remove(job.getId()) == null) {
            return LeaveResult.NOT_JOINED;
        }
        dirty = true;
        return LeaveResult.SUCCESS;
    }

    /**
     * Applies one action's reward for the given job to the player, if (and only if) they've
     * joined that job: deposits money (scaled by level bonus, via Vault if available), adds xp,
     * handles any level-ups, and shows an action-bar confirmation. A no-op if the player hasn't
     * joined this job, or if the job has no reward configured for this action at all.
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
     * Called by ExplorerListener whenever a player's farthest-from-spawn distance crosses one or
     * more new milestones; pays out once per milestone crossed (usually just one).
     */
    public void checkExplorerMilestones(Player player, double currentDistance) {
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
        double previousMilestones = Math.floor(playerData.getExplorerFarthestDistance() / perMilestone);
        double currentMilestones = Math.floor(currentDistance / perMilestone);
        if (currentDistance > playerData.getExplorerFarthestDistance()) {
            playerData.setExplorerFarthestDistance(currentDistance);
            dirty = true;
        }
        int crossed = (int) (currentMilestones - previousMilestones);
        for (int i = 0; i < crossed; i++) {
            applyReward(player, explorer, progress,
                    jobManager.explorerMoneyPerMilestone(), jobManager.explorerXpPerMilestone());
        }
    }

    private void applyReward(Player player, JobDefinition job, PlayerJobProgress progress, double baseMoney, double baseXp) {
        double levelMultiplier = 1 + progress.getLevel() * jobManager.payBonusPerLevel();
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
            PlayerJobProgress progress = playerData.getJobs().get(jobId);
            if (progress != null) {
                entries.add(new TopEntry(playerData.getName(), progress.getLevel(), progress.getXp()));
            }
        }
        entries.sort(Comparator.comparingInt(TopEntry::level).reversed()
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
            playerData.setExplorerFarthestDistance(playerSection.getDouble("explorer-distance", 0));
            ConfigurationSection jobsSection = playerSection.getConfigurationSection("jobs");
            if (jobsSection != null) {
                for (String jobId : jobsSection.getKeys(false)) {
                    ConfigurationSection jobSection = jobsSection.getConfigurationSection(jobId);
                    if (jobSection == null) {
                        continue;
                    }
                    playerData.getJobs().put(jobId, new PlayerJobProgress(
                            jobSection.getInt("level", 1), jobSection.getDouble("xp", 0)));
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
            yaml.set(base + ".explorer-distance", playerData.getExplorerFarthestDistance());
            for (Map.Entry<String, PlayerJobProgress> jobEntry : playerData.getJobs().entrySet()) {
                String jobBase = base + ".jobs." + jobEntry.getKey();
                yaml.set(jobBase + ".level", jobEntry.getValue().getLevel());
                yaml.set(jobBase + ".xp", jobEntry.getValue().getXp());
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
