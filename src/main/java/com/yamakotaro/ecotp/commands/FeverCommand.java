package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.EcoTpPlugin;
import com.yamakotaro.ecotp.FeverManager;
import com.yamakotaro.ecotp.TabCompleteUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Collections;
import java.util.List;

/** /fever : 状態表示。管理者は /fever start [分] [倍率] と /fever stop。 */
public class FeverCommand implements CommandExecutor, TabCompleter {

    private final EcoTpPlugin plugin;

    public FeverCommand(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        FeverManager fever = plugin.getFeverManager();
        String sub = args.length == 0 ? "" : args[0].toLowerCase();
        if (sub.equals("start") || sub.equals("stop")) {
            if (!sender.hasPermission("ecotp.admin")) {
                sender.sendMessage(plugin.msg("general.no-permission"));
                return true;
            }
            if (sub.equals("stop")) {
                if (!fever.stopManual()) {
                    sender.sendMessage(plugin.msg("fever.not-active"));
                }
                return true;
            }
            int minutes = 30;
            double multiplier = plugin.getConfig().getDouble("fever.multiplier", 2.0);
            try {
                if (args.length >= 2) {
                    minutes = Math.max(1, Integer.parseInt(args[1]));
                }
                if (args.length >= 3) {
                    multiplier = Double.parseDouble(args[2]);
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.msg("fever.usage"));
                return true;
            }
            if (!Double.isFinite(multiplier) || multiplier <= 1.0 || multiplier > 10.0) {
                sender.sendMessage(plugin.msg("fever.usage"));
                return true;
            }
            if (!fever.startManual(minutes, multiplier)) {
                sender.sendMessage(plugin.msg("fever.already-active"));
            }
            return true;
        }
        if (fever.isActive()) {
            long remaining = fever.remainingSeconds();
            sender.sendMessage(plugin.msg("fever.status-active",
                    "multiplier", String.valueOf(fever.multiplier()).replace(".0", ""),
                    "time", String.format("%d:%02d", remaining / 60, remaining % 60)));
        } else {
            sender.sendMessage(plugin.msg("fever.status-inactive"));
        }
        List<String> schedule = plugin.getConfig().getStringList("fever.schedule");
        if (!schedule.isEmpty()) {
            sender.sendMessage(plugin.msg("fever.status-schedule", "schedule", String.join(", ", schedule)));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("ecotp.admin")) {
            return TabCompleteUtil.filterPrefix(List.of("start", "stop"), args[0]);
        }
        return Collections.emptyList();
    }
}
