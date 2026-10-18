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

        var value = event.getValue("code");
        String code = value == null ? "" : value.getAsString().trim();
        LinkManager.Completion completion = LinkManager.complete(code, event.getUser().getId());

        switch (completion.result()) {
            case SUCCESS -> {
                LinkManager.PendingLink link = completion.link();
                Member member = event.getMember();
                if (Discordbot.getLinkedRole() != null && member != null) {
                    event.getGuild().addRoleToMember(member, Discordbot.getLinkedRole()).queue();
                }
                reply(event, Color.green, "連携が完了しました", "%sとして連携しました".formatted(link.name()));
                Velodicord.getVelodicord().getProxy().getPlayer(link.uuid()).ifPresent(player -> player.sendMessage(
                        Component.text("Discordアカウント(%s)との連携が完了しました".formatted(event.getUser().getName()), NamedTextColor.GREEN)
                ));
            }
            case DISCORD_ALREADY_LINKED ->
                    reply(event, Color.orange, "既に連携済みです", "連携し直す場合は先に /unlink を実行してください");
            case MINECRAFT_ALREADY_LINKED ->
                    reply(event, Color.orange, "そのマイクラアカウントは既に連携済みです", "別のDiscordアカウントと連携されています");
            case LOCKED_OUT ->
                    reply(event, Color.red, "試行回数が多すぎます", "しばらく(最大10分)待ってから、もう一度お試しください");
            case INVALID_CODE ->
                    reply(event, Color.red, "コードが無効です", "コードが間違っているか、期限切れです。マイクラで /link を再実行してください");
        }
    }

    private static void reply(ModalInteractionEvent event, Color color, String title, String description) {
        event.replyEmbeds(new EmbedBuilder()
                .setColor(color)
                .setTitle(title)
                .setDescription(description)
                .build()
        ).setEphemeral(true).queue();
    }
}
