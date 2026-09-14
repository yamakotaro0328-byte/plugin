package velodicord.commands;

import com.velocitypowered.api.command.RawCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import velodicord.link.LinkManager;

import java.awt.*;

/** マイクラ側から実行する連携コマンド。コードを発行してプレイヤーに通知するだけで、
 * 実際の連携完了はDiscord側の/linkモーダルでコードを入力した時点(ModalInteraction参照)。 */
public class LinkCommand implements RawCommand {
    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(Component.text("プレイヤーしかこのコマンドを使えません").color(TextColor.color(Color.RED.getRGB())));
            return;
        }

        String code = LinkManager.issueCode(player.getUniqueId(), player.getUsername());
        player.sendMessage(Component.text("連携コード: %s".formatted(code)).color(TextColor.color(Color.CYAN.getRGB())));
        player.sendMessage(Component.text("Discordで /link を実行し、表示されたコードを入力してください(5分間有効)").color(TextColor.color(Color.CYAN.getRGB())));
    }
}
