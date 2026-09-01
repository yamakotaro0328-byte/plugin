package com.yamakotaro.ecobanvelocity.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import com.yamakotaro.ecoban.core.PunishmentManager;
import com.yamakotaro.ecoban.core.migrate.AdvancedBanImporter;
import com.yamakotaro.ecoban.core.migrate.LiteBansImporter;
import com.yamakotaro.ecobanvelocity.EcoBanVelocityConfig;
import com.yamakotaro.ecobanvelocity.Messages;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * /ecoban reload, and /ecoban import &lt;advancedban|litebans&gt; - one-time migration from
 * either plugin's existing database directly into the shared MySQL database every backend server
 * already reads from. Runs off the proxy's main thread since a large network's punishment
 * history can take a while to copy.
 */
public class EcoBanCommand implements SimpleCommand {

    private final ProxyServer proxyServer;
    private final Object plugin;
    private final EcoBanVelocityConfig config;
    private final PunishmentManager punishmentManager;
    private final Messages messages;
    private final Logger logger;

    public EcoBanCommand(ProxyServer proxyServer, Object plugin, EcoBanVelocityConfig config,
                          PunishmentManager punishmentManager, Messages messages, Logger logger) {
        this.proxyServer = proxyServer;
        this.plugin = plugin;
        this.config = config;
        this.punishmentManager = punishmentManager;
        this.messages = messages;
        this.logger = logger;
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("ecoban.admin");
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0) {
            invocation.source().sendMessage(messages.get("ecoban.usage", Map.of()));
            return;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                config.load();
                invocation.source().sendMessage(messages.get("ecoban.reloaded", Map.of()));
            }
            case "import" -> handleImport(invocation, args);
            default -> invocation.source().sendMessage(messages.get("ecoban.usage", Map.of()));
        }
    }

    private void handleImport(Invocation invocation, String[] args) {
        if (args.length < 2) {
            invocation.source().sendMessage(messages.get("ecoban.import-usage", Map.of()));
            return;
        }
        String source = args[1].toLowerCase();
        if (!source.equals("advancedban") && !source.equals("litebans")) {
            invocation.source().sendMessage(messages.get("ecoban.import-unknown-source", Map.of("source", source)));
            return;
        }
        invocation.source().sendMessage(messages.get("ecoban.import-started", Map.of("source", source)));
        proxyServer.getScheduler().buildTask(plugin, () -> {
            int count;
            try {
                count = source.equals("advancedban") ? importAdvancedBan() : importLiteBans();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Import from " + source + " failed", e);
                invocation.source().sendMessage(messages.get("ecoban.import-failed", Map.of("source", source)));
                return;
            }
            invocation.source().sendMessage(messages.get("ecoban.import-finished",
                    Map.of("source", source, "count", String.valueOf(count))));
        }).schedule();
    }

    private int importAdvancedBan() throws Exception {
        String host = config.getString("import.advancedban.host", "localhost");
        int port = config.getInt("import.advancedban.port", 3306);
        String database = config.getString("import.advancedban.database", "advancedban");
        String username = config.getString("import.advancedban.username", "root");
        String password = config.getString("import.advancedban.password", "");
        try (Connection connection = openMysql(host, port, database, username, password)) {
            return AdvancedBanImporter.importInto(connection, punishmentManager.getStorage(), logger);
        }
    }

    private int importLiteBans() throws Exception {
        String host = config.getString("import.litebans.host", "localhost");
        int port = config.getInt("import.litebans.port", 3306);
        String database = config.getString("import.litebans.database", "litebans");
        String username = config.getString("import.litebans.username", "root");
        String password = config.getString("import.litebans.password", "");
        String tablePrefix = config.getString("import.litebans.table-prefix", "litebans_");
        try (Connection connection = openMysql(host, port, database, username, password)) {
            return LiteBansImporter.importInto(connection, punishmentManager.getStorage(), tablePrefix, logger);
        }
    }

    private Connection openMysql(String host, int port, String database, String username, String password) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&characterEncoding=utf8";
        return DriverManager.getConnection(url, username, password);
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0];
            return List.of("reload", "import").stream().filter(s -> s.startsWith(prefix.toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("import")) {
            String prefix = args[1];
            return List.of("advancedban", "litebans").stream().filter(s -> s.startsWith(prefix.toLowerCase())).toList();
        }
        return List.of();
    }
}
