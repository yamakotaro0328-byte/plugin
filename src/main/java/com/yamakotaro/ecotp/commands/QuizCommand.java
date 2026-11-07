package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.ChatQuizManager;
import com.yamakotaro.ecotp.EcoTpPlugin;
import com.yamakotaro.ecotp.TabCompleteUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** /quiz [top|start [math|typing|scramble]|stop] */
public class QuizCommand implements CommandExecutor, TabCompleter {

    private final EcoTpPlugin plugin;

    public QuizCommand(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ChatQuizManager quiz = plugin.getChatQuizManager();
        String sub = args.length == 0 ? "" : args[0].toLowerCase();
        switch (sub) {
            case "top" -> {
                List<Map.Entry<String, Integer>> top = quiz.top(10);
                if (top.isEmpty()) {
                    sender.sendMessage(plugin.msg("quiz.top-empty"));
                    return true;
                }
                sender.sendMessage(plugin.msg("quiz.top-header"));
                int rank = 1;
                for (Map.Entry<String, Integer> entry : top) {
                    sender.sendMessage(plugin.getMessages().get("quiz.top-entry", "rank", String.valueOf(rank++),
                            "player", entry.getKey(), "wins", String.valueOf(entry.getValue())));
                }
            }
            case "start" -> {
                if (!sender.hasPermission("ecotp.admin")) {
                    sender.sendMessage(plugin.msg("general.no-permission"));
                    return true;
                }
                ChatQuizManager.Type type = args.length >= 2 ? ChatQuizManager.Type.parse(args[1]) : null;
                if (!quiz.start(type)) {
                    sender.sendMessage(plugin.msg("quiz.already-running"));
                }
            }
            case "stop" -> {
                if (!sender.hasPermission("ecotp.admin")) {
                    sender.sendMessage(plugin.msg("general.no-permission"));
                    return true;
                }
                if (!quiz.stop()) {
                    sender.sendMessage(plugin.msg("quiz.none-running"));
                }
            }
            default -> {
                if (quiz.isRunning()) {
                    sender.sendMessage(plugin.msg("quiz.status-running"));
                } else {
                    sender.sendMessage(plugin.msg("quiz.status-next",
                            "minutes", String.valueOf(Math.max(1, (quiz.getSecondsUntilNext() + 59) / 60))));
                }
                if (sender instanceof Player player) {
                    sender.sendMessage(plugin.msg("quiz.status-wins", "wins", String.valueOf(quiz.getWins(player.getUniqueId()))));
                }
                sender.sendMessage(plugin.msg("quiz.usage"));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompleteUtil.filterPrefix(sender.hasPermission("ecotp.admin")
                    ? List.of("top", "start", "stop") : List.of("top"), args[0]);
        }
        if (args.length == 2 && "start".equalsIgnoreCase(args[0]) && sender.hasPermission("ecotp.admin")) {
            return TabCompleteUtil.filterPrefix(List.of("math", "typing", "scramble"), args[1]);
        }
        return Collections.emptyList();
    }
}
