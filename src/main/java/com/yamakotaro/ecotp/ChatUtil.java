package com.yamakotaro.ecotp;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public final class ChatUtil {

    private ChatUtil() {
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public static String formatMoney(double amount) {
        long rounded = Math.round(amount);
        return rounded + "円";
    }

    /**
     * 支払いの確認を求めるクリック可能なチャットメッセージを送る。
     * 統合版 (Bedrock) はクリックできないため、/accept を手入力する案内も添える。
     */
    public static void sendConfirmPrompt(Player player, String prefix, String description, double cost) {
        player.sendMessage(color(prefix + description + " (" + formatMoney(cost) + ")"));

        TextComponent acceptButton = new TextComponent("[承諾する]");
        acceptButton.setColor(net.md_5.bungee.api.ChatColor.GREEN);
        acceptButton.setBold(true);
        acceptButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/accept"));
        acceptButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("クリックして承諾する").create()));

        TextComponent space = new TextComponent("  ");

        TextComponent cancelButton = new TextComponent("[キャンセル]");
        cancelButton.setColor(net.md_5.bungee.api.ChatColor.RED);
        cancelButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/accept cancel"));
        cancelButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("クリックしてキャンセル").create()));

        player.spigot().sendMessage(acceptButton, space, cancelButton);
        player.sendMessage(color("&7※統合版の方は &f/accept &7と入力すると承諾できます (キャンセルは &f/accept cancel&7)"));
    }

    /**
     * テレポートリクエスト (/tpa) の承諾/拒否を求めるクリック可能なメッセージを送る。
     * こちらは /tpaccept, /tpdeny がもともと統合版でも入力可能なコマンドなので案内文のみ添える。
     */
    public static void sendTpaRequestPrompt(Player target, String prefix, String requesterName, double cost) {
        target.sendMessage(color(prefix + "&e" + requesterName + " &fさんからテレポートリクエストが届きました。(相手が " + formatMoney(cost) + " を支払います)"));

        TextComponent acceptButton = new TextComponent("[承諾する]");
        acceptButton.setColor(net.md_5.bungee.api.ChatColor.GREEN);
        acceptButton.setBold(true);
        acceptButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept"));
        acceptButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("クリックして承諾する").create()));

        TextComponent space = new TextComponent("  ");

        TextComponent denyButton = new TextComponent("[拒否する]");
        denyButton.setColor(net.md_5.bungee.api.ChatColor.RED);
        denyButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpdeny"));
        denyButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("クリックして拒否する").create()));

        target.spigot().sendMessage(acceptButton, space, denyButton);
        target.sendMessage(color("&7※統合版の方は &f/tpaccept &7または &f/tpdeny &7と入力してください"));
    }
}
