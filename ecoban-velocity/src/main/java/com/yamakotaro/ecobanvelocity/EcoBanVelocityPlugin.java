package com.yamakotaro.ecobanvelocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.yamakotaro.ecoban.core.MySqlPunishmentStorage;
import com.yamakotaro.ecoban.core.PunishmentManager;
import com.yamakotaro.ecoban.core.PunishmentStorage;
import com.yamakotaro.ecoban.core.web.WebDashboard;
import com.yamakotaro.ecobanvelocity.commands.BanCommands;
import com.yamakotaro.ecobanvelocity.commands.EcoBanCommand;
import com.yamakotaro.ecobanvelocity.commands.KickWarnCommands;
import com.yamakotaro.ecobanvelocity.commands.LookupCommands;
import com.yamakotaro.ecobanvelocity.commands.MuteCommands;
import com.yamakotaro.ecobanvelocity.listeners.BanEnforcementListener;
import com.yamakotaro.ecobanvelocity.listeners.MuteEnforcementListener;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Velocity plugins are Guice-injected, so this class does no real work in its constructor -
 * everything that touches config, storage, or the network happens in onProxyInitialize once
 * injection has finished.
 *
 * Note: Velocity injects an SLF4J Logger by convention, but ecoban-core (shared with the Paper
 * plugin) is written against java.util.logging.Logger to stay platform-agnostic. Rather than
 * adapt one to the other, this plugin just uses its own java.util.logging.Logger for everything
 * that touches ecoban-core - it still reaches the proxy console, just without Velocity's SLF4J
 * formatting.
 */
public class EcoBanVelocityPlugin {

    private static final long KICK_POLL_INTERVAL_SECONDS = 5;
    private static final long EXPIRY_SWEEP_INTERVAL_SECONDS = 60;

    private final ProxyServer proxyServer;
    private final Path dataDirectory;
    private final Logger logger = Logger.getLogger("EcoBan-Velocity");

    private EcoBanVelocityConfig config;
    private Messages messages;
    private PunishmentStorage storage;
    private PunishmentManager punishmentManager;
    private WebDashboard webDashboard;

    @Inject
    public EcoBanVelocityPlugin(ProxyServer proxyServer, @DataDirectory Path dataDirectory) {
        this.proxyServer = proxyServer;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        this.config = new EcoBanVelocityConfig(dataDirectory, logger);
        this.messages = new Messages(config);

        boolean useMysql = "mysql".equalsIgnoreCase(config.getString("storage.type", "mysql"));
        if (!useMysql) {
            logger.severe("EcoBan-Velocity requires storage.type: mysql in config.yml - SQLite "
                    + "cannot be shared between this proxy and every backend server. Disabling.");
            return;
        }
        this.storage = new MySqlPunishmentStorage(
                config.getString("storage.mysql.host", "localhost"),
                config.getInt("storage.mysql.port", 3306),
                config.getString("storage.mysql.database", "ecoban"),
                config.getString("storage.mysql.username", "root"),
                config.getString("storage.mysql.password", ""),
                logger);
        this.punishmentManager = new PunishmentManager(storage);

        proxyServer.getEventManager().register(this, new BanEnforcementListener(punishmentManager, messages));
        proxyServer.getEventManager().register(this, new MuteEnforcementListener(punishmentManager, messages));

        registerCommand(new BanCommands(proxyServer, punishmentManager, messages), "ban", "tempban", "unban", "ipban", "unbanip");
        registerCommand(new MuteCommands(proxyServer, punishmentManager, messages), "mute", "tempmute", "unmute");
        registerCommand(new KickWarnCommands(proxyServer, punishmentManager, messages), "kick", "warn");
        registerCommand(new LookupCommands(proxyServer, punishmentManager, messages), "history", "banlist");
        registerCommand(new EcoBanCommand(proxyServer, this, config, punishmentManager, messages, logger), "ecoban");

        if (config.getBoolean("web.enabled", false)) {
            this.webDashboard = new WebDashboard(punishmentManager,
                    config.getInt("web.port", 8123),
                    config.getString("web.username", "admin"),
                    config.getString("web.password", "changeme"),
                    logger);
            webDashboard.start();
        }

        proxyServer.getScheduler().buildTask(this, punishmentManager::deactivateExpired)
                .repeat(EXPIRY_SWEEP_INTERVAL_SECONDS, TimeUnit.SECONDS)
                .schedule();
        proxyServer.getScheduler().buildTask(this, this::pollPendingKicks)
                .repeat(KICK_POLL_INTERVAL_SECONDS, TimeUnit.SECONDS)
                .schedule();
    }

    /**
     * Unlike a single Paper backend, the proxy sees every player on the network - so this is the
     * one place a pending kick (issued from the web dashboard, or from a backend with no
     * connection to the target) is guaranteed to actually reach them.
     */
    private void pollPendingKicks() {
        for (PunishmentStorage.PendingKick pending : punishmentManager.pollPendingKicks(20)) {
            Optional<Player> online = proxyServer.getPlayer(pending.targetUuid());
            online.ifPresent(player -> player.disconnect(messages.get("kick.message",
                    Map.of("reason", pending.reason() != null ? pending.reason() : ""))));
            punishmentManager.markKickHandled(pending.id());
        }
    }

    private void registerCommand(SimpleCommand command, String primary, String... aliases) {
        CommandMeta meta = proxyServer.getCommandManager().metaBuilder(primary)
                .aliases(aliases)
                .plugin(this)
                .build();
        proxyServer.getCommandManager().register(meta, command);
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (webDashboard != null) {
            webDashboard.stop();
        }
        if (storage != null) {
            storage.close();
        }
    }
}
