package velodicord.events.discord;

import com.github.ucchyocean.lc3.japanize.Japanizer;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import velodicord.ChatFormat;
import velodicord.Config;
import velodicord.Velodicord;
import velodicord.pmConnection.DiscordPluginMessageManager;
import velodicord.pmConnection.PluginMessageManager;

import javax.annotation.Nonnull;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static velodicord.Discordbot.*;

public class MessageReceived extends ListenerAdapter {
    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        if (!(event.getAuthor().isBot() && !Config.getDetectbot().contains(event.getAuthor().getId())) && event.getChannel().getId().equals(getMainChannel().getId())) {
            String message = event.getMessage().getContentDisplay();
            String japanese = Japanizer.japanize(message);

            TextComponent.Builder line = text()
                    .append(text("[discord]", DARK_GREEN))
                    .append(text("<%s> ".formatted(event.getAuthor().getName())))
                    .append(MiniMessage.miniMessage().deserialize(ChatFormat.toMiniMessage(message)));
            if (!japanese.isEmpty() && !message.contains("https://") && !message.contains("http://") && !message.contains("```")) {
                line.append(text("(%s)".formatted(japanese), GOLD));
            }
            for (Message.Attachment attachment : event.getMessage().getAttachments()) {
                line.append(text(" [添付: %s]".formatted(attachment.getFileName()), BLUE)
                        .decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(attachment.getUrl()))
                        .hoverEvent(HoverEvent.showText(text("クリックで開く"))));
            }
            Velodicord.getVelodicord().getProxy().sendMessage(line.build());
        } else if (Velodicord.getPMManager() instanceof DiscordPluginMessageManager manager && event.getChannel().getId().equals(manager.getPMChannel().getId())) {
            PluginMessageManager.receive(event.getMessage().getContentDisplay());
        }
    }
}
