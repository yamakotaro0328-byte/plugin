package com.yamakotaro.ecotp;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 放置(AFK)状態の管理。/afk で手動切り替え、または afk.auto-seconds の間何も操作しないと
 * 自動でAFKになる。移動・視点移動・チャット・コマンド・クリックのどれかでAFK解除。
 * afk.kick-seconds を0より大きくすると、その時間放置したプレイヤーをキックする
 * (ecotp.afk.kickexempt 権限を持つプレイヤーは対象外)。
 */
public class AfkManager implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final EcoTpPlugin plugin;
    // AsyncChatEvent (非同期スレッド) からも書き込まれるため concurrent にしておく。
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private final Set<UUID> afk = ConcurrentHashMap.newKeySet();

    public AfkManager(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            lastActivity.put(player.getUniqueId(), now);
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L * 10, 20L * 10);
    }

    private boolean enabled() {
        return plugin.isFeatureEnabled("afk");
    }

    public boolean isAfk(UUID uuid) {
        return afk.contains(uuid);
    }

    /** /afk 用。AFKでなければAFKにし、AFKなら解除する。 */
    public void toggle(Player player) {
        setAfk(player, !isAfk(player.getUniqueId()));
    }

    private void tick() {
        if (!enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        long autoMillis = plugin.getConfig().getLong("afk.auto-seconds", 300) * 1000L;
        long kickMillis = plugin.getConfig().getLong("afk.kick-seconds", 0) * 1000L;
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            long idle = now - lastActivity.getOrDefault(uuid, now);
            if (kickMillis > 0 && idle >= kickMillis && !player.hasPermission("ecotp.afk.kickexempt")) {
                player.kick(Component.text(plugin.getMessages().get("afk.kick-reason")));
                continue;
            }
            if (autoMillis > 0 && idle >= autoMillis && !isAfk(uuid)) {
                setAfk(player, true);
            }
        }
    }

    /** メインスレッドから呼ぶこと (タブリスト名の変更を伴うため)。 */
    private void setAfk(Player player, boolean value) {
        UUID uuid = player.getUniqueId();
        if (value == isAfk(uuid)) {
            return;
        }
        if (value) {
            afk.add(uuid);
            if (plugin.getConfig().getBoolean("afk.tablist-tag", true)) {
                player.playerListName(LEGACY.deserialize(plugin.getMessages().get("afk.tag")).append(player.displayName()));
            }
            Bukkit.broadcastMessage(plugin.getMessages().get("afk.now-afk", "player", player.getName()));
        } else {
            afk.remove(uuid);
            lastActivity.put(uuid, System.currentTimeMillis());
            if (plugin.getConfig().getBoolean("afk.tablist-tag", true)) {
                player.playerListName(null);
            }
            Bukkit.broadcastMessage(plugin.getMessages().get("afk.no-longer-afk", "player", player.getName()));
        }
    }

    private void markActive(Player player) {
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
        if (isAfk(player.getUniqueId())) {
            setAfk(player, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.hasChangedPosition() || event.hasChangedOrientation()) {
            markActive(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
        if (isAfk(player.getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    setAfk(player, false);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage().toLowerCase();
        // /afk 自体は「操作」に数えない (数えると /afk した直後に解除されてしまう)。
        if (message.equals("/afk") || message.startsWith("/afk ") || message.startsWith("/ecotp:afk")) {
            return;
        }
        markActive(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        markActive(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        lastActivity.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastActivity.remove(uuid);
        afk.remove(uuid);
    }
}
