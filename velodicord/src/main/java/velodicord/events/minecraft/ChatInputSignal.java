package velodicord.events.minecraft;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import velodicord.chatinput.ChatInputSuppressor;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * バックエンド側のGUIプラグイン(例: EcoTPのChatInputManager)から届く
 * 「このプレイヤーの次の発言はGUI入力なので中継しないで」という合図を受け取る。
 */
public class ChatInputSignal {

    public static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from("velodicord:chatinput");

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(CHANNEL) || !(event.getSource() instanceof ServerConnection)) {
            return;
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(event.getData()))) {
            ChatInputSuppressor.mark(UUID.fromString(in.readUTF()));
        } catch (IOException | IllegalArgumentException ignored) {
        }
    }
}
