package velodicord.events.discord;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

/** /linkpanelで設置されたパネルの「連携する」ボタンを受け取り、
 * /linkと同じ連携モーダルを開く(ModalInteractionが送信を処理する)。 */
public class LinkPanelButton extends ListenerAdapter {
    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (!"link-panel-open".equals(event.getComponentId())) {
            return;
        }

        event.replyModal(ModalInteraction.buildLinkModal()).queue();
    }
}
