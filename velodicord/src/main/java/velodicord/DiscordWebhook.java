package velodicord;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * マイクラのチャットをプレイヤー名・スキン付きでDiscordに投稿する。1本の送信スレッドで順番に送るので
 * 発言順が入れ替わらず、Discordのレート制限(429)を受けたら指定秒数待って再送する。
 */
public final class DiscordWebhook {

    private static final int DISCORD_CONTENT_LIMIT = 2000;
    private static final int MAX_ATTEMPTS = 4;
    private static final MediaType JSON = MediaType.get("application/json");
    private static final OkHttpClient httpClient = new OkHttpClient();
    private static final ExecutorService sender = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "velodicord-webhook");
        thread.setDaemon(true);
        return thread;
    });

    private DiscordWebhook() {
    }

    public static void send(String username, String content) {
        if (content.length() > DISCORD_CONTENT_LIMIT) {
            content = content.substring(0, DISCORD_CONTENT_LIMIT - 1) + "…";
        }
        JsonObject body = new JsonObject();
        body.addProperty("content", content);
        body.addProperty("username", username);
        body.addProperty("avatar_url", "https://mc-heads.net/avatar/%s.png".formatted(username));
        JsonObject allowedMentions = new JsonObject();
        allowedMentions.add("parse", new Gson().toJsonTree(Discordbot.getMentionable()).getAsJsonArray());
        body.add("allowed_mentions", allowedMentions);
        String json = body.toString();

        sender.submit(() -> post(json));
    }

    private static void post(String json) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Request request = new Request.Builder()
                    .url(Discordbot.getWebhook().getUrl())
                    .post(RequestBody.create(JSON, json))
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) return;
                if (response.code() == 429) {
                    Thread.sleep(retryAfterMillis(response));
                    continue;
                }
                String detail = response.body() == null ? "" : response.body().string();
                Velodicord.getVelodicord().getLogger().warn("Discordへのチャット送信に失敗しました (HTTP {}): {}", response.code(), detail);
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                if (attempt == MAX_ATTEMPTS) {
                    Velodicord.getVelodicord().getLogger().warn("Discordへのチャット送信に失敗しました: {}", e.toString());
                    return;
                }
                try {
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        Velodicord.getVelodicord().getLogger().warn("Discordのレート制限が続いたため、チャットを1件送信できませんでした");
    }

    private static long retryAfterMillis(Response response) {
        String header = response.header("Retry-After");
        try {
            return header == null ? 1000L : Math.max(100L, (long) (Double.parseDouble(header) * 1000));
        } catch (NumberFormatException e) {
            return 1000L;
        }
    }
}
