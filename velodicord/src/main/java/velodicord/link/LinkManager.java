package velodicord.link;

import velodicord.Config;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * DiscordアカウントとMinecraftアカウントの連携を仲介する。マイクラ側の/linkで発行された
 * 一時コード(pending, メモリ上のみ・数分で失効)をDiscord側のモーダルで入力してもらい、
 * 一致すればConfigのlink(discordユーザーID -> マイクラUUID)に書き込んで即座にlink.jsonへ保存する。
 * linkマップはJDAのスレッドとVelocityのコマンドスレッドの両方から触られるため、
 * 読み書きはすべてこのクラスの同期メソッド経由で行う。
 */
public final class LinkManager {

    public record PendingLink(UUID uuid, String name, long expiresAtMillis) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAtMillis;
        }
    }

    public enum Result { SUCCESS, INVALID_CODE, LOCKED_OUT, DISCORD_ALREADY_LINKED, MINECRAFT_ALREADY_LINKED }

    public record Completion(Result result, PendingLink link) {
    }

    private static final long CODE_TTL_MILLIS = 5 * 60 * 1000L;
    private static final int MAX_FAILURES = 5;
    private static final long FAILURE_WINDOW_MILLIS = 10 * 60 * 1000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Map<String, PendingLink> pending = new HashMap<>();
    private static final Map<String, long[]> failures = new HashMap<>();

    private LinkManager() {
    }

    /** マイクラ側の/linkで呼ばれる。そのプレイヤーの古いコードは無効にして、新しい6桁コードを1つだけ発行する。 */
    public static synchronized String issueCode(UUID uuid, String name) {
        pending.values().removeIf(entry -> entry.isExpired() || entry.uuid().equals(uuid));
        String code;
        do {
            code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        } while (pending.containsKey(code));
        pending.put(code, new PendingLink(uuid, name, System.currentTimeMillis() + CODE_TTL_MILLIS));
        return code;
    }

    /** Discord側のモーダル送信時に呼ばれる。総当たり対策として、短時間に何度も外したユーザーは一時的に受け付けない。 */
    public static synchronized Completion complete(String code, String discordUserId) {
        if (isLockedOut(discordUserId)) {
            return new Completion(Result.LOCKED_OUT, null);
        }
        if (Config.getLink().containsKey(discordUserId)) {
            return new Completion(Result.DISCORD_ALREADY_LINKED, null);
        }
        PendingLink entry = pending.get(code);
        if (entry == null || entry.isExpired()) {
            if (entry != null) pending.remove(code);
            recordFailure(discordUserId);
            return new Completion(Result.INVALID_CODE, null);
        }
        pending.remove(code);
        if (Config.getLink().containsValue(entry.uuid().toString())) {
            return new Completion(Result.MINECRAFT_ALREADY_LINKED, null);
        }
        failures.remove(discordUserId);
        Config.getLink().put(discordUserId, entry.uuid().toString());
        Config.saveLink();
        return new Completion(Result.SUCCESS, entry);
    }

    /** Discord側の/unlink用。解除できたらtrue。 */
    public static synchronized boolean unlinkDiscord(String discordUserId) {
        if (Config.getLink().remove(discordUserId) == null) return false;
        Config.saveLink();
        return true;
    }

    /** マイクラ側の/unlink用。解除できたら、紐づいていたDiscordユーザーIDを返す(ロール剥奪に使う)。 */
    public static synchronized Optional<String> unlinkMinecraft(UUID uuid) {
        String target = uuid.toString();
        Iterator<Map.Entry<String, String>> it = Config.getLink().entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> entry = it.next();
            if (target.equals(entry.getValue())) {
                it.remove();
                Config.saveLink();
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    public static synchronized Optional<UUID> linkedUuid(String discordUserId) {
        String uuid = Config.getLink().get(discordUserId);
        return uuid == null ? Optional.empty() : Optional.of(UUID.fromString(uuid));
    }

    public static synchronized boolean isDiscordLinked(String discordUserId) {
        return Config.getLink().containsKey(discordUserId);
    }

    public static synchronized boolean isMinecraftLinked(UUID uuid) {
        return Config.getLink().containsValue(uuid.toString());
    }

    private static boolean isLockedOut(String discordUserId) {
        long[] state = failures.get(discordUserId);
        if (state == null) return false;
        if (System.currentTimeMillis() - state[1] > FAILURE_WINDOW_MILLIS) {
            failures.remove(discordUserId);
            return false;
        }
        return state[0] >= MAX_FAILURES;
    }

    private static void recordFailure(String discordUserId) {
        long now = System.currentTimeMillis();
        long[] state = failures.get(discordUserId);
        if (state == null || now - state[1] > FAILURE_WINDOW_MILLIS) {
            failures.put(discordUserId, new long[]{1, now});
        } else {
            state[0]++;
        }
    }
}
