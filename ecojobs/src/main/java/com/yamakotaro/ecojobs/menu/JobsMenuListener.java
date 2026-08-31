package com.yamakotaro.ecojobs.menu;

import com.yamakotaro.ecojobs.JobManager;
import com.yamakotaro.ecojobs.JobOverrides;
import com.yamakotaro.ecojobs.Messages;
import com.yamakotaro.ecojobs.PlayerJobManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Map;

public class JobsMenuListener implements Listener {

    private final JobManager jobManager;
    private final PlayerJobManager playerJobManager;
    private final JobOverrides jobOverrides;
    private final Messages messages;

    public JobsMenuListener(JobManager jobManager, PlayerJobManager playerJobManager, JobOverrides jobOverrides, Messages messages) {
        this.jobManager = jobManager;
        this.playerJobManager = playerJobManager;
        this.jobOverrides = jobOverrides;
        this.messages = messages;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof JobsMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() != event.getInventory()) {
            return;
        }
        int slot = event.getSlot();
        if (slot == JobsMenuHolder.CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        String jobId = holder.jobIdAt(slot);
        if (jobId == null) {
            return;
        }
        if (playerJobManager.isJoined(player.getUniqueId(), jobId)) {
            playerJobManager.leave(player.getUniqueId(), jobId);
            player.sendMessage(messages.get("jobs.left", Map.of("job", messages.jobName(jobId))));
        } else {
            switch (playerJobManager.join(player, jobId)) {
                case MAX_JOBS_REACHED -> player.sendMessage(messages.get("jobs.max-jobs-reached",
                        Map.of("max", String.valueOf(jobManager.maxConcurrentJobs()))));
                case JOB_DISABLED -> player.sendMessage(messages.get("jobs.job-disabled", Map.of("job", messages.jobName(jobId))));
                case SUCCESS -> player.sendMessage(messages.get("jobs.joined", Map.of("job", messages.jobName(jobId))));
                default -> {
                    // ALREADY_JOINED/UNKNOWN_JOB can't happen here: the menu only lists real,
                    // not-yet-joined jobs by construction.
                }
            }
        }
        holder.render(jobManager, playerJobManager, jobOverrides, player.getUniqueId());
    }
}
