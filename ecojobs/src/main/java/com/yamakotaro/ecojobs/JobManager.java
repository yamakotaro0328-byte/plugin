package com.yamakotaro.ecojobs;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
        ConfigurationSection jobsSection = plugin.config().getConfigurationSection("jobs");
        if (jobsSection != null) {
            for (String jobId : jobsSection.getKeys(false)) {
                ConfigurationSection jobSection = jobsSection.getConfigurationSection(jobId);
                if (jobSection == null) {
                    continue;
                }
                jobs.put(jobId, new JobDefinition(jobId, loadActions(jobSection), loadPerks(jobSection)));
            }
        }
        // Explorer pays out purely on distance milestones (see ExplorerListener), not through
        // the action-reward table, but still needs to exist as a joinable job. Its perks (if any)
        // live under the top-level "explorer" section instead, alongside its milestone settings.
        ConfigurationSection explorerSection = plugin.config().getConfigurationSection("explorer");
        jobs.put("explorer", new JobDefinition("explorer", Map.of(),
                explorerSection != null ? loadPerks(explorerSection) : List.of()));
    }

    /** Parses a job's (or explorer's) "perks" list - see {@link PerkDefinition} for what each field means. */
    private List<PerkDefinition> loadPerks(ConfigurationSection section) {
        List<PerkDefinition> perks = new ArrayList<>();
        for (Map<?, ?> raw : section.getMapList("perks")) {
            Object levelObj = raw.get("level");
            Object typeObj = raw.get("type");
            if (levelObj == null || typeObj == null) {
                continue;
            }
            int level = ((Number) levelObj).intValue();
            String type = String.valueOf(typeObj);
            double value = raw.get("value") instanceof Number number ? number.doubleValue() : 0;
            String effect = raw.get("effect") != null ? String.valueOf(raw.get("effect")) : null;
            perks.add(new PerkDefinition(level, type, value, effect));
        }
        return perks;
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
        return plugin.config().getInt("max-concurrent-jobs", 3);
    }

    public double baseXpToLevel() {
        return plugin.config().getDouble("leveling.base-xp-to-level", 50);
    }

    public double growthExponent() {
        return plugin.config().getDouble("leveling.growth-exponent", 1.35);
    }

    public double payBonusPerLevel() {
        return plugin.config().getDouble("leveling.pay-bonus-per-level", 0.005);
    }

    public int maxLevel() {
        return plugin.config().getInt("leveling.max-level", 100);
    }

    /**
     * Extra permanent pay-bonus multiplier per prestige (stacks additively with
     * payBonusPerLevel*level - see PlayerJobManager#applyReward).
     */
    public double prestigeBonusPerPrestige() {
        return plugin.config().getDouble("leveling.prestige-bonus-per-prestige", 0.02);
    }

    public double explorerDistancePerMilestone() {
        return plugin.config().getDouble("explorer.distance-per-milestone", 250);
    }

    public double explorerMoneyPerMilestone() {
        return plugin.config().getDouble("explorer.money-per-milestone", 100);
    }

    public double explorerXpPerMilestone() {
        return plugin.config().getDouble("explorer.xp-per-milestone", 60);
    }

    private static final List<Integer> DEFAULT_MILESTONE_LEVELS = List.of(10, 25, 50, 75, 100);

    /**
     * Levels that trigger a one-time server-wide bonus (see PlayerJobManager#awardMilestone),
     * applied to every job the same way.
     */
    public List<Integer> milestoneLevels() {
        List<Integer> configured = plugin.config().getIntegerList("leveling.milestone-levels");
        return configured.isEmpty() ? DEFAULT_MILESTONE_LEVELS : configured;
    }

    public double milestoneBonusMoney() {
        return plugin.config().getDouble("leveling.milestone-bonus-money", 100);
    }

    /**
     * 同じ相手を倒して戦士の報酬が再び出るまでの待ち時間(ミリ秒)。2人組で殺し合うだけの
     * 無限稼ぎを防ぐためのもので、0にすると制限なし。
     */
    public long playerKillCooldownMillis() {
        return Math.max(0, plugin.config().getLong("anti-farm.player-kill-cooldown-minutes", 30)) * 60_000L;
    }
}
