package com.yamakotaro.ecojobs.menu;

import com.yamakotaro.ecojobs.JobManager;
import com.yamakotaro.ecojobs.JobOverrides;
import com.yamakotaro.ecojobs.Messages;
import com.yamakotaro.ecojobs.PlayerJobManager;
import com.yamakotaro.ecojobs.PlayerJobProgress;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Map;

/**
 * Owns clicks on the player-facing jobs menu and everything reached from a job icon in it: the
 * per-job payout screen ({@link JobInfoMenuHolder}) and the prestige confirmation step
 * ({@link PrestigeConfirmMenuHolder}). Leaderboards opened from either screen are still handled by
 * {@link AdminMenuListener}, which owns every {@link LeaderboardMenuHolder} regardless of origin.
 */
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
        if (event.getInventory().getHolder() instanceof JobInfoMenuHolder infoHolder) {
            handleJobInfoClick(event, infoHolder);
        } else if (event.getInventory().getHolder() instanceof PrestigeConfirmMenuHolder confirmHolder) {
            handlePrestigeConfirmClick(event, confirmHolder);
        } else if (event.getInventory().getHolder() instanceof JobsMenuHolder holder) {
            handleJobsMenuClick(event, holder);
        }
    }

    private void handleJobsMenuClick(InventoryClickEvent event, JobsMenuHolder holder) {
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

        if (event.isShiftClick() && event.isRightClick()) {
            openJobInfo(player, jobId, 0);
            playSuccess(player);
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
        if (event.isRightClick()) {
            attemptPrestige(player, jobId, PrestigeConfirmMenuHolder.Origin.JOBS_MENU);
            return;
        }

        handleJoinLeave(player, jobId);
        holder.render(jobManager, playerJobManager, jobOverrides, player);
    }

    private void handleJobInfoClick(InventoryClickEvent event, JobInfoMenuHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getInventory()) {
            return;
        }
        int slot = event.getSlot();
        if (slot == JobInfoMenuHolder.CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == JobInfoMenuHolder.BACK_SLOT) {
            JobsMenuHolder jobsMenu = new JobsMenuHolder(messages);
            jobsMenu.render(jobManager, playerJobManager, jobOverrides, player);
            player.openInventory(jobsMenu.getInventory());
            playSuccess(player);
            return;
        }
        if (slot == JobInfoMenuHolder.PREV_PAGE_SLOT) {
            holder.render(jobManager, playerJobManager, jobOverrides, player, holder.getPage() - 1);
            playSuccess(player);
            return;
        }
        if (slot == JobInfoMenuHolder.NEXT_PAGE_SLOT) {
            holder.render(jobManager, playerJobManager, jobOverrides, player, holder.getPage() + 1);
            playSuccess(player);
            return;
        }
        if (slot != JobInfoMenuHolder.HEADER_SLOT) {
            return;
        }
        String jobId = holder.getJobId();
        if (event.isShiftClick()) {
            if (!player.hasPermission("ecojobs.top")) {
                player.sendMessage(messages.get("general.no-permission", Map.of()));
                playDenied(player);
                return;
            }
            // Simplification: returning from here goes to the jobs menu list, not back to this
            // exact detail screen - one extra shift-right-click gets back here if needed.
            LeaderboardMenuHolder leaderboard = new LeaderboardMenuHolder(messages, jobId, LeaderboardMenuHolder.Origin.JOBS_MENU);
            leaderboard.render(playerJobManager);
            player.openInventory(leaderboard.getInventory());
            playSuccess(player);
            return;
        }
        if (event.isRightClick()) {
            attemptPrestige(player, jobId, PrestigeConfirmMenuHolder.Origin.JOB_INFO);
            return;
        }
        handleJoinLeave(player, jobId);
        holder.render(jobManager, playerJobManager, jobOverrides, player, holder.getPage());
    }

    private void handlePrestigeConfirmClick(InventoryClickEvent event, PrestigeConfirmMenuHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getInventory()) {
            return;
        }
        int slot = event.getSlot();
        if (slot == PrestigeConfirmMenuHolder.CONFIRM_SLOT) {
            String jobId = holder.getJobId();
            switch (playerJobManager.prestige(player, jobId)) {
                case SUCCESS -> playSuccess(player); // the server-wide broadcast is sent inside prestige() itself
                case NOT_JOINED -> {
                    player.sendMessage(messages.get("jobs.not-joined", Map.of("job", messages.jobName(jobId))));
                    playDenied(player);
                }
                case NOT_MAX_LEVEL -> {
                    player.sendMessage(messages.get("jobs.prestige-not-max-level",
                            Map.of("job", messages.jobName(jobId), "max", String.valueOf(jobManager.maxLevel()))));
                    playDenied(player);
                }
                case UNKNOWN_JOB -> {
                    player.sendMessage(messages.get("jobs.unknown-job", Map.of("job", jobId)));
                    playDenied(player);
                }
            }
            returnFromPrestigeConfirm(player, holder);
        } else if (slot == PrestigeConfirmMenuHolder.CANCEL_SLOT) {
            returnFromPrestigeConfirm(player, holder);
        }
    }

    /** Opens the confirmation screen, but only once eligibility is actually verified. */
    private void attemptPrestige(Player player, String jobId, PrestigeConfirmMenuHolder.Origin origin) {
        PlayerJobProgress progress = playerJobManager.allProgress(player.getUniqueId()).get(jobId);
        boolean active = playerJobManager.isJoined(player.getUniqueId(), jobId);
        if (!active || progress == null || progress.getLevel() < jobManager.maxLevel()) {
            player.sendMessage(messages.get(active ? "jobs.prestige-not-max-level" : "jobs.not-joined",
                    Map.of("job", messages.jobName(jobId), "max", String.valueOf(jobManager.maxLevel()))));
            playDenied(player);
            return;
        }
        PrestigeConfirmMenuHolder confirm = new PrestigeConfirmMenuHolder(messages, jobId, origin);
        confirm.render();
        player.openInventory(confirm.getInventory());
        playSuccess(player);
    }

    private void returnFromPrestigeConfirm(Player player, PrestigeConfirmMenuHolder holder) {
        if (holder.getOrigin() == PrestigeConfirmMenuHolder.Origin.JOB_INFO) {
            openJobInfo(player, holder.getJobId(), 0);
        } else {
            JobsMenuHolder jobsMenu = new JobsMenuHolder(messages);
            jobsMenu.render(jobManager, playerJobManager, jobOverrides, player);
            player.openInventory(jobsMenu.getInventory());
        }
    }

    private void openJobInfo(Player player, String jobId, int page) {
        JobInfoMenuHolder info = new JobInfoMenuHolder(messages, jobId);
        info.render(jobManager, playerJobManager, jobOverrides, player, page);
        player.openInventory(info.getInventory());
    }

    /** Shared join/leave toggle used by both the jobs menu and the job-info header icon. */
    private void handleJoinLeave(Player player, String jobId) {
        if (playerJobManager.isJoined(player.getUniqueId(), jobId)) {
            playerJobManager.leave(player.getUniqueId(), jobId);
            player.sendMessage(messages.get("jobs.left", Map.of("job", messages.jobName(jobId))));
            playSuccess(player);
            return;
        }
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
                // ALREADY_JOINED/UNKNOWN_JOB can't happen here: both menus only ever offer real,
                // not-yet-joined jobs by construction.
            }
        }
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
