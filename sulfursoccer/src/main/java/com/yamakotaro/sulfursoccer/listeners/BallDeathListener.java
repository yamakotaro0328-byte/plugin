package com.yamakotaro.sulfursoccer.listeners;

import com.yamakotaro.sulfursoccer.match.Match;
import com.yamakotaro.sulfursoccer.match.MatchManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.Optional;

/**
 * The match ball is a real, killable mob (fire, fall damage, a lucky hit) - rather than fight
 * the knockback physics that make it work as a ball by trying to make it invulnerable, this just
 * lets it die and puts a fresh one at kickoff immediately, so it's seamless to players.
 */
public class BallDeathListener implements Listener {

    private final MatchManager matchManager;

    public BallDeathListener(MatchManager matchManager) {
        this.matchManager = matchManager;
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        Optional<Match> match = matchManager.findMatchByBallId(event.getEntity().getUniqueId());
        if (match.isEmpty()) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        matchManager.respawnBall(match.get());
    }
}
