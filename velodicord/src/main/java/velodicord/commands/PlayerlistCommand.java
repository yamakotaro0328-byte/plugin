package velodicord.commands;

import com.velocitypowered.api.command.RawCommand;
import net.kyori.adventure.text.TextComponent;
import velodicord.Velodicord;

import static net.kyori.adventure.text.Component.newline;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.DARK_GREEN;
import static net.kyori.adventure.text.format.NamedTextColor.YELLOW;

/** 実行した人にだけ一覧を返す(以前はプロキシ全員にブロードキャストしていた)。 */
public class PlayerlistCommand implements RawCommand {
    @Override
    public void execute(Invocation invocation) {
        var players = Velodicord.getVelodicord().getProxy().getAllPlayers();
        TextComponent.Builder list = text().append(text("オンライン: %d人".formatted(players.size()), YELLOW));
        players.forEach(player -> list
                .append(newline())
                .append(text("・"))
                .append(text("[%s]".formatted(player.getCurrentServer().map(server -> server.getServerInfo().getName()).orElse("接続中")), DARK_GREEN))
                .append(text(player.getUsername())));
        invocation.source().sendMessage(list.build());
    }
}
