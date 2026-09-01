package com.yamakotaro.ecotp.listeners;

import com.yamakotaro.ecotp.ChatUtil;
import com.yamakotaro.ecotp.EcoTpPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * /ecoitem give で生成した通貨アイテムを右クリックで換金する (EcoItemManager 参照)。
 * 通常のアイテムを右クリックしたときは EcoItemManager#redeem が 0 を返すだけなので、
 * 毎回のクリックでメッセージが出ることはない。
 */
public class EcoItemListener implements Listener {

    private final EcoTpPlugin plugin;

    public EcoItemListener(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // オフハンドでも同じイベントが飛んでくるため、二重処理を避けるためメインハンドのみ扱う。
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!plugin.isFeatureEnabled("eco-item")) {
            return;
        }
        Player player = event.getPlayer();
        double redeemed = plugin.getEcoItemManager().redeem(player);
        if (redeemed <= 0) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(plugin.msg("ecoitem.redeemed", "amount", ChatUtil.formatMoney(redeemed)));
    }
}
