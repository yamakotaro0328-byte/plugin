package com.yamakotaro.manhunt.game;

import com.yamakotaro.manhunt.Messages;
import com.yamakotaro.manhunt.items.SpecialItems;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates the single ManhuntGame: role changes, starting/stopping, win conditions, the
 * server-wide announcements and gamemode changes that go with them, and handing out/clearing the
 * role-specific special items.
 */
public class GameManager {

    private final ManhuntGame game = new ManhuntGame();
    private final Messages messages;
    private final SpecialItems specialItems;
    private final JavaPlugin plugin;

    public GameManager(Messages messages, SpecialItems specialItems, JavaPlugin plugin) {
        this.messages = messages;
        this.specialItems = specialItems;
        this.plugin = plugin;
    }

    public ManhuntGame game() {
        return game;
    }

    public String setRole(UUID playerId, Role role) {
        return game.setRole(playerId, role);
    }

    public void leave(UUID playerId) {
        game.removePlayer(playerId);
    }

    public String start(long headStartSeconds) {
        String error = game.start(headStartSeconds);
        if (error != null) {
            return error;
        }
        giveSpecialItems();
        announceToAll("manhunt.started-headstart", Map.of("seconds", String.valueOf(headStartSeconds)));
        return null;
    }

    public String stopManually() {
        if (!game.isRunning()) {
            return "manhunt.not-running";
        }
        endGame("manhunt.stopped-manually");
        return null;
    }

    public void runnersWin() {
        endGame("manhunt.runners-win");
    }

    public void huntersWin() {
        endGame("manhunt.hunters-win");
    }

    /** Marks a runner caught, announces it, and ends the game if that was the last one standing. */
    public void eliminate(Player runner) {
        game.eliminate(runner.getUniqueId());
        announceToAll("manhunt.runner-eliminated", Map.of("player", runner.getName()));
        if (game.allRunnersEliminated()) {
            huntersWin();
        }
    }

    private void giveSpecialItems() {
        int locatorCharges = plugin.getConfig().getInt("items.locator-orb-charges", 3);
        int smokeCharges = plugin.getConfig().getInt("items.smoke-bomb-charges", 2);
        long smokeDuration = plugin.getConfig().getLong("items.smoke-bomb-duration-seconds", 8);
        for (Player hunter : game.onlineHunters()) {
            hunter.getInventory().addItem(specialItems.createLocatorOrb(locatorCharges));
            hunter.sendMessage(messages.get("item.locator-given", Map.of("count", String.valueOf(locatorCharges))));
        }
        for (Player runner : game.onlineRunners()) {
            runner.getInventory().addItem(specialItems.createSmokeBomb(smokeCharges, smokeDuration));
            runner.sendMessage(messages.get("item.smoke-given", Map.of("count", String.valueOf(smokeCharges))));
        }
    }

    private void endGame(String announcementKey) {
        // Restore anyone already spectating from an earlier elimination this game. The runner
        // who was JUST eliminated (if that's why the game is ending) hasn't respawned into
        // spectator mode yet, so there's nothing to restore for them here.
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (game.isEliminated(player.getUniqueId())) {
                player.setGameMode(GameMode.SURVIVAL);
            }
            specialItems.clearFrom(player);
        }
        game.stop();
        announceToAll(announcementKey, Map.of());
    }

    private void announceToAll(String key, Map<String, String> placeholders) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(messages.get(key, placeholders));
        }
    }
}
