package com.yamakotaro.ecoboss;

import com.yamakotaro.ecoboss.boss.BossManager;
import com.yamakotaro.ecoboss.commands.BossCommand;
import com.yamakotaro.ecoboss.listeners.BossDamageListener;
import com.yamakotaro.ecoboss.listeners.BossDeathListener;
import com.yamakotaro.ecoboss.listeners.DungeonTriggerListener;
import com.yamakotaro.ecoboss.listeners.SelectionListener;
import com.yamakotaro.ecoboss.location.BossLocationManager;
import com.yamakotaro.ecoboss.scheduler.EventBossScheduler;
import com.yamakotaro.ecoboss.scheduler.WorldBossScheduler;
import com.yamakotaro.ecoboss.selection.SelectionManager;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class EcoBossPlugin extends JavaPlugin {

    private BukkitTask heartbeatTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Messages messages = new Messages(this);
        BossLocationManager locationManager = new BossLocationManager(this);
        BossManager bossManager = new BossManager(this, messages, locationManager);
        SelectionManager selectionManager = new SelectionManager();
        NamespacedKey wandKey = new NamespacedKey(this, "wand");

        WorldBossScheduler worldBossScheduler = new WorldBossScheduler(this, bossManager, messages);
        EventBossScheduler eventBossScheduler = new EventBossScheduler(this, bossManager, messages);

        getServer().getPluginManager().registerEvents(new SelectionListener(this, selectionManager, messages), this);
        getServer().getPluginManager().registerEvents(new DungeonTriggerListener(bossManager, messages), this);
        getServer().getPluginManager().registerEvents(new BossDamageListener(bossManager), this);
        getServer().getPluginManager().registerEvents(new BossDeathListener(bossManager), this);

        BossCommand bossCommand = new BossCommand(this, bossManager, selectionManager, wandKey, messages);
        PluginCommand command = getCommand("boss");
        command.setExecutor(bossCommand);
        command.setTabCompleter(bossCommand);

        this.heartbeatTask = getServer().getScheduler().runTaskTimer(this, () -> {
            worldBossScheduler.tick();
            eventBossScheduler.tick();
            bossManager.syncBossBars();
            bossManager.tickAuras();
            bossManager.tickAbilities();
        }, 20L, 20L);
    }

    @Override
    public void onDisable() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
        }
    }
}
