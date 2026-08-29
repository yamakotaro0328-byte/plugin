package com.yamakotaro.serverkit.dragonarena.commands;

import com.yamakotaro.serverkit.Messages;
import com.yamakotaro.serverkit.dragonarena.DragonArenaManager;
import com.yamakotaro.serverkit.dragonarena.PartyManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;

public class DragonFightCommand implements CommandExecutor {

    private final Plugin plugin;
    private final DragonArenaManager arenaManager;
    private final PartyManager partyManager;
    private final Messages messages;

    public DragonFightCommand(Plugin plugin, DragonArenaManager arenaManager, PartyManager partyManager, Messages messages) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        this.partyManager = partyManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("general.players-only", Map.of()));
            return true;
        }
        if (!player.hasPermission("serverkit.dragonfight")) {
            player.sendMessage(messages.get("general.no-permission", Map.of()));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(messages.get("dragonarena.usage", Map.of()));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "start" -> handleStart(player);
            case "leave" -> handleLeave(player);
            case "party" -> handleParty(player, args);
            default -> player.sendMessage(messages.get("dragonarena.usage", Map.of()));
        }
        return true;
    }

    private void handleStart(Player player) {
        DragonArenaManager.StartOutcome outcome = arenaManager.start(player);
        switch (outcome.result()) {
            case NOT_LEADER -> player.sendMessage(messages.get("dragonarena.party-not-leader", Map.of()));
            case ALREADY_IN_FIGHT -> player.sendMessage(messages.get("dragonarena.already-in-fight", Map.of()));
            case ON_COOLDOWN -> player.sendMessage(messages.get("dragonarena.on-cooldown",
                    Map.of("seconds", String.valueOf(outcome.cooldownSecondsRemaining()))));
            case MAX_INSTANCES -> player.sendMessage(messages.get("dragonarena.max-instances", Map.of()));
            case WORLD_ERROR -> player.sendMessage(messages.get("dragonarena.world-error", Map.of()));
            case SUCCESS -> player.sendMessage(messages.get("dragonarena.starting", Map.of()));
        }
    }

    private void handleLeave(Player player) {
        DragonArenaManager.LeaveResult result = arenaManager.leave(player);
        if (result == DragonArenaManager.LeaveResult.NOT_IN_FIGHT) {
            player.sendMessage(messages.get("dragonarena.not-in-fight", Map.of()));
        } else {
            player.sendMessage(messages.get("dragonarena.left-fight", Map.of()));
        }
    }

    private void handleParty(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(messages.get("dragonarena.usage", Map.of()));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "invite" -> handlePartyInvite(player, args);
            case "accept" -> handlePartyAccept(player);
            case "leave" -> handlePartyLeave(player);
            default -> player.sendMessage(messages.get("dragonarena.usage", Map.of()));
        }
    }

    private void handlePartyInvite(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(messages.get("dragonarena.usage", Map.of()));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            player.sendMessage(messages.get("general.player-not-found", Map.of("player", args[2])));
            return;
        }
        int teamSizeMax = plugin.getConfig().getInt("dragonarena.team-size-max", 4);
        long timeoutMillis = plugin.getConfig().getLong("dragonarena.invite-timeout-seconds", 60) * 1000L;
        PartyManager.InviteResult result = partyManager.invite(player.getUniqueId(), target.getUniqueId(), teamSizeMax, timeoutMillis);
        switch (result) {
            case NOT_LEADER -> player.sendMessage(messages.get("dragonarena.party-not-leader", Map.of()));
            case TARGET_IS_SELF -> player.sendMessage(messages.get("dragonarena.party-target-is-self", Map.of()));
            case TARGET_IN_PARTY -> player.sendMessage(messages.get("dragonarena.party-target-in-party", Map.of()));
            case PARTY_FULL -> player.sendMessage(messages.get("dragonarena.party-full", Map.of()));
            case SUCCESS -> {
                player.sendMessage(messages.get("dragonarena.party-invited", Map.of("player", target.getName())));
                target.sendMessage(messages.get("dragonarena.party-invite-received", Map.of("player", player.getName())));
            }
        }
    }

    private void handlePartyAccept(Player player) {
        UUID leaderId = partyManager.acceptResult(player.getUniqueId());
        PartyManager.AcceptResult result = partyManager.accept(player.getUniqueId());
        if (result != PartyManager.AcceptResult.SUCCESS) {
            player.sendMessage(messages.get("dragonarena.party-no-pending-invite", Map.of()));
            return;
        }
        Player leader = leaderId != null ? Bukkit.getPlayer(leaderId) : null;
        String leaderName = leader != null ? leader.getName() : "?";
        player.sendMessage(messages.get("dragonarena.party-joined", Map.of("player", leaderName)));
    }

    private void handlePartyLeave(Player player) {
        boolean left = partyManager.leave(player.getUniqueId());
        if (left) {
            player.sendMessage(messages.get("dragonarena.party-left", Map.of()));
        } else {
            player.sendMessage(messages.get("dragonarena.party-not-in-party", Map.of()));
        }
    }
}
