package com.yamakotaro.ecojobs;

import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads job definitions from config.yml's "jobs" section (plus the special "explorer" job, which
 * has no action list of its own - see config.yml's separate "explorer" section).
 */
public class JobManager {

    private final EcoJobsPlugin plugin;
    private final Map<String, JobDefinition> jobs = new LinkedHashMap<>();

    public JobManager(EcoJobsPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        jobs.clear();
        ConfigurationSection jobsSection = plugin.getConfig().getConfigurationSection("jobs");
        if (jobsSection != null) {
            for (String jobId : jobsSection.getKeys(false)) {
                ConfigurationSection jobSection = jobsSection.getConfigurationSection(jobId);
                if (jobSection == null) {
                    continue;
                }
                jobs.put(jobId, new JobDefinition(jobId, loadActions(jobSection)));
            }
        }
        // Explorer pays out purely on distance milestones (see ExplorerListener), not through
        // the action-reward table, but still needs to exist as a joinable job.
        jobs.put("explorer", new JobDefinition("explorer", Map.of()));
    }

    private Map<String, Map<String, ActionReward>> loadActions(ConfigurationSection jobSection) {
        Map<String, Map<String, ActionReward>> actionsByType = new HashMap<>();
        ConfigurationSection actionsSection = jobSection.getConfigurationSection("actions");
        if (actionsSection == null) {
            return actionsByType;
        }
        for (String actionType : actionsSection.getKeys(false)) {
            ConfigurationSection entriesSection = actionsSection.getConfigurationSection(actionType);
            if (entriesSection == null) {
                continue;
            }
            Map<String, ActionReward> rewards = new HashMap<>();
            for (String key : entriesSection.getKeys(false)) {
                ConfigurationSection entry = entriesSection.getConfigurationSection(key);
                if (entry == null) {
                    continue;
                }
                rewards.put(key.toUpperCase(), new ActionReward(
                        entry.getDouble("money", 0),
                        entry.getDouble("xp", 0),
                        entry.getDouble("money-per-level", 0),
                        entry.getDouble("xp-per-level", 0)));
            }
            actionsByType.put(actionType, rewards);
        }
        return actionsByType;
    }

    public JobDefinition get(String jobId) {
        return jobs.get(jobId.toLowerCase());
    }

    public Map<String, JobDefinition> all() {
        return jobs;
    }

    public int maxConcurrentJobs() {
        return plugin.getConfig().getInt("max-concurrent-jobs", 3);
    }

    public double baseXpToLevel() {
        return plugin.getConfig().getDouble("leveling.base-xp-to-level", 50);
    }

    public double growthExponent() {
        return plugin.getConfig().getDouble("leveling.growth-exponent", 1.35);
    }

    public double payBonusPerLevel() {
        return plugin.getConfig().getDouble("leveling.pay-bonus-per-level", 0.005);
    }

    public int maxLevel() {
        return plugin.getConfig().getInt("leveling.max-level", 100);
    }

    public double explorerDistancePerMilestone() {
        return plugin.getConfig().getDouble("explorer.distance-per-milestone", 250);
    }

    public double explorerMoneyPerMilestone() {
        return plugin.getConfig().getDouble("explorer.money-per-milestone", 100);
    }

    public double explorerXpPerMilestone() {
        return plugin.getConfig().getDouble("explorer.xp-per-milestone", 60);
    }
}
