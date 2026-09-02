package com.yamakotaro.manhunt.listeners;

import com.yamakotaro.manhunt.game.ManhuntGame;
import com.yamakotaro.manhunt.game.Role;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/** Freezes hunters in place (they can still look around) for the duration of the head start. */
public class HeadStartFreezeListener implements Listener {

    private final ManhuntGame game;

    public HeadStartFreezeListener(ManhuntGame game) {
        this.game = game;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!game.isHeadStartActive() || game.getRole(event.getPlayer().getUniqueId()) != Role.HUNTER) {
            return;
        }
        if (event.getFrom().getX() == event.getTo().getX()
                && event.getFrom().getY() == event.getTo().getY()
                && event.getFrom().getZ() == event.getTo().getZ()) {
            return;
        }
        event.setCancelled(true);
    }
}
