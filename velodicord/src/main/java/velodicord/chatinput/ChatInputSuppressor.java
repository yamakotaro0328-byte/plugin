package velodicord.chatinput;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * バックエンド側のGUIプラグイン(例: EcoTPのChatInputManager)が「次のこのプレイヤーの
 * 発言はGUIへの入力なので、Discord/他サーバーへ中継しないでほしい」と申告してきたプレイヤーを
 * 次の一回だけ除外するための仕組み。 申告はvelodicord.events.minecraft.ChatInputSignal が
 * プラグインメッセージ経由で受け取ってmark()を呼ぶ。
 */
public final class ChatInputSuppressor {

    private static final long TTL_MILLIS = 35_000L;
    private static final Map<UUID, Long> suppressedUntil = new ConcurrentHashMap<>();

    private ChatInputSuppressor() {
    }

    public static void mark(UUID uuid) {
        suppressedUntil.put(uuid, System.currentTimeMillis() + TTL_MILLIS);
    }

    /** 一度だけ消費する。期限切れ・未申告ならfalseを返す。 */
    public static boolean consume(UUID uuid) {
        Long expiresAt = suppressedUntil.remove(uuid);
        return expiresAt != null && expiresAt >= System.currentTimeMillis();
    }
}
