package com.yamakotaro.ecojobs;

import com.yamakotaro.ecojobs.commands.JobsCommand;
import com.yamakotaro.ecojobs.listeners.BlockJobListener;
import com.yamakotaro.ecojobs.listeners.CraftingJobListener;
import com.yamakotaro.ecojobs.listeners.EntityJobListener;
import com.yamakotaro.ecojobs.listeners.EvenMoreFishBridge;
import com.yamakotaro.ecojobs.listeners.ExplorerListener;
import com.yamakotaro.ecojobs.listeners.TradeJobListener;
import com.yamakotaro.ecojobs.menu.AdminMenuListener;
import com.yamakotaro.ecojobs.menu.HubMenuListener;
import com.yamakotaro.ecojobs.menu.JobsMenuListener;
import com.yamakotaro.ecojobs.storage.JobStorage;
import com.yamakotaro.ecojobs.storage.MySqlJobStorage;
import com.yamakotaro.ecojobs.storage.YamlJobStorage;
import com.yamakotaro.ecojobs.tasks.PerkHeartbeatTask;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * Standalone, EcoTP-independent plugin: 20 jobs that pay players (via Vault, if present) for
 * everyday survival actions, each with its own level that raises the payout over time. See
 * config.yml for the full list of jobs and their per-action rewards.
 */
public class EcoJobsPlugin extends JavaPlugin {

    private static final long SAVE_INTERVAL_TICKS = 20L * 60 * 5; // every 5 minutes
    private static final long PLACED_BLOCK_CLEAR_INTERVAL_TICKS = 20L * 60 * 30; // every 30 minutes
    private static final long PERK_HEARTBEAT_INTERVAL_TICKS = 20L * 5; // every 5 seconds
    private static final long ACTION_BAR_FLUSH_INTERVAL_TICKS = 5L; // every 0.25 seconds

    private PlayerJobManager playerJobManager;
    private YamlConfiguration config;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadPluginConfig();
        Messages messages = new Messages(this);
        JobManager jobManager = new JobManager(this);
        EconomyHolder economyHolder = new EconomyHolder(this);
        economyHolder.setup();
        JobOverrides jobOverrides = new JobOverrides(this);
        BoosterManager boosterManager = new BoosterManager();
        PerkManager perkManager = new PerkManager(this, jobManager);
        JobStorage storage = "mysql".equalsIgnoreCase(config().getString("storage.type", "yaml"))
                ? new MySqlJobStorage(this) : new YamlJobStorage(this);
        this.playerJobManager = new PlayerJobManager(this, jobManager, economyHolder, messages, storage, jobOverrides, boosterManager, perkManager);
        PlacedBlockTracker placedBlockTracker = new PlacedBlockTracker();

        // EvenMoreFish が入っていればカスタム魚をレア度別に支払う。未導入なら register() は何もせず、
        // EntityJobListener 側の問い合わせも常に false を返すだけなので、通常の釣り報酬に影響しない。
        EvenMoreFishBridge evenMoreFish = new EvenMoreFishBridge(this, playerJobManager);
        evenMoreFish.register();

        getServer().getPluginManager().registerEvents(new BlockJobListener(playerJobManager, placedBlockTracker, jobManager, perkManager), this);
        getServer().getPluginManager().registerEvents(new EntityJobListener(playerJobManager, jobManager, evenMoreFish), this);
        getServer().getPluginManager().registerEvents(new CraftingJobListener(playerJobManager), this);
        getServer().getPluginManager().registerEvents(new TradeJobListener(playerJobManager), this);
        getServer().getPluginManager().registerEvents(new ExplorerListener(playerJobManager), this);
        getServer().getPluginManager().registerEvents(
                new JobsMenuListener(jobManager, playerJobManager, jobOverrides, boosterManager, perkManager, messages), this);
        getServer().getPluginManager().registerEvents(
                new AdminMenuListener(jobManager, playerJobManager, jobOverrides, boosterManager, messages), this);
        getServer().getPluginManager().registerEvents(
                new HubMenuListener(jobManager, playerJobManager, jobOverrides, boosterManager, messages), this);

        JobsCommand jobsCommand = new JobsCommand(this, jobManager, playerJobManager, jobOverrides, boosterManager, messages);
        getCommand("jobs").setExecutor(jobsCommand);
        getCommand("jobs").setTabCompleter(jobsCommand);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new EcoJobsPlaceholders(this, playerJobManager).register();
        }

        // Actions fire far more often than the rare admin edits other EcoTP-family plugins save
        // on, so this batches saves on a timer instead of writing to disk on every single action.
        getServer().getScheduler().runTaskTimer(this, playerJobManager::save, SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS);
        // Bounds the placed-block tracker's memory growth from blocks removed by anything other
        // than a player break (explosions, pistons, liquid flow, ...), which never clear their
        // entry otherwise - see PlacedBlockTracker#clear.
        getServer().getScheduler().runTaskTimer(this, placedBlockTracker::clear,
                PLACED_BLOCK_CLEAR_INTERVAL_TICKS, PLACED_BLOCK_CLEAR_INTERVAL_TICKS);
        // Keeps "potion" perks (see PerkManager) topped up for every online player in every job
        // they're actively in - see PerkHeartbeatTask for why a periodic refresh needs no
        // separate cleanup on leave/logout.
        getServer().getScheduler().runTaskTimer(this,
                new PerkHeartbeatTask(jobManager, playerJobManager, perkManager), PERK_HEARTBEAT_INTERVAL_TICKS, PERK_HEARTBEAT_INTERVAL_TICKS);
        // Batches the "you earned X" action bar instead of sending it on every single reward -
        // see PlayerJobManager#flushEarnedActionBars.
        getServer().getScheduler().runTaskTimer(this, playerJobManager::flushEarnedActionBars,
                ACTION_BAR_FLUSH_INTERVAL_TICKS, ACTION_BAR_FLUSH_INTERVAL_TICKS);
    }

    @Override
    public void onDisable() {
        if (playerJobManager != null) {
            playerJobManager.close();
        }
    }

    /** config.yml をこのプラグイン自身のUTF-8ローダーで再読み込みする (BukkitのreloadConfig()は使わない)。 */
    public void reloadPluginConfig() {
        this.config = YamlIo.load(new File(getDataFolder(), "config.yml"));
    }

    /** Bukkit標準の getConfig() の代わりに使う、UTF-8で読み込んだ設定。 */
    public YamlConfiguration config() {
        return config;
    }
}
