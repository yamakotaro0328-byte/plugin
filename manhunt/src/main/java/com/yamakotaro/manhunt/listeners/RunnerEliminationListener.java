package com.yamakotaro.manhunt.listeners;

import com.yamakotaro.manhunt.game.GameManager;
import com.yamakotaro.manhunt.game.Role;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class RunnerEliminationListener implements Listener {

    private final GameManager gameManager;

    public RunnerEliminationListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!gameManager.game().isRunning() || gameManager.game().getRole(player.getUniqueId()) != Role.RUNNER) {
            return;
        }
        gameManager.eliminate(player);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        // isRunning() guards against the game having just ended because THIS elimination was the
        // last one - in that case the game is over and the player should respawn normally.
        if (gameManager.game().isRunning() && gameManager.game().isEliminated(player.getUniqueId())) {
            player.setGameMode(GameMode.SPECTATOR);
        }
    }
}
