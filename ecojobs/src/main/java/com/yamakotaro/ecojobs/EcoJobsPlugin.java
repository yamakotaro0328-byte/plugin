package com.yamakotaro.ecojobs;

import com.yamakotaro.ecojobs.commands.JobsCommand;
import com.yamakotaro.ecojobs.listeners.BlockJobListener;
import com.yamakotaro.ecojobs.listeners.CraftingJobListener;
import com.yamakotaro.ecojobs.listeners.EntityJobListener;
import com.yamakotaro.ecojobs.listeners.ExplorerListener;
import com.yamakotaro.ecojobs.listeners.TradeJobListener;
import com.yamakotaro.ecojobs.menu.JobsMenuListener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Standalone, EcoTP-independent plugin: 20 jobs that pay players (via Vault, if present) for
 * everyday survival actions, each with its own level that raises the payout over time. See
 * config.yml for the full list of jobs and their per-action rewards.
 */
public class EcoJobsPlugin extends JavaPlugin {

    private static final long SAVE_INTERVAL_TICKS = 20L * 60 * 5; // every 5 minutes

    private PlayerJobManager playerJobManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Messages messages = new Messages(this);
        JobManager jobManager = new JobManager(this);
        EconomyHolder economyHolder = new EconomyHolder(this);
        economyHolder.setup();
        this.playerJobManager = new PlayerJobManager(this, jobManager, economyHolder, messages);
        PlacedBlockTracker placedBlockTracker = new PlacedBlockTracker();

        getServer().getPluginManager().registerEvents(new BlockJobListener(playerJobManager, placedBlockTracker), this);
        getServer().getPluginManager().registerEvents(new EntityJobListener(playerJobManager), this);
        getServer().getPluginManager().registerEvents(new CraftingJobListener(playerJobManager), this);
        getServer().getPluginManager().registerEvents(new TradeJobListener(playerJobManager), this);
        getServer().getPluginManager().registerEvents(new ExplorerListener(playerJobManager), this);
        getServer().getPluginManager().registerEvents(new JobsMenuListener(jobManager, playerJobManager, messages), this);

        JobsCommand jobsCommand = new JobsCommand(this, jobManager, playerJobManager, messages);
        getCommand("jobs").setExecutor(jobsCommand);
        getCommand("jobs").setTabCompleter(jobsCommand);

        // Actions fire far more often than the rare admin edits other EcoTP-family plugins save
        // on, so this batches saves on a timer instead of writing to disk on every single action.
        getServer().getScheduler().runTaskTimer(this, playerJobManager::save, SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS);
    }

    @Override
    public void onDisable() {
        if (playerJobManager != null) {
            playerJobManager.save();
        }
    }
}
