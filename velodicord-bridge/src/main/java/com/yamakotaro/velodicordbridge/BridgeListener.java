package com.yamakotaro.velodicordbridge;

import io.papermc.paper.advancement.AdvancementDisplay;
import io.papermc.paper.advancement.AdvancementDisplayType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.advancement.Advancement;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

/** 死亡・実績達成をNOTICE(Discord向けプレーンテキスト)とSEND(マイクラの他サーバーへのチャット中継)で
 * Velodicordに送る。参加/退出はVelocityが直接検知して処理するため、ここでは扱わない。 */
public class BridgeListener implements Listener {

    private final BridgeServer server;
    private final String serverName;

    public BridgeListener(BridgeServer server, String serverName) {
        this.server = server;
        this.serverName = serverName;
    }

    private static String plain(Component component) {
        return component == null ? "" : PlainTextComponentSerializer.plainText().serialize(component);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        String playerName = event.getEntity().getName();
        String message = plain(event.deathMessage());
        var location = event.getEntity().getLocation();
        String place = "%s:(%d, %d, %d)".formatted(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());

        server.sendMessage("NOTICE&☠️ **%s** が %s で死亡しました\n%s".formatted(playerName, place, message));
        server.sendMessage("SEND&<red><dark_green>[%s]</dark_green> <aqua>%s</aqua> が死亡しました".formatted(serverName, playerName));
    }

    @EventHandler
    public void onPlayerAdvancement(PlayerAdvancementDoneEvent event) {
        Advancement advancement = event.getAdvancement();
        AdvancementDisplay display = advancement.getDisplay();
        if (display == null || !display.shouldAnnounceChat()) return;

        String playerName = event.getPlayer().getName();
        String title = plain(display.title());
        String description = plain(display.description());
        AdvancementDisplayType type = display.getType();
        boolean isChallenge = type == AdvancementDisplayType.CHALLENGE;
        String frameName = type == AdvancementDisplayType.TASK ? "進捗" : type == AdvancementDisplayType.GOAL ? "目標" : "挑戦";
        String completionWord = isChallenge ? "完了" : "達成";
        String color = isChallenge ? "dark_purple" : "green";

        server.sendMessage("NOTICE&🏆 **%s** が%s [%s] を%sしました\n%s".formatted(playerName, frameName, title, completionWord, description));
        server.sendMessage("SEND&<yellow><dark_green>[%s]</dark_green> <aqua>%s</aqua> が%s <%s>[%s]</%s> を%sしました".formatted(serverName, playerName, frameName, color, title, color, completionWord));
    }
}
