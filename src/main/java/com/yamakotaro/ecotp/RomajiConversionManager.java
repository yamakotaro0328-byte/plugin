package com.yamakotaro.ecotp;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * /roma でオン/オフする、プレイヤーごとのローマ字→日本語自動変換の設定。
 * あくまで軽い切り替えなので永続化はしない (サーバー再起動・リロードでリセットされ、
 * デフォルトはオフ)。
 */
public class RomajiConversionManager {

    private final Set<UUID> enabled = new HashSet<>();

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
