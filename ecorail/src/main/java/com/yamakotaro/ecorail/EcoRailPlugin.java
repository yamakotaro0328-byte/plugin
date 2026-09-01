package com.yamakotaro.ecorail;

import com.yamakotaro.ecorail.cart.CartManager;
import com.yamakotaro.ecorail.cart.ChunkForceLoadTask;
import com.yamakotaro.ecorail.commands.EcoRailCommand;
import com.yamakotaro.ecorail.items.TicketItemFactory;
import com.yamakotaro.ecorail.listeners.SettingsMenuListener;
import com.yamakotaro.ecorail.listeners.SignListener;
import com.yamakotaro.ecorail.listeners.VehicleCollisionListener;
import com.yamakotaro.ecorail.settings.PlayerSettingsManager;
import com.yamakotaro.ecorail.signs.TicketSignManager;
import com.yamakotaro.ecorail.station.StationManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class EcoRailPlugin extends JavaPlugin {

    private Messages messages;
    private StationManager stationManager;
    private TicketSignManager ticketSignManager;
    private CartManager cartManager;
    private PlayerSettingsManager settingsManager;
    private EconomyHolder economyHolder;
    private ChunkForceLoadTask chunkForceLoadTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.messages = new Messages(this);
        this.stationManager = new StationManager(this);
        this.ticketSignManager = new TicketSignManager(this);
        this.cartManager = new CartManager(this);
        this.settingsManager = new PlayerSettingsManager(this);

        this.economyHolder = new EconomyHolder(this);
        if (!economyHolder.setup()) {
            getLogger().warning("No Vault economy found - riders will need a physical ticket item to board.");
        }

        TicketItemFactory ticketItemFactory = new TicketItemFactory(this, messages);

        getServer().getPluginManager().registerEvents(
                new SignListener(this, stationManager, ticketSignManager, ticketItemFactory, economyHolder, cartManager, messages), this);
        getServer().getPluginManager().registerEvents(new SettingsMenuListener(settingsManager, messages), this);
        getServer().getPluginManager().registerEvents(new VehicleCollisionListener(cartManager, settingsManager), this);

        EcoRailCommand ecoRailCommand = new EcoRailCommand(this, stationManager, ticketItemFactory, settingsManager, messages);
        PluginCommand command = getCommand("ecorail");
        command.setExecutor(ecoRailCommand);
        command.setTabCompleter(ecoRailCommand);

        long tickInterval = getConfig().getLong("physics.tick-interval-ticks", 4);
        this.chunkForceLoadTask = new ChunkForceLoadTask(this, cartManager, stationManager, settingsManager, messages);
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
