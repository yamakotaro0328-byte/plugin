package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.ChatUtil;
import com.yamakotaro.ecotp.EcoTpPlugin;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PayCommand implements CommandExecutor {

    private final EcoTpPlugin plugin;

    public PayCommand(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("プレイヤーのみ実行できます。");
            return true;
        }
        if (!player.hasPermission("ecotp.pay")) {
            player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&cこのコマンドを使う権限がありません。"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&c使い方: /pay <プレイヤー名> <金額>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&cそのプレイヤーはオンラインではありません。"));
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&c自分自身には送金できません。"));
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&c金額は数値で指定してください。"));
            return true;
        }
        if (amount <= 0) {
            player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&c金額は1以上を指定してください。"));
            return true;
        }

        Economy economy = plugin.getEconomy();
        if (!economy.has(player, amount)) {
            player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&c所持金が不足しています。(必要: " + ChatUtil.formatMoney(amount) + ")"));
            return true;
        }

        String targetName = target.getName();
        plugin.getConfirmationManager().request(player, amount, targetName + " に送金します", () -> {
            Player currentTarget = Bukkit.getPlayerExact(targetName);
            if (currentTarget == null || !currentTarget.isOnline()) {
                player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&c" + targetName + " はオフラインになりました。"));
                return;
            }
            if (!economy.has(player, amount)) {
                player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&c所持金が不足しています。(必要: " + ChatUtil.formatMoney(amount) + ")"));
                return;
            }
            EconomyResponse withdrawResponse = economy.withdrawPlayer(player, amount);
            if (!withdrawResponse.transactionSuccess()) {
                player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&c送金に失敗しました: " + withdrawResponse.errorMessage));
                return;
            }
            EconomyResponse depositResponse = economy.depositPlayer(currentTarget, amount);
            if (!depositResponse.transactionSuccess()) {
                // 入金に失敗した場合は引き落とし分を払い戻し、お金が消えないようにする。
                economy.depositPlayer(player, amount);
                player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&c送金に失敗したため取り消しました: " + depositResponse.errorMessage));
                return;
            }
            player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&a" + targetName + " に " + ChatUtil.formatMoney(amount) + " を送金しました。"));
            currentTarget.sendMessage(ChatUtil.color(plugin.getPrefix() + "&a" + player.getName() + " から " + ChatUtil.formatMoney(amount) + " を受け取りました。"));
        });
        return true;
    }
}
