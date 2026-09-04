package com.yamakotaro.ecojobs;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Admin-editable per-job settings (enabled, pay multiplier) set live from /jobs admin. Kept in
 * its own job-overrides.yml, separate from config.yml, so an admin's live tuning survives a
 * config.yml reload/update instead of being overwritten by it.
 */
public class JobOverrides {

    private final EcoJobsPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;

    public JobOverrides(EcoJobsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "job-overrides.yml");
        this.yaml = YamlIo.load(file);
    }

    public boolean isEnabled(String jobId) {
        return yaml.getBoolean(jobId + ".enabled", true);
    }

    public void setEnabled(String jobId, boolean enabled) {
        yaml.set(jobId + ".enabled", enabled);
        save();
    }

    /**
     * Extra multiplier on top of the level/prestige pay bonus (see PlayerJobManager#applyReward),
     * for admins to tune a job's overall payout without editing config.yml. 1.0 = unchanged.
     */
    public double payMultiplier(String jobId) {
        return yaml.getDouble(jobId + ".pay-multiplier", 1.0);
    }

    public void adjustPayMultiplier(String jobId, double delta) {
        double next = Math.max(0, payMultiplier(jobId) + delta);
        yaml.set(jobId + ".pay-multiplier", Math.round(next * 100.0) / 100.0);
        save();
    }

    private void save() {
        try {
            YamlIo.save(yaml, file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save job-overrides.yml", e);
        }
    }
}
