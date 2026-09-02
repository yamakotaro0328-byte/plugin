package com.yamakotaro.manhunt.listeners;

import com.yamakotaro.manhunt.game.GameManager;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public class DragonDeathListener implements Listener {

    private final GameManager gameManager;

    public DragonDeathListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (event.getEntityType() != EntityType.ENDER_DRAGON || !gameManager.game().isRunning()) {
            return;
        }
        gameManager.runnersWin();
    }
}
