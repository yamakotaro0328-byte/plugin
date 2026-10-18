package velodicord.events.minecraft;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import velodicord.Discordbot;
import velodicord.Velodicord;

import java.awt.*;
import java.util.concurrent.TimeUnit;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.YELLOW;

public class Disconnect {
    @Subscribe(order = PostOrder.FIRST)
    public void onDisconnect(DisconnectEvent event) {
        Velodicord.getVelodicord().getProxy().getScheduler()
                .buildTask(Velodicord.getVelodicord(), Discordbot::updatePresence)
                .delay(1, TimeUnit.SECONDS)
                .schedule();

        // BAN・ホワイトリスト・ログイン中のキャンセルなど、一度もサーバーに入れなかった人の切断は「退出」として通知しない。
        if (event.getLoginStatus() != DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN) return;

        String playerName = event.getPlayer().getUsername();
        Velodicord.getVelodicord().getProxy().sendMessage(text()
                .append(text("[%s]".formatted(playerName), AQUA))
                .append(text(" が退出しました", YELLOW))
        );
        Discordbot.getNoticeChannel().sendMessageEmbeds(new EmbedBuilder()
                .setTitle("退出しました")
                .setColor(Color.blue)
                .setAuthor(playerName, null, "https://mc-heads.net/avatar/%s.png".formatted(playerName))
                .build()).queue();

        Velodicord.getVelodicord().getProxy().getAllPlayers().forEach(player -> player.getTabList().removeEntry(event.getPlayer().getUniqueId()));
    }
}
