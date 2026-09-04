package com.yamakotaro.ecojobs.menu;

import com.yamakotaro.ecojobs.JobManager;
import com.yamakotaro.ecojobs.JobOverrides;
import com.yamakotaro.ecojobs.Messages;
import com.yamakotaro.ecojobs.PlayerJobManager;
import org.bukkit.Sound;
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
        if (slot == JobsMenuHolder.BACK_SLOT) {
            HubMenuHolder hub = new HubMenuHolder(messages);
            hub.render(player.hasPermission("ecojobs.admin"));
            player.openInventory(hub.getInventory());
            playSuccess(player);
            return;
        }
        String jobId = holder.jobIdAt(slot);
        if (jobId == null) {
            return;
        }

        if (event.isShiftClick()) {
            if (!player.hasPermission("ecojobs.top")) {
                player.sendMessage(messages.get("general.no-permission", Map.of()));
                playDenied(player);
                return;
            }
            LeaderboardMenuHolder leaderboard = new LeaderboardMenuHolder(messages, jobId, LeaderboardMenuHolder.Origin.JOBS_MENU);
            leaderboard.render(playerJobManager);
            player.openInventory(leaderboard.getInventory());
            playSuccess(player);
            return;
        }

        if (playerJobManager.isJoined(player.getUniqueId(), jobId)) {
            playerJobManager.leave(player.getUniqueId(), jobId);
            player.sendMessage(messages.get("jobs.left", Map.of("job", messages.jobName(jobId))));
            playSuccess(player);
        } else {
            switch (playerJobManager.join(player, jobId)) {
                case MAX_JOBS_REACHED -> {
                    player.sendMessage(messages.get("jobs.max-jobs-reached",
                            Map.of("max", String.valueOf(jobManager.maxConcurrentJobs()))));
                    playDenied(player);
                }
                case JOB_DISABLED -> {
                    player.sendMessage(messages.get("jobs.job-disabled", Map.of("job", messages.jobName(jobId))));
                    playDenied(player);
                }
                case SUCCESS -> {
                    player.sendMessage(messages.get("jobs.joined", Map.of("job", messages.jobName(jobId))));
                    playSuccess(player);
                }
                default -> {
                    // ALREADY_JOINED/UNKNOWN_JOB can't happen here: the menu only lists real,
                    // not-yet-joined jobs by construction.
                }
            }
        }
        holder.render(jobManager, playerJobManager, jobOverrides, player);
    }

    private void playSuccess(Player player) {
        if (playerJobManager.isSoundEnabled(player)) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
        }
    }

    private void playDenied(Player player) {
        if (playerJobManager.isSoundEnabled(player)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
        }
    }
}
