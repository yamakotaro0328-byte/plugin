package com.yamakotaro.serverkit.staff.commands;

import com.yamakotaro.serverkit.Messages;
import com.yamakotaro.serverkit.staff.VanishManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public class VanishCommand implements CommandExecutor {

    private final VanishManager vanishManager;
    private final Messages messages;

    public VanishCommand(VanishManager vanishManager, Messages messages) {
        this.vanishManager = vanishManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("general.players-only", Map.of()));
            return true;
        }
        if (!player.hasPermission("serverkit.staff.vanish")) {
            player.sendMessage(messages.get("general.no-permission", Map.of()));
            return true;
        }
        boolean nowVanished = vanishManager.toggle(player);
        player.sendMessage(messages.get(nowVanished ? "staff.vanish-on" : "staff.vanish-off", Map.of()));
        return true;
    }
}
