package com.yamakotaro.serverkit.staff.commands;

import com.yamakotaro.serverkit.Messages;
import com.yamakotaro.serverkit.staff.FreezeManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public class FreezeCommand implements CommandExecutor {

    private final FreezeManager freezeManager;
    private final Messages messages;

    public FreezeCommand(FreezeManager freezeManager, Messages messages) {
        this.freezeManager = freezeManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("serverkit.staff.freeze")) {
            sender.sendMessage(messages.get("general.no-permission", Map.of()));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(messages.get("staff.freeze-usage", Map.of()));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(messages.get("general.player-not-found", Map.of("player", args[0])));
            return true;
        }
        boolean nowFrozen = freezeManager.toggle(target.getUniqueId());
        sender.sendMessage(messages.get(nowFrozen ? "staff.freeze-on" : "staff.freeze-off",
                Map.of("player", target.getName())));
        target.sendMessage(messages.get(nowFrozen ? "staff.you-were-frozen" : "staff.you-were-unfrozen", Map.of()));
        return true;
    }
}
