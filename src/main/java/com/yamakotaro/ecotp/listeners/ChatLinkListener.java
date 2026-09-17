package com.yamakotaro.ecotp.listeners;

import com.yamakotaro.ecotp.EcoTpPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * プレイヤーがチャットに書いたメッセージの中のURLを自動検出し、クリックで開ける
 * (ClickEvent.openUrl) リンクに変換する。config.yml の features.chat-link-detection で
 * 無効化できる (デフォルトは有効)。
 *
 * バニラのクライアントにもURLを自動でクリック可能にする設定があるが、プレイヤー側の
 * 設定に依存する上に見た目(色・下線)を統一できないため、こちらはサーバー側で常に
 * 同じ見た目・挙動になるようリンク化する。メッセージ全体は一旦プレーンテキストとして
 * 展開してから再構築するため、色付きチャット権限を持つプレイヤーが送った元の装飾は
 * (URL以外の部分も含めて) 失われる点に注意。
 */
public class ChatLinkListener implements Listener {

    // http(s)://... または www. から始まる、空白を含まない一続きの文字列をURLとみなす簡易な
    // 検出パターン。文末の句読点・括弧など、URLの一部ではなさそうな記号は末尾から除外する。
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)\\b((?:https?://|www\\.)[-\\w+&@#/%?=~|!:,.;]*[-\\w+&@#/%=~|])");

    private final EcoTpPlugin plugin;

    public ChatLinkListener(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    // 他のチャット装飾プラグイン(接頭辞・チャンネル表示など)より後に実行する。先に実行すると、
    // それらのプラグインがメッセージ全体を作り直した際にこちらが付けたクリック情報ごと
    // 上書きされてしまうことがある。
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!plugin.isFeatureEnabled("chat-link-detection")) {
            return;
        }
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        Matcher matcher = URL_PATTERN.matcher(plain);
        if (!matcher.find()) {
            return;
        }

        Component result = Component.empty();
        int lastEnd = 0;
        matcher.reset();
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                result = result.append(Component.text(plain.substring(lastEnd, matcher.start())));
            }
            String matched = matcher.group(1);
            String target = matched.toLowerCase().startsWith("http") ? matched : "https://" + matched;
            Component link = Component.text(matched)
                    .color(NamedTextColor.AQUA)
                    .decorate(TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.openUrl(target))
                    .hoverEvent(Component.text("Click to open " + target, NamedTextColor.GRAY));
            result = result.append(link);
            lastEnd = matcher.end();
        }
        if (lastEnd < plain.length()) {
            result = result.append(Component.text(plain.substring(lastEnd)));
        }

        event.message(result);
    }
}
