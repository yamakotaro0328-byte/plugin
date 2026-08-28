package com.yamakotaro.ecotp;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public final class ChatUtil {

    private static Messages messages;

    private ChatUtil() {
    }

    /**
     * EcoTpPlugin#onEnable から一度だけ呼ばれる。
     * formatMoney を static のまま各コマンドから呼べるようにするための橋渡し。
     */
    public static void init(Messages messagesInstance) {
        messages = messagesInstance;
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public static String formatMoney(double amount) {
        if (messages != null) {
            return messages.formatMoney(amount);
        }
        return Math.round(amount) + " coins";
    }

    /**
     * 支払いの確認を求めるクリック可能なチャットメッセージを送る。
     * 統合版 (Bedrock) はクリックできないため、/accept を手入力する案内も添える。
     */
    public static void sendConfirmPrompt(Player player, String prefix, String description, double cost) {
        player.sendMessage(color(prefix + description + " (" + formatMoney(cost) + ")"));

        TextComponent acceptButton = new TextComponent(messages.get("confirm.accept-button"));
        acceptButton.setColor(net.md_5.bungee.api.ChatColor.GREEN);
        acceptButton.setBold(true);
        acceptButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/accept"));
        acceptButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(messages.get("confirm.accept-hover")).create()));

        TextComponent space = new TextComponent("  ");

        TextComponent cancelButton = new TextComponent(messages.get("confirm.cancel-button"));
        cancelButton.setColor(net.md_5.bungee.api.ChatColor.RED);
        cancelButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/accept cancel"));
        cancelButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(messages.get("confirm.cancel-hover")).create()));

        player.spigot().sendMessage(acceptButton, space, cancelButton);
        player.sendMessage(messages.get("confirm.bedrock-hint"));
    }

    /**
     * テレポートリクエスト (/tpa) の承諾/拒否を求めるクリック可能なメッセージを送る。
     * こちらは /tpaccept, /tpdeny がもともと統合版でも入力可能なコマンドなので案内文のみ添える。
     */
    public static void sendTpaRequestPrompt(Player target, String prefix, String requesterName, double cost, String incomingMessageKey) {
        target.sendMessage(messages.get(incomingMessageKey, "player", requesterName, "cost", formatMoney(cost)));

        TextComponent acceptButton = new TextComponent(messages.get("confirm.accept-button"));
        acceptButton.setColor(net.md_5.bungee.api.ChatColor.GREEN);
        acceptButton.setBold(true);
        acceptButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept"));
        acceptButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(messages.get("tpa.incoming-accept-hover")).create()));

        TextComponent space = new TextComponent("  ");

        TextComponent denyButton = new TextComponent(messages.get("confirm.cancel-button"));
        denyButton.setColor(net.md_5.bungee.api.ChatColor.RED);
        denyButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpdeny"));
        denyButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(messages.get("tpa.incoming-deny-hover")).create()));

        target.spigot().sendMessage(acceptButton, space, denyButton);
        target.sendMessage(messages.get("tpa.bedrock-hint"));
    }
}
