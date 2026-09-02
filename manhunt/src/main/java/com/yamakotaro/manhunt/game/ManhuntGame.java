package com.yamakotaro.manhunt.game;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Holds the single, server-wide Manhunt game's state. There's only ever one game at a time -
 * Manhunt is a whole-server event, not something split across arenas like EcoRail/SulfurSoccer.
 */
public class ManhuntGame {

    private final Map<UUID, Role> roles = new HashMap<>();
    private final Set<UUID> eliminatedRunners = new HashSet<>();
    private boolean running = false;
    private long headStartEndMillis = 0;

    public boolean isRunning() {
        return running;
    }

    public boolean isHeadStartActive() {
        return running && System.currentTimeMillis() < headStartEndMillis;
    }

    public Role getRole(UUID playerId) {
        return roles.get(playerId);
    }

    public Map<UUID, Role> getRoles() {
        return roles;
    }

    public String setRole(UUID playerId, Role role) {
        if (running) {
            return "manhunt.role-locked";
        }
        roles.put(playerId, role);
        return null;
    }

    public void removePlayer(UUID playerId) {
        roles.remove(playerId);
        eliminatedRunners.remove(playerId);
    }

    public String start(long headStartSeconds) {
        if (running) {
            return "manhunt.already-running";
        }
        if (!roles.containsValue(Role.RUNNER) || !roles.containsValue(Role.HUNTER)) {
            return "manhunt.need-both-roles";
        }
        eliminatedRunners.clear();
        running = true;
        headStartEndMillis = System.currentTimeMillis() + (headStartSeconds * 1000);
        return null;
    }

    /** Ends the game. Roles are left as-is, so the same lineup can be restarted with /manhunt start. */
    public void stop() {
        running = false;
        headStartEndMillis = 0;
        eliminatedRunners.clear();
    }

    public void eliminate(UUID playerId) {
        eliminatedRunners.add(playerId);
    }

    public boolean isEliminated(UUID playerId) {
        return eliminatedRunners.contains(playerId);
    }

    public boolean allRunnersEliminated() {
        for (Map.Entry<UUID, Role> entry : roles.entrySet()) {
            if (entry.getValue() == Role.RUNNER && !eliminatedRunners.contains(entry.getKey())) {
                return false;
            }
        }
        return true;
    }

    public List<Player> onlineRunners() {
        return onlineWithRole(Role.RUNNER);
    }

    public List<Player> onlineHunters() {
        return onlineWithRole(Role.HUNTER);
    }

    public List<Player> onlineAliveRunners() {
        List<Player> players = new ArrayList<>();
        for (Player player : onlineRunners()) {
            if (!eliminatedRunners.contains(player.getUniqueId())) {
                players.add(player);
            }
        }
        return players;
    }

    private List<Player> onlineWithRole(Role role) {
        List<Player> players = new ArrayList<>();
        for (Map.Entry<UUID, Role> entry : roles.entrySet()) {
            if (entry.getValue() == role) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null) {
                    players.add(player);
                }
            }
        }
        return players;
    }
}
