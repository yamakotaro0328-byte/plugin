package com.yamakotaro.ecorail;

import com.yamakotaro.ecorail.cart.CartManager;
import com.yamakotaro.ecorail.cart.ChunkForceLoadTask;
import com.yamakotaro.ecorail.commands.EcoRailCommand;
import com.yamakotaro.ecorail.listeners.VehicleCollisionListener;
import com.yamakotaro.ecorail.stop.StopPointManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class EcoRailPlugin extends JavaPlugin {

    private Messages messages;
    private StopPointManager stopPointManager;
    private CartManager cartManager;
    private ChunkForceLoadTask chunkForceLoadTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.messages = new Messages(this);
        this.stopPointManager = new StopPointManager(this);
        this.cartManager = new CartManager(this);

        getServer().getPluginManager().registerEvents(new VehicleCollisionListener(this, cartManager), this);

        EcoRailCommand ecoRailCommand = new EcoRailCommand(this, stopPointManager, cartManager, messages);
        PluginCommand command = getCommand("ecorail");
        command.setExecutor(ecoRailCommand);
        command.setTabCompleter(ecoRailCommand);

        long tickInterval = getConfig().getLong("physics.tick-interval-ticks", 4);
        this.chunkForceLoadTask = new ChunkForceLoadTask(this, cartManager, stopPointManager);
        chunkForceLoadTask.runTaskTimer(this, tickInterval, tickInterval);
    }

    @Override
    public void onDisable() {
        if (chunkForceLoadTask != null) {
            chunkForceLoadTask.cancel();
        }
        if (cartManager != null) {
            cartManager.save();
        }
    }
}
