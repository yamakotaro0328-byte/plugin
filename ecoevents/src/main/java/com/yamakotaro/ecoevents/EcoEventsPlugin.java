package com.yamakotaro.ecoevents;

import com.yamakotaro.ecoevents.commands.EventCommand;
import com.yamakotaro.ecoevents.event.EventManager;
import com.yamakotaro.ecoevents.scheduler.EventScheduler;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class EcoEventsPlugin extends JavaPlugin {

    private BukkitTask heartbeatTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Messages messages = new Messages(this);
        EventManager eventManager = new EventManager(this, messages);
        EventScheduler scheduler = new EventScheduler(this, eventManager);

        EventCommand eventCommand = new EventCommand(this, eventManager, messages);
        PluginCommand command = getCommand("event");
        command.setExecutor(eventCommand);
        command.setTabCompleter(eventCommand);

        this.heartbeatTask = getServer().getScheduler().runTaskTimer(this, scheduler::tick, 20L, 20L);
    }

    @Override
    public void onDisable() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
        }
    }
}
