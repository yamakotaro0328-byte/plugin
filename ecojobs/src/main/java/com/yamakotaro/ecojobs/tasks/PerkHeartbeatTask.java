package com.yamakotaro.ecojobs.tasks;

import com.yamakotaro.ecojobs.JobDefinition;
import com.yamakotaro.ecojobs.JobManager;
import com.yamakotaro.ecojobs.PerkManager;
import com.yamakotaro.ecojobs.PlayerJobManager;
import com.yamakotaro.ecojobs.PlayerJobProgress;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Every ~5 seconds, re-applies each online player's unlocked "potion" perks (see PerkManager) for
 * every job they're actively in - the same short-effect-kept-alive-by-refresh approach as
 * EcoCosmetics' ParticleTrailTask, so a player who leaves the job or logs off needs no separate
 * cleanup: the refreshes simply stop and the last application's own (slightly longer) duration
 * expires on its own.
 */
public class PerkHeartbeatTask implements Runnable {

    /** Longer than the run interval so a slightly-late tick never causes a visible flicker/drop. */
    private static final int EFFECT_DURATION_TICKS = 20 * 8;

    private final JobManager jobManager;
    private final PlayerJobManager playerJobManager;
    private final PerkManager perkManager;

    public PerkHeartbeatTask(JobManager jobManager, PlayerJobManager playerJobManager, PerkManager perkManager) {
        this.jobManager = jobManager;
        this.playerJobManager = playerJobManager;
        this.perkManager = perkManager;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (Map.Entry<String, PlayerJobProgress> entry : playerJobManager.joinedJobs(player.getUniqueId()).entrySet()) {
                JobDefinition job = jobManager.get(entry.getKey());
                if (job == null) {
                    continue;
                }
                int effectiveLevel = perkManager.effectiveLevel(entry.getValue());
                perkManager.applyPotionPerks(player, job, effectiveLevel, EFFECT_DURATION_TICKS);
            }
        }
    }
}
