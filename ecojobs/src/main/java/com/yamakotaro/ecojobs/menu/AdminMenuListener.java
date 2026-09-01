package com.yamakotaro.ecojobs.menu;

import com.yamakotaro.ecojobs.BoosterManager;
import com.yamakotaro.ecojobs.JobManager;
import com.yamakotaro.ecojobs.JobOverrides;
import com.yamakotaro.ecojobs.Messages;
import com.yamakotaro.ecojobs.PlayerJobManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class AdminMenuListener implements Listener {

    private final JobManager jobManager;
    private final PlayerJobManager playerJobManager;
    private final JobOverrides jobOverrides;
    private final BoosterManager boosterManager;
    private final Messages messages;

    public AdminMenuListener(JobManager jobManager, PlayerJobManager playerJobManager, JobOverrides jobOverrides,
                              BoosterManager boosterManager, Messages messages) {
        this.jobManager = jobManager;
        this.playerJobManager = playerJobManager;
        this.jobOverrides = jobOverrides;
        this.boosterManager = boosterManager;
        this.messages = messages;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof LeaderboardMenuHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getInventory() && event.getSlot() == LeaderboardMenuHolder.CLOSE_SLOT
                    && event.getWhoClicked() instanceof Player player) {
                player.closeInventory();
            }
            return;
        }

        if (!(event.getInventory().getHolder() instanceof AdminMenuHolder holder)) {
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
        if (slot == AdminMenuHolder.CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == AdminMenuHolder.BOOSTER_START_SLOT) {
            long durationMillis = TimeUnit.MINUTES.toMillis(AdminMenuHolder.QUICK_BOOSTER_MINUTES);
            boosterManager.start(BoosterManager.GLOBAL_SCOPE, AdminMenuHolder.QUICK_BOOSTER_MULTIPLIER,
                    AdminMenuHolder.QUICK_BOOSTER_MULTIPLIER, durationMillis, player.getName());
            Bukkit.getServer().sendMessage(messages.get("jobs.booster-started", Map.of(
                    "scope", messages.raw("jobs.booster-scope-all", Map.of()),
                    "money", String.format("%.2f", AdminMenuHolder.QUICK_BOOSTER_MULTIPLIER),
                    "xp", String.format("%.2f", AdminMenuHolder.QUICK_BOOSTER_MULTIPLIER),
                    "minutes", String.valueOf(AdminMenuHolder.QUICK_BOOSTER_MINUTES),
                    "player", player.getName())));
            playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1.2f);
            holder.render(jobManager, jobOverrides, boosterManager);
            return;
        }
        if (slot == AdminMenuHolder.BOOSTER_STOP_SLOT) {
            int stopped = boosterManager.stopAll();
            if (stopped > 0) {
                Bukkit.getServer().sendMessage(messages.get("jobs.booster-stopped-all", Map.of("player", player.getName())));
            }
            playSound(player, Sound.UI_BUTTON_CLICK, 0.8f);
            holder.render(jobManager, jobOverrides, boosterManager);
            return;
        }

        String jobId = holder.jobIdAt(slot);
        if (jobId == null) {
            return;
        }
        if (event.isShiftClick() && event.isLeftClick()) {
            player.closeInventory();
            LeaderboardMenuHolder leaderboard = new LeaderboardMenuHolder(messages, jobId);
            leaderboard.render(playerJobManager);
            player.openInventory(leaderboard.getInventory());
            playSound(player, Sound.UI_BUTTON_CLICK, 1.4f);
            return;
        }
        if (event.isShiftClick() && event.isRightClick()) {
            jobOverrides.adjustPayMultiplier(jobId, -AdminMenuHolder.MULTIPLIER_STEP);
        } else if (event.isRightClick()) {
            jobOverrides.adjustPayMultiplier(jobId, AdminMenuHolder.MULTIPLIER_STEP);
        } else {
            jobOverrides.setEnabled(jobId, !jobOverrides.isEnabled(jobId));
        }
        playSound(player, Sound.UI_BUTTON_CLICK, 1.4f);
        holder.render(jobManager, jobOverrides, boosterManager);
    }

    private void playSound(Player player, Sound sound, float pitch) {
        if (playerJobManager.isSoundEnabled(player)) {
            player.playSound(player.getLocation(), sound, 0.6f, pitch);
        }
    }
}
