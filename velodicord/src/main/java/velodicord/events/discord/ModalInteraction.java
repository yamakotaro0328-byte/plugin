package velodicord.events.discord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import velodicord.Discordbot;
import velodicord.link.LinkManager;

import java.awt.*;
import java.util.Optional;

/** /linkモーダル(SlashCommandInteractionのcase "link"参照)の送信を受け取り、
 * マイクラ側で発行されたコードと照合してアカウント連携を完了させる。連携が完了したら
 * config.jsonのLinkedRoleIDが設定されていればそのロールを付与する。 */
public class ModalInteraction extends ListenerAdapter {
    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        if (!"link-modal".equals(event.getModalId())) {
            return;
        }

        var value = event.getValue("code");
        String code = value == null ? "" : value.getAsString().trim();
        Optional<LinkManager.PendingLink> result = LinkManager.complete(code, event.getUser().getId());

        if (result.isPresent()) {
            Member member = event.getMember();
            if (Discordbot.getLinkedRole() != null && member != null) {
                event.getGuild().addRoleToMember(member, Discordbot.getLinkedRole()).queue();
            }
            event.replyEmbeds(new EmbedBuilder()
                    .setColor(Color.green)
                    .setTitle("連携が完了しました")
                    .setDescription("%sとして連携しました".formatted(result.get().name()))
                    .build()
            ).setEphemeral(true).queue();
        } else {
            event.replyEmbeds(new EmbedBuilder()
                    .setColor(Color.red)
                    .setTitle("コードが無効です")
                    .setDescription("コードが間違っているか、期限切れです。マイクラで /link を再実行してください")
                    .build()
            ).setEphemeral(true).queue();
        }
    }
}
