package com.yamakotaro.serverkit.referral.commands;

import com.yamakotaro.serverkit.Messages;
import com.yamakotaro.serverkit.referral.ReferralManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public class ReferralCommand implements CommandExecutor {

    private final ReferralManager referralManager;
    private final Messages messages;

    public ReferralCommand(ReferralManager referralManager, Messages messages) {
        this.referralManager = referralManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("general.players-only", Map.of()));
            return true;
        }
        if (args.length != 2) {
            player.sendMessage(messages.get("referral.usage", Map.of()));
            return true;
        }
        OfflinePlayer target = resolve(args[1]);
        switch (args[0].toLowerCase()) {
            case "claim" -> handleClaim(player, target, args[1]);
            case "confirm" -> handleConfirm(player, target, args[1]);
            default -> player.sendMessage(messages.get("referral.usage", Map.of()));
        }
        return true;
    }

    private OfflinePlayer resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        return Bukkit.getOfflinePlayer(name);
    }

    private void handleClaim(Player player, OfflinePlayer inviter, String inviterName) {
        ReferralManager.ClaimResult result = referralManager.claim(player, inviter);
        switch (result) {
            case CANNOT_TARGET_SELF -> player.sendMessage(messages.get("referral.cannot-target-self", Map.of()));
            case TARGET_NEVER_PLAYED -> player.sendMessage(messages.get("referral.target-never-played", Map.of()));
            case ALREADY_CLAIMED -> player.sendMessage(messages.get("referral.already-claimed", Map.of()));
            case NOT_NEW_ENOUGH -> player.sendMessage(messages.get("referral.not-new-enough",
                    Map.of("minutes", String.valueOf(referralManager.windowMinutes()))));
            case SUCCESS -> {
                player.sendMessage(messages.get("referral.claim-sent", Map.of("player", inviterName, "you", player.getName())));
                Player onlineInviter = inviter.getPlayer();
                if (onlineInviter != null) {
                    onlineInviter.sendMessage(messages.get("referral.claim-received", Map.of("player", player.getName())));
                }
            }
        }
    }

    private void handleConfirm(Player player, OfflinePlayer claimant, String claimantName) {
        ReferralManager.ConfirmResult result = referralManager.confirm(player, claimant);
        switch (result) {
            case NO_PENDING_CLAIM -> player.sendMessage(messages.get("referral.no-pending-claim", Map.of("player", claimantName)));
            case ALREADY_REWARDED -> player.sendMessage(messages.get("referral.already-rewarded", Map.of()));
            case NO_ECONOMY -> player.sendMessage(messages.get("referral.no-economy", Map.of()));
            case SUCCESS -> {
                player.sendMessage(messages.get("referral.confirmed-inviter", Map.of(
                        "player", claimantName,
                        "amount", String.valueOf(referralManager.rewardAmount()))));
                Player onlineClaimant = claimant.getPlayer();
                if (onlineClaimant != null) {
                    onlineClaimant.sendMessage(messages.get("referral.confirmed-claimant-notify", Map.of("player", player.getName())));
                }
            }
        }
    }
}
