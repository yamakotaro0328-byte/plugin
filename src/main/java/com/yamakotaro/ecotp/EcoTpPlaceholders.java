package com.yamakotaro.ecotp;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

/**
 * PlaceholderAPI 用のプレースホルダー。
 * %ecotp_balance%           所持金 (数値のみ)
 * %ecotp_balance_formatted% 所持金 ("1000円" のように整形)
 * %ecotp_sethome_cost%      次に /sethome を使ったときの料金
 */
public class EcoTpPlaceholders extends PlaceholderExpansion {

    private final EcoTpPlugin plugin;

    public EcoTpPlaceholders(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "ecotp";
    }

    @Override
    public String getAuthor() {
        return "yamakotaro0328";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null) {
            return "";
        }
        return switch (identifier.toLowerCase()) {
            case "balance" -> String.valueOf(Math.round(plugin.getEconomyHolder().get().getBalance(player)));
            case "balance_formatted" -> ChatUtil.formatMoney(plugin.getEconomyHolder().get().getBalance(player));
            case "sethome_cost" -> ChatUtil.formatMoney(plugin.getHomeManager().getNextSetHomeCost(player.getUniqueId()));
            default -> null;
        };
    }
}
