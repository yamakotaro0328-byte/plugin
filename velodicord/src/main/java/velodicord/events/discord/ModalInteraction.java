package velodicord.events.discord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.modals.Modal;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.NotNull;
import velodicord.Discordbot;
import velodicord.Velodicord;
import velodicord.link.LinkManager;

import java.awt.*;
import java.util.Optional;

/** /linkモーダル(SlashCommandInteractionのcase "link"、LinkPanelButtonのボタンから開かれる)の送信を受け取り、
 * マイクラ側で発行されたコードと照合してアカウント連携を完了させる。連携が完了したら
 * config.jsonのLinkedRoleIDが設定されていればそのロールを付与する。 */
public class ModalInteraction extends ListenerAdapter {

    public static Modal buildLinkModal() {
        TextInput codeInput = TextInput.create("code", TextInputStyle.SHORT)
                .setPlaceholder("123456")
                .setMinLength(6)
                .setMaxLength(6)
                .setRequired(true)
                .build();
        return Modal.create("link-modal", "アカウント連携")
                .addComponents(Label.of("マイクラで発行されたコード", codeInput))
                .build();
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        if (!"link-modal".equals(event.getModalId())) {
            return;
        }

        if (LinkManager.isDiscordLinked(event.getUser().getId())) {
            event.replyEmbeds(new EmbedBuilder()
                    .setColor(Color.orange)
                    .setTitle("既に連携済みです")
                    .setDescription("連携し直す場合は先に /unlink を実行してください")
                    .build()
            ).setEphemeral(true).queue();
            return;
        }

        var value = event.getValue("code");
        String code = value == null ? "" : value.getAsString().trim();
        Optional<LinkManager.PendingLink> result = LinkManager.complete(code, event.getUser().getId());

        if (result.isPresent()) {
            LinkManager.PendingLink link = result.get();
            Member member = event.getMember();
            if (Discordbot.getLinkedRole() != null && member != null) {
                event.getGuild().addRoleToMember(member, Discordbot.getLinkedRole()).queue();
            }
            event.replyEmbeds(new EmbedBuilder()
                    .setColor(Color.green)
                    .setTitle("連携が完了しました")
                    .setDescription("%sとして連携しました".formatted(link.name()))
                    .build()
            ).setEphemeral(true).queue();

            Velodicord.getVelodicord().getProxy().getPlayer(link.uuid()).ifPresent(player -> player.sendMessage(
                    Component.text("Discordアカウント(%s)との連携が完了しました".formatted(event.getUser().getName()), NamedTextColor.GREEN)
            ));
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
