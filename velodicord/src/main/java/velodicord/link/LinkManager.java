package velodicord.link;

import velodicord.Config;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DiscordアカウントとMinecraftアカウントの連携を仲介する。マイクラ側の/linkで発行された
 * 一時コード(pending, メモリ上のみ・数分で失効)をDiscord側のモーダルで入力してもらい、
 * 一致すればConfigのlink(discordユーザーID -> マイクラUUID)に書き込んで永続化する
 * (実際にファイルへ書き出すのは他のConfigの項目と同じくListenerClose、シャットダウン時)。
 */
public final class LinkManager {

    public record PendingLink(UUID uuid, String name, long expiresAtMillis) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAtMillis;
        }
    }

    private static final long CODE_TTL_MILLIS = 5 * 60 * 1000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Map<String, PendingLink> pending = new ConcurrentHashMap<>();

    private LinkManager() {
    }

    /** マイクラ側の/linkで呼ばれる。6桁のコードを発行してpendingに登録する。 */
    public static String issueCode(UUID uuid, String name) {
        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        pending.put(code, new PendingLink(uuid, name, System.currentTimeMillis() + CODE_TTL_MILLIS));
        return code;
    }

    /** Discord側のモーダル送信時に呼ばれる。コードが有効ならlinkに登録して結果を返す。 */
    public static Optional<PendingLink> complete(String code, String discordUserId) {
        PendingLink entry = pending.remove(code);
        if (entry == null || entry.isExpired()) {
            return Optional.empty();
        }
        Config.getLink().put(discordUserId, entry.uuid().toString());
        return Optional.of(entry);
    }

    public static Optional<UUID> linkedUuid(String discordUserId) {
        String uuid = Config.getLink().get(discordUserId);
        return uuid == null ? Optional.empty() : Optional.of(UUID.fromString(uuid));
    }
}
