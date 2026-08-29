package com.yamakotaro.serverkit.staff;

import com.yamakotaro.serverkit.Messages;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;

public class StaffListener implements Listener {

    private final VanishManager vanishManager;
    private final FreezeManager freezeManager;
    private final StaffChatManager staffChatManager;
    private final Messages messages;

    public StaffListener(VanishManager vanishManager, FreezeManager freezeManager,
                          StaffChatManager staffChatManager, Messages messages) {
        this.vanishManager = vanishManager;
        this.freezeManager = freezeManager;
        this.staffChatManager = staffChatManager;
        this.messages = messages;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        vanishManager.applyTo(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        vanishManager.clear(event.getPlayer().getUniqueId());
        freezeManager.clear(event.getPlayer().getUniqueId());
        staffChatManager.clear(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!freezeManager.isFrozen(event.getPlayer().getUniqueId())) {
            return;
        }
        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (freezeManager.isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(messages.get("staff.frozen-blocked", Map.of()));
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (freezeManager.isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (freezeManager.isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (freezeManager.isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        if (!staffChatManager.isToggled(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        staffChatManager.broadcast(event.getPlayer(), message);
    }
}
