package com.yamakotaro.ecoban;

import com.yamakotaro.ecoban.commands.BanCommands;
import com.yamakotaro.ecoban.commands.EcoBanCommand;
import com.yamakotaro.ecoban.commands.KickWarnCommands;
import com.yamakotaro.ecoban.commands.LookupCommands;
import com.yamakotaro.ecoban.commands.MuteCommands;
import com.yamakotaro.ecoban.core.MySqlPunishmentStorage;
import com.yamakotaro.ecoban.core.PunishmentManager;
import com.yamakotaro.ecoban.core.PunishmentStorage;
import com.yamakotaro.ecoban.core.SqlitePunishmentStorage;
import com.yamakotaro.ecoban.core.web.WebDashboard;
import com.yamakotaro.ecoban.listeners.BanEnforcementListener;
import com.yamakotaro.ecoban.listeners.MuteEnforcementListener;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;
import java.util.logging.Logger;

public class EcoBanPlugin extends JavaPlugin {

    private static final long SAVE_TICK_INTERVAL = 20L * 60; // every minute
    private static final long KICK_POLL_TICK_INTERVAL = 20L * 5; // every 5 seconds

    private Messages messages;
    private PunishmentStorage storage;
    private PunishmentManager punishmentManager;
    private WebDashboard webDashboard;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.messages = new Messages(this);

        Logger logger = getLogger();
        boolean useMysql = "mysql".equalsIgnoreCase(getConfig().getString("storage.type", "sqlite"));
        this.storage = useMysql
                ? new MySqlPunishmentStorage(
                        getConfig().getString("storage.mysql.host", "localhost"),
                        getConfig().getInt("storage.mysql.port", 3306),
                        getConfig().getString("storage.mysql.database", "ecoban"),
                        getConfig().getString("storage.mysql.username", "root"),
                        getConfig().getString("storage.mysql.password", ""),
                        logger)
                : new SqlitePunishmentStorage(new File(getDataFolder(), "ecoban.db"), logger);
        this.punishmentManager = new PunishmentManager(storage);

        getServer().getPluginManager().registerEvents(new BanEnforcementListener(punishmentManager, messages), this);
        getServer().getPluginManager().registerEvents(new MuteEnforcementListener(punishmentManager, messages), this);

        registerAll(new BanCommands(punishmentManager, messages), "ban", "tempban", "unban", "ipban", "unbanip");
        registerAll(new MuteCommands(punishmentManager, messages), "mute", "tempmute", "unmute");
        registerAll(new KickWarnCommands(punishmentManager, messages), "kick", "warn");
        registerAll(new LookupCommands(punishmentManager, messages), "history", "banlist");
        EcoBanCommand ecoBanCommand = new EcoBanCommand(this, messages);
        PluginCommand ecobanCommand = getCommand("ecoban");
        ecobanCommand.setExecutor(ecoBanCommand);
        ecobanCommand.setTabCompleter(ecoBanCommand);

        if (getConfig().getBoolean("web.enabled", true)) {
            this.webDashboard = new WebDashboard(punishmentManager,
                    getConfig().getInt("web.port", 8123),
                    getConfig().getString("web.username", "admin"),
                    getConfig().getString("web.password", "changeme"),
                    logger);
            webDashboard.start();
        }

        getServer().getScheduler().runTaskTimer(this, punishmentManager::deactivateExpired, SAVE_TICK_INTERVAL, SAVE_TICK_INTERVAL);
        getServer().getScheduler().runTaskTimer(this, this::pollPendingKicks, KICK_POLL_TICK_INTERVAL, KICK_POLL_TICK_INTERVAL);
    }

    /**
     * Kicks issued from the web dashboard have no live connection to reach into, so they're
     * queued in storage instead (see PunishmentManager#kick) - this is what actually carries them
     * out for any target who happens to be on this server.
     */
    private void pollPendingKicks() {
        for (PunishmentStorage.PendingKick pending : punishmentManager.pollPendingKicks(20)) {
            Player online = getServer().getPlayer(pending.targetUuid());
            if (online != null) {
                online.kick(messages.get("kick.message", Map.of("reason", pending.reason() != null ? pending.reason() : "")));
            }
            punishmentManager.markKickHandled(pending.id());
        }
    }

    private <T extends CommandExecutor & TabCompleter> void registerAll(T executorAndCompleter, String... commandNames) {
        for (String name : commandNames) {
            PluginCommand command = getCommand(name);
            command.setExecutor(executorAndCompleter);
            command.setTabCompleter(executorAndCompleter);
        }
    }

    @Override
    public void onDisable() {
        if (webDashboard != null) {
            webDashboard.stop();
        }
        if (storage != null) {
            storage.close();
        }
    }

    public Messages getMessages() {
        return messages;
    }

    public PunishmentManager getPunishmentManager() {
        return punishmentManager;
    }
}
