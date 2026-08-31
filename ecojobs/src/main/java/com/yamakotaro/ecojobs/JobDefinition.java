package com.yamakotaro.ecojobs;

import java.util.Map;

/**
 * A configured job: its id (e.g. "miner") and, per action type (e.g. "break-block"), a map of
 * material/entity/item name (or "default") to its {@link ActionReward}.
 */
public class JobDefinition {

    private final String id;
    private final Map<String, Map<String, ActionReward>> actionsByType;

    public JobDefinition(String id, Map<String, Map<String, ActionReward>> actionsByType) {
        this.id = id;
        this.actionsByType = actionsByType;
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
}
