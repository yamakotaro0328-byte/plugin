package com.yamakotaro.ecotp;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * GUIから「チャットに入力してください」という一往復のやり取りを実現するための仕組み。
 * 例: 送金するプレイヤーをGUIで選んだ後、金額だけチャットで入力してもらう。
 * AsyncPlayerChatEvent はメインスレッド以外で発火するため、request()/cancelSilently()
 * (メインスレッド) と onChat() (チャットスレッド) から同時にこのマップへアクセスされ得る。
 * そのため通常の HashMap ではなく ConcurrentHashMap を使う。
 */
public class ChatInputManager implements Listener {

    /** Velodicord(Velocityプロキシ)に「次のこのプレイヤーの発言はGUI入力なので
     * Discord/他サーバーへ中継しないで」と伝えるためのプラグインメッセージチャンネル。
     * Velodicordが導入されていないサーバーではこの送信は単に無視されるだけなので、
     * 常時送って問題ない。 */
    public static final String SUPPRESS_CHANNEL = "velodicord:chatinput";

    private final EcoTpPlugin plugin;
    private final Map<UUID, PendingInput> pending = new ConcurrentHashMap<>();

    public ChatInputManager(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    private static final class PendingInput {
        final Consumer<String> onInput;
        final BukkitTask timeoutTask;

        private PendingInput(Consumer<String> onInput, BukkitTask timeoutTask) {
            this.onInput = onInput;
            this.timeoutTask = timeoutTask;
        }
    }

    /**
     * 次にこのプレイヤーが送信するチャットメッセージを横取りして onInput に渡す。
     * "cancel" と入力された場合は onInput を呼ばずにキャンセル扱いにする。
     */
    public void request(Player player, Consumer<String> onInput) {
        UUID uuid = player.getUniqueId();
        cancelSilently(uuid);
        sendSuppressSignal(player);

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pending.remove(uuid);
            if (player.isOnline()) {
                player.sendMessage(plugin.msg("menu.chat-input-timeout"));
            }
        }, 30L * 20L);

        pending.put(uuid, new PendingInput(onInput, task));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        PendingInput input = pending.get(uuid);
        if (input == null) {
            return;
        }
        event.setCancelled(true);
        pending.remove(uuid);
        input.timeoutTask.cancel();

        String message = event.getMessage();
        Player player = event.getPlayer();
        // AsyncPlayerChatEvent は非同期で発火するため、Bukkit API を触る処理はメインスレッドへ戻す。
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage(plugin.msg("menu.chat-input-cancelled"));
                return;
            }
            input.onInput.accept(message);
        });
    }

    public void cancelSilently(UUID uuid) {
        PendingInput input = pending.remove(uuid);
        if (input != null) {
            input.timeoutTask.cancel();
        }
    }

    private void sendSuppressSignal(Player player) {
        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
        try (DataOutputStream payload = new DataOutputStream(payloadBytes)) {
            payload.writeUTF(player.getUniqueId().toString());
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to build a chat-input suppress signal", e);
            return;
        }
        player.sendPluginMessage(plugin, SUPPRESS_CHANNEL, payloadBytes.toByteArray());
    }
}
