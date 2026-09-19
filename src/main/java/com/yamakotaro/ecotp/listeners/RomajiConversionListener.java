package com.yamakotaro.ecotp.listeners;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.yamakotaro.ecotp.EcoTpPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.logging.Level;

/**
 * /roma でオンにしたプレイヤーのチャットメッセージを、ローマ字から日本語(漢字混じり)へ
 * 自動変換する (config.yml の features.romaji-conversion で機能自体を無効化できる)。
 *
 * Google公式には文書化されていない内部エンドポイント (inputtools.google.com、
 * 「Google 日本語入力」等のWeb版が実際に使っているもの) を直接叩く非公式な実装。
 * 登録・APIキーは不要な代わりに、予告なく仕様変更・停止される可能性がある - 失敗しても
 * 例外を握りつぶして元のメッセージのまま送信するだけにし、チャット自体は絶対に止めない。
 *
 * AsyncChatEvent は名前の通りメインスレッド以外で呼ばれるイベントなので、ここで同期的に
 * HTTPリクエストを送って結果を待っても、サーバー全体のTPSには影響しない。
 */
public class RomajiConversionListener implements Listener {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private static final Gson GSON = new Gson();

    private final EcoTpPlugin plugin;

    public RomajiConversionListener(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    // ChatLinkListener・ChatFormatListenerより前に、素のローマ字を日本語へ変換しておく
    // (URLの自動リンク化やチャット整形は変換後の文字列に対して行われるべきため)。
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!plugin.isFeatureEnabled("romaji-conversion")) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("ecotp.romaji") || !plugin.getRomajiConversionManager().isEnabled(player.getUniqueId())) {
            return;
        }
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (!looksLikeRomaji(plain)) {
            return;
        }
        String converted = convert(plain);
        if (converted == null || converted.equals(plain)) {
            return;
        }
        event.message(Component.text(converted));
    }

    /** 既に日本語やURLを含むメッセージは変換対象にしない (ASCII文字だけのメッセージのみ対象)。 */
    private static boolean looksLikeRomaji(String text) {
        if (text.isBlank() || text.contains("://") || text.toLowerCase(java.util.Locale.ROOT).startsWith("www.")) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > 127) {
                return false;
            }
        }
        return true;
    }

    private String convert(String romaji) {
        try {
            String url = "https://inputtools.google.com/request?text="
                    + URLEncoder.encode(romaji, StandardCharsets.UTF_8)
                    + "&itc=ja-t-i0-und&num=1";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            JsonArray root = GSON.fromJson(response.body(), JsonArray.class);
            if (root == null || root.size() < 2 || !"SUCCESS".equals(root.get(0).getAsString())) {
                return null;
            }
            JsonArray segments = root.get(1).getAsJsonArray();
            if (segments.isEmpty()) {
                return null;
            }
            JsonArray first = segments.get(0).getAsJsonArray();
            JsonArray candidates = first.get(1).getAsJsonArray();
            if (candidates.isEmpty()) {
                return null;
            }
            return candidates.get(0).getAsString();
        } catch (IOException e) {
            plugin.getLogger().log(Level.FINE, "Romaji conversion request failed", e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (RuntimeException e) {
            // 想定外のJSON構造 (非公式エンドポイントの仕様が変わった等) - 変換を諦めるだけにする。
            plugin.getLogger().log(Level.FINE, "Romaji conversion response was not in the expected shape", e);
            return null;
        }
    }
}
