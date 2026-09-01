package com.yamakotaro.ecoban.commands;

import com.yamakotaro.ecoban.EcoBanPlugin;
import com.yamakotaro.ecoban.Messages;
import com.yamakotaro.ecoban.TabCompleteUtil;
import com.yamakotaro.ecoban.core.migrate.AdvancedBanImporter;
import com.yamakotaro.ecoban.core.migrate.LiteBansImporter;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * /ecoban reload, and /ecoban import &lt;advancedban|litebans&gt; - one-time migration from
 * either plugin's existing database into EcoBan's own storage. Runs off the main thread since a
 * large server's punishment history can take a while to copy.
 */
public class EcoBanCommand implements CommandExecutor, TabCompleter {

    private final EcoBanPlugin plugin;
    private final Messages messages;

    public EcoBanCommand(EcoBanPlugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ecoban.admin")) {
            sender.sendMessage(messages.get("general.no-permission", Map.of()));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(messages.get("ecoban.usage", Map.of()));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage(messages.get("ecoban.reloaded", Map.of()));
            }
            case "import" -> handleImport(sender, args);
            default -> sender.sendMessage(messages.get("ecoban.usage", Map.of()));
        }
        return true;
    }

    private void handleImport(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(messages.get("ecoban.import-usage", Map.of()));
            return;
        }
        String source = args[1].toLowerCase();
        if (!source.equals("advancedban") && !source.equals("litebans")) {
            sender.sendMessage(messages.get("ecoban.import-unknown-source", Map.of("source", source)));
            return;
        }
        sender.sendMessage(messages.get("ecoban.import-started", Map.of("source", source)));
        new BukkitRunnable() {
            @Override
            public void run() {
                int count;
                try {
                    count = source.equals("advancedban") ? importAdvancedBan() : importLiteBans();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Import from " + source + " failed", e);
                    Bukkit.getScheduler().runTask(plugin, () ->
                            sender.sendMessage(messages.get("ecoban.import-failed", Map.of("source", source))));
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(messages.get("ecoban.import-finished",
                        Map.of("source", source, "count", String.valueOf(count)))));
            }
        }.runTaskAsynchronously(plugin);
    }

    private int importAdvancedBan() throws Exception {
        String host = plugin.getConfig().getString("import.advancedban.host", "localhost");
        int port = plugin.getConfig().getInt("import.advancedban.port", 3306);
        String database = plugin.getConfig().getString("import.advancedban.database", "advancedban");
        String username = plugin.getConfig().getString("import.advancedban.username", "root");
        String password = plugin.getConfig().getString("import.advancedban.password", "");
        try (Connection connection = openMysql(host, port, database, username, password)) {
            return AdvancedBanImporter.importInto(connection, plugin.getPunishmentManager().getStorage(), plugin.getLogger());
        }
    }

    private int importLiteBans() throws Exception {
        String host = plugin.getConfig().getString("import.litebans.host", "localhost");
        int port = plugin.getConfig().getInt("import.litebans.port", 3306);
        String database = plugin.getConfig().getString("import.litebans.database", "litebans");
        String username = plugin.getConfig().getString("import.litebans.username", "root");
        String password = plugin.getConfig().getString("import.litebans.password", "");
        String tablePrefix = plugin.getConfig().getString("import.litebans.table-prefix", "litebans_");
        try (Connection connection = openMysql(host, port, database, username, password)) {
            return LiteBansImporter.importInto(connection, plugin.getPunishmentManager().getStorage(), tablePrefix, plugin.getLogger());
        }
    }

    private Connection openMysql(String host, int port, String database, String username, String password) throws Exception {
        // Only ecoban-core's MySqlPunishmentStorage triggers this driver's static registration,
        // and that class is never loaded when storage.type is sqlite - load it explicitly here
        // too, since importing works regardless of which storage backend EcoBan itself is using.
        Class.forName("com.mysql.cj.jdbc.Driver");
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&characterEncoding=utf8";
        return DriverManager.getConnection(url, username, password);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompleteUtil.filterPrefix(List.of("reload", "import"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("import")) {
            return TabCompleteUtil.filterPrefix(List.of("advancedban", "litebans"), args[1]);
        }
        return Collections.emptyList();
    }
}
