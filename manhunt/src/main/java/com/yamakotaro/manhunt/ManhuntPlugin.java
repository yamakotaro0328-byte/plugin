package com.yamakotaro.manhunt;

import com.yamakotaro.manhunt.commands.ManhuntCommand;
import com.yamakotaro.manhunt.game.GameManager;
import com.yamakotaro.manhunt.gui.StatusGuiBuilder;
import com.yamakotaro.manhunt.items.SpecialItems;
import com.yamakotaro.manhunt.listeners.DragonDeathListener;
import com.yamakotaro.manhunt.listeners.HeadStartFreezeListener;
import com.yamakotaro.manhunt.listeners.RunnerEliminationListener;
import com.yamakotaro.manhunt.listeners.SpecialItemListener;
import com.yamakotaro.manhunt.listeners.StatusGuiClickListener;
import com.yamakotaro.manhunt.tasks.CompassTrackingTask;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class ManhuntPlugin extends JavaPlugin {

    private CompassTrackingTask compassTrackingTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Messages messages = new Messages(this);
        SpecialItems specialItems = new SpecialItems(this, messages);
        GameManager gameManager = new GameManager(messages, specialItems, this);
        StatusGuiBuilder statusGuiBuilder = new StatusGuiBuilder(gameManager.game(), messages);

        getServer().getPluginManager().registerEvents(new HeadStartFreezeListener(gameManager.game()), this);
        getServer().getPluginManager().registerEvents(new DragonDeathListener(gameManager), this);
        getServer().getPluginManager().registerEvents(new RunnerEliminationListener(gameManager), this);
        getServer().getPluginManager().registerEvents(new SpecialItemListener(this, gameManager, specialItems, messages), this);
        getServer().getPluginManager().registerEvents(new StatusGuiClickListener(), this);

        ManhuntCommand manhuntCommand = new ManhuntCommand(this, gameManager, messages, statusGuiBuilder);
        PluginCommand command = getCommand("manhunt");
        command.setExecutor(manhuntCommand);
        command.setTabCompleter(manhuntCommand);

        long compassInterval = getConfig().getLong("compass-update-interval-ticks", 20);
        this.compassTrackingTask = new CompassTrackingTask(gameManager.game());
        compassTrackingTask.runTaskTimer(this, compassInterval, compassInterval);
    }

    @Override
    public void onDisable() {
        if (compassTrackingTask != null) {
            compassTrackingTask.cancel();
        }
    }
}
