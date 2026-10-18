package velodicord.commands;

import com.velocitypowered.api.command.RawCommand;
import com.velocitypowered.api.proxy.Player;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import velodicord.Discordbot;
import velodicord.link.LinkManager;

/** マイクラ側から連携を解除するコマンド。Discordを開けない状況でも解除できるようにする。 */
public class UnlinkCommand implements RawCommand {
    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(Component.text("プレイヤーしかこのコマンドを使えません", NamedTextColor.RED));
            return;
        }

        LinkManager.unlinkMinecraft(player.getUniqueId()).ifPresentOrElse(discordUserId -> {
            Role role = Discordbot.getLinkedRole();
            if (role != null) {
                role.getGuild().removeRoleFromMember(UserSnowflake.fromId(discordUserId), role).queue(null, error -> {
                });
            }
            player.sendMessage(Component.text("Discordとの連携を解除しました", NamedTextColor.GREEN));
        }, () -> player.sendMessage(Component.text("このアカウントはDiscordと連携されていません", NamedTextColor.RED)));
    }
}
