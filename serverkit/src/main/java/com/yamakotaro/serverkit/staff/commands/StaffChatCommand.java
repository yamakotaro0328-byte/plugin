package com.yamakotaro.serverkit.staff.commands;

import com.yamakotaro.serverkit.Messages;
import com.yamakotaro.serverkit.staff.StaffChatManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public class StaffChatCommand implements CommandExecutor {

    private final StaffChatManager staffChatManager;
    private final Messages messages;

    public StaffChatCommand(StaffChatManager staffChatManager, Messages messages) {
        this.staffChatManager = staffChatManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("general.players-only", Map.of()));
            return true;
        }
        if (!player.hasPermission("serverkit.staff.staffchat")) {
            player.sendMessage(messages.get("general.no-permission", Map.of()));
            return true;
        }
        if (args.length == 0) {
            boolean nowOn = staffChatManager.toggle(player.getUniqueId());
            player.sendMessage(messages.get(nowOn ? "staff.staffchat-mode-on" : "staff.staffchat-mode-off", Map.of()));
            return true;
        }
        staffChatManager.broadcast(player, String.join(" ", args));
        return true;
    }
}
