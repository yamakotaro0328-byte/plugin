package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.EcoTpPlugin;
import com.yamakotaro.ecotp.gui.QuestHolder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /quest : 今日のデイリークエストを開く。 */
public class QuestCommand implements CommandExecutor {

    private final EcoTpPlugin plugin;

    public QuestCommand(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().get("general.players-only"));
            return true;
        }
        if (!plugin.getDailyQuestManager().isEnabled()) {
            player.sendMessage(plugin.msg("general.feature-disabled"));
            return true;
        }
        player.openInventory(new QuestHolder(plugin, player).getInventory());
        return true;
    }
}
