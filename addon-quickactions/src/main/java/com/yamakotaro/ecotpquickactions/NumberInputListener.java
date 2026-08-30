package com.yamakotaro.ecotpquickactions;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * NumberInputHolderの+/-ボタン、確定・キャンセル・無効化ボタンのクリックを処理する。
 * チャット入力を一切使わないため、他のチャット系プラグインに邪魔される余地がない。
 */
public class NumberInputListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof NumberInputHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() != event.getInventory()) {
            return;
        }
        int slot = event.getSlot();

        Double delta = holder.getDelta(slot);
        if (delta != null) {
            holder.adjust(delta);
            return;
        }

        if (slot == NumberInputHolder.CANCEL_SLOT) {
            player.closeInventory();
            holder.getOnCancel().run();
            return;
        }

        if (slot == NumberInputHolder.CONFIRM_SLOT) {
            if (!holder.canConfirm()) {
                return;
            }
            double value = holder.getValue();
            player.closeInventory();
            holder.getOnConfirm().accept(value);
            return;
        }

        if (slot == NumberInputHolder.DISABLE_SLOT && holder.isAllowDisable()) {
            player.closeInventory();
            holder.getOnDisable().run();
        }
    }
}
