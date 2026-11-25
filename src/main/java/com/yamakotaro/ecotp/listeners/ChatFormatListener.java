package com.yamakotaro.ecotp.listeners;

import com.yamakotaro.ecotp.ChatUtil;
import com.yamakotaro.ecotp.EcoTpPlugin;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.chat.Chat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * config.yml の chat-format で組み立てる、簡易的なチャット整形(接頭辞+プレイヤー名+本文)。
 * LunaChatのような別のチャット装飾プラグインを入れずにこれだけで済ませたい場合向け
 * (デフォルト無効)。接頭辞はVault経由のChatサービス(LuckPerms等が提供)から取得する。
 *
 * event.message() の中身を丸ごと文字列として書き換えるのではなく、Paperの
 * ChatRenderer として接頭辞・プレイヤー名を差し込む方式にしている。これにより
 * ChatLinkListener が message() に付けたクリックイベントは、装飾に巻き込まれて
 * 消えることなくそのまま残る (message部分は render() の中でも同じComponentの
 * インスタンスをそのまま組み込むだけなので、クリック情報を壊さない)。
 */
public class ChatFormatListener implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final EcoTpPlugin plugin;

    public ChatFormatListener(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    // renderer() が受け取る message は実際の配信時点での event.message() の値なので、
    // ここで登録する優先度に関わらず ChatLinkListener が付けたクリックイベントは保持される
    // (renderer は message を丸ごと包み込むだけで、中身を作り直さないため)。
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!plugin.getConfig().getBoolean("chat-format.enabled", false)) {
            return;
        }
        String template = plugin.getConfig().getString("chat-format.format", "{prefix}&f{player}&7: &f{message}");
        String prefix = vaultPrefix(event.getPlayer());
        event.renderer(ChatRenderer.viewerUnaware((source, sourceDisplayName, message) ->
                buildComponent(template, prefix, source.getName(), message)));
    }

    private static Component buildComponent(String template, String prefix, String playerName, Component message) {
        Component result = Component.empty();
        int i = 0;
        while (i < template.length()) {
            int next = nextPlaceholder(template, i);
            if (next < 0) {
                result = result.append(legacyText(template.substring(i)));
                break;
            }
            if (next > i) {
                result = result.append(legacyText(template.substring(i, next)));
            }
            if (template.startsWith("{prefix}", next)) {
                result = result.append(legacyText(prefix));
                i = next + "{prefix}".length();
            } else if (template.startsWith("{player}", next)) {
                result = result.append(Component.text(playerName));
                i = next + "{player}".length();
            } else {
                result = result.append(message);
                i = next + "{message}".length();
            }
        }
        return result;
    }

    private static int nextPlaceholder(String template, int from) {
        int min = -1;
        for (String placeholder : new String[]{"{prefix}", "{player}", "{message}"}) {
            int index = template.indexOf(placeholder, from);
            if (index >= 0 && (min < 0 || index < min)) {
                min = index;
            }
        }
        return min;
    }

    private static Component legacyText(String text) {
        return text.isEmpty() ? Component.empty() : LEGACY.deserialize(ChatUtil.color(text));
    }

    private static String vaultPrefix(Player player) {
        RegisteredServiceProvider<Chat> registration = player.getServer().getServicesManager().getRegistration(Chat.class);
        if (registration == null) {
            return "";
        }
        String prefix = registration.getProvider().getPlayerPrefix(player);
        return prefix != null ? prefix : "";
    }
}
