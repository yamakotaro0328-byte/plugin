package com.yamakotaro.ecocosmetics.listeners;

import com.yamakotaro.ecocosmetics.CosmeticDefinition;
import com.yamakotaro.ecocosmetics.CosmeticManager;
import com.yamakotaro.ecocosmetics.EcoCosmeticsPlugin;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * 装備中のコスメティックを実際に反映する: 参加時のJOIN_EFFECT再生と、チャットでのTITLE接頭辞付与。
 * PARTICLEトレイルは tasks.ParticleTrailTask が定期的に処理する。
 */
public class CosmeticEffectListener implements Listener {

    private final EcoCosmeticsPlugin plugin;

    public CosmeticEffectListener(EcoCosmeticsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        CosmeticManager manager = plugin.getCosmeticManager();
        CosmeticDefinition joinEffect = manager.getEquippedJoinEffect(player.getUniqueId());
        if (joinEffect == null || joinEffect.particle() == null) {
            return;
        }
        player.getWorld().spawnParticle(joinEffect.particle(), player.getLocation().add(0, 1, 0), 40, 0.5, 0.5, 0.5, 0.05);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String prefix = plugin.getCosmeticManager().getEquippedTitlePrefix(player.getUniqueId());
        if (prefix == null || prefix.isEmpty()) {
            return;
        }
        Component prefixComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(prefix);
        ChatRenderer previous = event.renderer();
        event.renderer((source, sourceDisplayName, message, viewer) ->
                Component.text().append(prefixComponent).append(previous.render(source, sourceDisplayName, message, viewer)).build());
    }
}
