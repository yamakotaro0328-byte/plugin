package velodicord;

import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.kyori.adventure.audience.MessageType;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.awt.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Discordの/server commandからVelocityのコマンドを実行するときの実行者。コンソール扱い(全権限)なので、
 * SlashCommandInteraction側でコマンドロール持ちだけに制限している。
 * 出力は最後の出力から少し待ってまとめ、Discordの文字数制限に収まるよう分割して送る。
 */
public class DiscordCommandSource implements ConsoleCommandSource {

    private static final int CHUNK = 1900;
    private static final ScheduledExecutorService FLUSHER = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "velodicord-command-output");
        thread.setDaemon(true);
        return thread;
    });

    private final SlashCommandInteractionEvent event;
    private final StringBuilder output = new StringBuilder();
    private ScheduledFuture<?> pendingFlush;

    public DiscordCommandSource(SlashCommandInteractionEvent event) {
        this.event = event;
        if (event != null) event.replyEmbeds(new EmbedBuilder()
                .setColor(Color.yellow)
                .setTitle("実行しています...")
                .build()
        ).queue();
    }

    @Override
    public Tristate getPermissionValue(String s) {
        return Tristate.TRUE;
    }

    @Override
    public synchronized void sendMessage(@NonNull Identity identity, @NonNull Component message) {
        if (event == null) return;
        output.append(PlainTextComponentSerializer.plainText().serialize(message)).append('\n');
        if (pendingFlush != null) pendingFlush.cancel(false);
        pendingFlush = FLUSHER.schedule(this::flush, 300, TimeUnit.MILLISECONDS);
    }

    @Override
    public void sendMessage(@NonNull Identity identity, @NonNull Component message, @NonNull MessageType type) {
        sendMessage(identity, message);
    }

    private synchronized void flush() {
        String text = output.toString().replace("```", "`​``");
        output.setLength(0);
        for (int start = 0; start < text.length(); start += CHUNK) {
            String chunk = text.substring(start, Math.min(text.length(), start + CHUNK));
            event.getChannel().sendMessage("```\n" + chunk + "\n```").queue();
        }
    }
}
