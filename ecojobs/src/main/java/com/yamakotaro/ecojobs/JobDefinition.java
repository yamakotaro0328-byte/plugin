package com.yamakotaro.ecojobs;

import java.util.List;
import java.util.Map;

/**
 * A configured job: its id (e.g. "miner"), per action type (e.g. "break-block") a map of
 * material/entity/item name (or "default") to its {@link ActionReward}, and its list of
 * level-gated {@link PerkDefinition}s (see {@link PerkManager}).
 */
public class JobDefinition {

    private final String id;
    private final Map<String, Map<String, ActionReward>> actionsByType;
    private final List<PerkDefinition> perks;

    public JobDefinition(String id, Map<String, Map<String, ActionReward>> actionsByType, List<PerkDefinition> perks) {
        this.id = id;
        this.actionsByType = actionsByType;
        this.perks = perks;
    }

    public String getId() {
        return id;
    }

    /**
     * @return the reward for this exact key under this action type, falling back to a
     * "default" entry if one exists, or null if this job doesn't pay out for this action at all.
     */
    public ActionReward getReward(String actionType, String key) {
        Map<String, ActionReward> rewards = actionsByType.get(actionType);
        if (rewards == null) {
            return null;
        }
        ActionReward reward = rewards.get(key.toUpperCase());
        if (reward != null) {
            return reward;
        }
        return rewards.get("DEFAULT");
    }

    /**
     * Read-only view of every action type this job pays out for, and each one's reward table -
     * used by /jobs info to show players a job's payouts without needing to open config.yml.
     */
    public Map<String, Map<String, ActionReward>> getActionsByType() {
        return actionsByType;
    }

    public List<PerkDefinition> getPerks() {
        return perks;
    }
}
