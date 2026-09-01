package com.yamakotaro.ecojobs.menu;

import com.yamakotaro.ecojobs.BoosterManager;
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

public class HubMenuListener implements Listener {

    private final JobManager jobManager;
    private final PlayerJobManager playerJobManager;
    private final JobOverrides jobOverrides;
    private final BoosterManager boosterManager;
    private final Messages messages;

    public HubMenuListener(JobManager jobManager, PlayerJobManager playerJobManager, JobOverrides jobOverrides,
                            BoosterManager boosterManager, Messages messages) {
        this.jobManager = jobManager;
        this.playerJobManager = playerJobManager;
        this.jobOverrides = jobOverrides;
        this.boosterManager = boosterManager;
        this.messages = messages;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof HubMenuHolder) {
            handleHubClick(event);
        } else if (event.getInventory().getHolder() instanceof SettingsMenuHolder) {
            handleSettingsClick(event);
        } else if (event.getInventory().getHolder() instanceof LeaderboardPickerMenuHolder holder) {
            handlePickerClick(event, holder);
        }
    }

    private void handleHubClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getInventory()) {
            return;
        }
        int slot = event.getSlot();
        if (slot == HubMenuHolder.CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == HubMenuHolder.MY_JOBS_SLOT) {
            player.closeInventory();
            JobsMenuHolder holder = new JobsMenuHolder(messages);
            holder.render(jobManager, playerJobManager, jobOverrides, player);
            player.openInventory(holder.getInventory());
            playClick(player);
        } else if (slot == HubMenuHolder.LEADERBOARDS_SLOT) {
            if (!player.hasPermission("ecojobs.top")) {
                player.sendMessage(messages.get("general.no-permission", Map.of()));
                return;
            }
            player.closeInventory();
            LeaderboardPickerMenuHolder holder = new LeaderboardPickerMenuHolder(messages);
            holder.render(jobManager);
            player.openInventory(holder.getInventory());
            playClick(player);
        } else if (slot == HubMenuHolder.SETTINGS_SLOT) {
            player.closeInventory();
            SettingsMenuHolder holder = new SettingsMenuHolder(messages);
            holder.render(playerJobManager, player);
            player.openInventory(holder.getInventory());
            playClick(player);
        } else if (slot == HubMenuHolder.ADMIN_SLOT) {
            if (!player.hasPermission("ecojobs.admin")) {
                player.sendMessage(messages.get("general.no-permission", Map.of()));
                return;
            }
            player.closeInventory();
            AdminMenuHolder holder = new AdminMenuHolder(messages);
            holder.render(jobManager, jobOverrides, boosterManager);
            player.openInventory(holder.getInventory());
            playClick(player);
        }
    }

    private void handleSettingsClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getInventory()) {
            return;
        }
        int slot = event.getSlot();
        if (slot == SettingsMenuHolder.CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == SettingsMenuHolder.SOUND_SLOT) {
            playerJobManager.toggleSoundEnabled(player);
        } else if (slot == SettingsMenuHolder.ACTIONBAR_SLOT) {
            playerJobManager.toggleActionBarEnabled(player);
        } else {
            return;
        }
        playClick(player);
        ((SettingsMenuHolder) event.getInventory().getHolder()).render(playerJobManager, player);
    }

    private void handlePickerClick(InventoryClickEvent event, LeaderboardPickerMenuHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getInventory()) {
            return;
        }
        if (event.getSlot() == LeaderboardPickerMenuHolder.CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        String jobId = holder.jobIdAt(event.getSlot());
        if (jobId == null) {
            return;
        }
        player.closeInventory();
        LeaderboardMenuHolder leaderboard = new LeaderboardMenuHolder(messages, jobId);
        leaderboard.render(playerJobManager);
        player.openInventory(leaderboard.getInventory());
        playClick(player);
    }

    private void playClick(Player player) {
        if (playerJobManager.isSoundEnabled(player)) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        }
    }
}
