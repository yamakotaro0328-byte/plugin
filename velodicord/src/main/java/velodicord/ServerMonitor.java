package velodicord;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import velodicord.pmConnection.WebSocketPluginMessageManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.DARK_GREEN;
import static net.kyori.adventure.text.format.NamedTextColor.YELLOW;

/**
 * velodicord-bridgeが入っていないサーバー(ロビー等)は起動・停止を自分から知らせてこないので、
 * 定期的にpingして状態の変化を通知する。ブリッジと一度でも繋がったサーバーはブリッジ側の正確な通知に任せ、
 * ここでは扱わない(二重通知防止)。プロキシ起動直後の最初の結果は「現状把握」なので通知しない。
 */
public final class ServerMonitor {

    private static final long INTERVAL_SECONDS = 30;
    private static final long PING_TIMEOUT_SECONDS = 5;
    private static final long STARTUP_GRACE_SECONDS = 15;

    private static final Map<String, Boolean> lastState = new ConcurrentHashMap<>();

    /** ping監視中のサーバーの最新状態。ブリッジ管理のサーバーやまだ一度も確認していないサーバーはnull。 */
    public static Boolean pingState(String name) {
        return lastState.get(name);
    }

    public void start() {
        Velodicord.getVelodicord().getProxy().getScheduler()
                .buildTask(Velodicord.getVelodicord(), this::check)
                .delay(10, TimeUnit.SECONDS)
                .repeat(INTERVAL_SECONDS, TimeUnit.SECONDS)
                .schedule();
    }

    private void check() {
        for (RegisteredServer server : Velodicord.getVelodicord().getProxy().getAllServers()) {
            String name = server.getServerInfo().getName();
            if (handledByBridge(name)) {
                lastState.remove(name);
                continue;
            }
            server.ping().orTimeout(PING_TIMEOUT_SECONDS, TimeUnit.SECONDS).whenComplete((ping, error) -> {
                boolean up = error == null;
                Boolean previous = lastState.put(name, up);
                if (previous == null || previous == up) return;
                if (up) {
                    // ブリッジ入りのサーバーは起動直後、ブリッジが繋がる前にpingへ応答し始めることがある。
                    // 少し待ってブリッジが繋がっていればそちらの「起動しました」に任せる。
                    Velodicord.getVelodicord().getProxy().getScheduler()
                            .buildTask(Velodicord.getVelodicord(), () -> {
                                if (!handledByBridge(name)) announce(name, true);
                            })
                            .delay(STARTUP_GRACE_SECONDS, TimeUnit.SECONDS)
                            .schedule();
                } else {
                    announce(name, false);
                }
            });
        }
    }

    private static boolean handledByBridge(String name) {
        return Velodicord.getPMManager() instanceof WebSocketPluginMessageManager manager && manager.hasBridgeEverConnected(name);
    }

    private static void announce(String name, boolean up) {
        String emoji = up ? "✅" : "🛑";
        String verb = up ? "が起動しました" : "が停止しました(応答なし)";
        Velodicord.getVelodicord().getProxy().sendMessage(text()
                .append(text(emoji + " "))
                .append(text("[%s]".formatted(name), DARK_GREEN))
                .append(text(" " + verb, YELLOW)));
        try {
            Discordbot.getNoticeChannel().sendMessage("%s **%s** %s".formatted(emoji, name, verb)).queue();
        } catch (RuntimeException e) {
            Velodicord.getVelodicord().getLogger().warn("[{}] の状態変化をDiscordに通知できませんでした: {}", name, e.toString());
        }
    }
}
