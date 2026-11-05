package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.EcoTpPlugin;
import com.yamakotaro.ecotp.gui.TeleportStyleHolder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /tpeffect : テレポート演出の選択/購入GUIを開く。 */
public class TpEffectCommand implements CommandExecutor {

    private final EcoTpPlugin plugin;

    public TpEffectCommand(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().get("general.players-only"));
            return true;
        }
        if (!plugin.getTeleportStyleManager().isEnabled()) {
            player.sendMessage(plugin.msg("general.feature-disabled"));
            return true;
        }
        if (!player.hasPermission("ecotp.tpeffect")) {
            player.sendMessage(plugin.msg("general.no-permission"));
            return true;
        }
        player.openInventory(new TeleportStyleHolder(plugin, player).getInventory());
        return true;
    }
}
