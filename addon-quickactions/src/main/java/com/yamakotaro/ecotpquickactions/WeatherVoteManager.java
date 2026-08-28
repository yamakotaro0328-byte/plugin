package com.yamakotaro.ecotpquickactions;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ワールド単位・天気の種類(clear/rain)単位で投票を管理する。
 * 賛成が必要数に達した時点で即座に天気を変更し、制限時間内に達しなければ不成立にする。
 */
public class WeatherVoteManager {

    public enum Weather {
        CLEAR,
        RAIN;

        public String configKey() {
            return name().toLowerCase();
        }
    }

    private record SessionKey(UUID worldId, Weather weather) {
    }

    private static final class Session {
        final Set<UUID> voters = new HashSet<>();
        BukkitTask timeoutTask;
    }

    private final EcoTpQuickActionsPlugin plugin;
    private final Messages messages;
    private final Map<SessionKey, Session> sessions = new HashMap<>();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();

    public WeatherVoteManager(EcoTpQuickActionsPlugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    /**
     * @return null なら成功(または投票継続中)。失敗理由のメッセージキーを返す場合はそれを呼び出し側が表示する。
     */
    public void startOrJoin(Player player, Weather weather) {
        long now = System.currentTimeMillis();
        Long until = cooldownUntil.get(player.getUniqueId());
        if (until != null && until > now) {
            player.sendMessage(messages.get("on-cooldown",
                    Map.of("seconds", String.valueOf((until - now + 999) / 1000))));
            return;
        }

        World world = player.getWorld();
        SessionKey key = new SessionKey(world.getUID(), weather);
        Session session = sessions.get(key);
        boolean isNew = session == null;
        if (isNew) {
            session = new Session();
            sessions.put(key, session);
            long durationTicks = plugin.getConfig().getLong("weather-vote.duration-seconds", 60) * 20L;
            Session finalSession = session;
            session.timeoutTask = plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> onTimeout(key, finalSession), durationTicks);
        }

        if (!session.voters.add(player.getUniqueId())) {
            player.sendMessage(messages.get("already-voted", Map.of()));
            return;
        }

        int needed = requiredVotes(world);
        String weatherName = messages.weatherName(weather.configKey());
        Map<String, String> placeholders = Map.of(
                "player", player.getName(),
                "weather", weatherName,
                "votes", String.valueOf(session.voters.size()),
                "needed", String.valueOf(needed));

        for (Player online : world.getPlayers()) {
            online.sendMessage(messages.get(isNew ? "started" : "joined", placeholders));
        }

        if (session.voters.size() >= needed) {
            pass(key, session, world, weather, weatherName);
        }
    }

    private int requiredVotes(World world) {
        double ratio = plugin.getConfig().getDouble("weather-vote.required-ratio", 0.5);
        int online = world.getPlayers().size();
        return Math.max(1, (int) Math.ceil(online * ratio));
    }

    private void pass(SessionKey key, Session session, World world, Weather weather, String weatherName) {
        if (weather == Weather.CLEAR) {
            world.setStorm(false);
            world.setThundering(false);
        } else {
            world.setStorm(true);
        }
        for (Player online : world.getPlayers()) {
            online.sendMessage(messages.get("passed", Map.of("weather", weatherName)));
        }
        endSession(key, session);
    }

    private void onTimeout(SessionKey key, Session session) {
        Session current = sessions.get(key);
        if (current != session) {
            return; // 既に成立して終了済み
        }
        World world = plugin.getServer().getWorld(key.worldId());
        if (world != null) {
            for (Player online : world.getPlayers()) {
                online.sendMessage(messages.get("failed", Map.of("weather", messages.weatherName(key.weather().configKey()))));
            }
        }
        endSession(key, session);
    }

    private void endSession(SessionKey key, Session session) {
        sessions.remove(key);
        if (session.timeoutTask != null) {
            session.timeoutTask.cancel();
        }
        long cooldownMillis = plugin.getConfig().getLong("weather-vote.cooldown-seconds", 120) * 1000L;
        long until = System.currentTimeMillis() + cooldownMillis;
        for (UUID voter : session.voters) {
            cooldownUntil.put(voter, until);
        }
    }
}
