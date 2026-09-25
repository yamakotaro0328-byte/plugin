package com.yamakotaro.ecotp;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /roma でオン/オフする、プレイヤーごとのローマ字→日本語自動変換の設定。
 * あくまで軽い切り替えなので永続化はしない (サーバー再起動・リロードでリセットされ、
 * デフォルトはオフ)。
 */
public class RomajiConversionManager {

    // /roma (メインスレッド) と AsyncChatEvent (非同期スレッド) の両方から触られる。
    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();

    public boolean isEnabled(UUID uuid) {
        return enabled.contains(uuid);
    }

    /** @return 切り替えた後の状態 (true = 有効になった)。 */
    public boolean toggle(UUID uuid) {
        if (enabled.remove(uuid)) {
            return false;
        }
        enabled.add(uuid);
        return true;
    }
}
