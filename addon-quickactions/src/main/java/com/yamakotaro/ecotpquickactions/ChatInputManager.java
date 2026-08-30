package com.yamakotaro.ecotpquickactions;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 次にプレイヤーがチャットで送った1行を、通常のチャット送信としてではなくコールバックへの
 * 入力として受け取るための小さな仕組み(価格入力など)。EcoTP本体のChatInputManagerと同じ
 * パターンだが、このアドオンはEcoTPのクラスを参照しないため独自に実装している。
 */
public class ChatInputManager implements Listener {

    private final Plugin plugin;
    private final Map<UUID, Consumer<String>> pending = new ConcurrentHashMap<>();

    public ChatInputManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void request(Player player, Consumer<String> callback) {
        pending.put(player.getUniqueId(), callback);
    }

    // LOWEST so this runs before any chat-formatting plugin (LunaChat, DiscordSRV, etc.) can
    // rewrite event.message() into a fully-formatted line; ignoreCancelled so a message another
    // plugin already blocked (e.g. a mute) isn't misread as the expected input either.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Consumer<String> callback = pending.remove(event.getPlayer().getUniqueId());
        if (callback == null) {
            return;
        }
        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        // AsyncChatEventは非同期スレッドで発火するため、Bukkit APIに触れる処理はメインスレッドに戻す。
        Bukkit.getScheduler().runTask(plugin, () -> callback.accept(message));
    }
}
