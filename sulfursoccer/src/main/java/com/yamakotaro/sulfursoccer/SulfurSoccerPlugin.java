package com.yamakotaro.sulfursoccer;

import com.yamakotaro.sulfursoccer.arena.ArenaManager;
import com.yamakotaro.sulfursoccer.commands.SoccerCommand;
import com.yamakotaro.sulfursoccer.listeners.BallDeathListener;
import com.yamakotaro.sulfursoccer.listeners.SelectionListener;
import com.yamakotaro.sulfursoccer.match.MatchManager;
import com.yamakotaro.sulfursoccer.match.MatchScoreboard;
import com.yamakotaro.sulfursoccer.match.SoccerTickTask;
import com.yamakotaro.sulfursoccer.selection.SelectionManager;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class SulfurSoccerPlugin extends JavaPlugin {

    private SoccerTickTask soccerTickTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Messages messages = new Messages(this);
        ArenaManager arenaManager = new ArenaManager(this);
        SelectionManager selectionManager = new SelectionManager();
        MatchScoreboard matchScoreboard = new MatchScoreboard(messages);
        MatchManager matchManager = new MatchManager(this, arenaManager, messages, matchScoreboard);
        NamespacedKey wandKey = new NamespacedKey(this, "wand");

        getServer().getPluginManager().registerEvents(new SelectionListener(this, selectionManager, messages), this);
        getServer().getPluginManager().registerEvents(new BallDeathListener(matchManager), this);

        SoccerCommand soccerCommand = new SoccerCommand(arenaManager, matchManager, selectionManager, wandKey, messages);
        PluginCommand command = getCommand("soccer");
        command.setExecutor(soccerCommand);
        command.setTabCompleter(soccerCommand);

        long tickInterval = getConfig().getLong("match.tick-interval-ticks", 10);
        this.soccerTickTask = new SoccerTickTask(this, arenaManager, matchManager, matchScoreboard);
        soccerTickTask.runTaskTimer(this, tickInterval, tickInterval);
    }

    @Override
    public void onDisable() {
        if (soccerTickTask != null) {
            soccerTickTask.cancel();
        }
    }
}
