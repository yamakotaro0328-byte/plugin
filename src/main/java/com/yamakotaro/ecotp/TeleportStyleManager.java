package com.yamakotaro.ecotp;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * プレイヤーごとの「持っている演出」と「選んでいる演出」を teleport-styles.yml に保存する。
 */
public class TeleportStyleManager {

    public enum BuyResult { BOUGHT, ALREADY_OWNED, NOT_ENOUGH_MONEY, NO_ECONOMY, DISABLED }

    private final EcoTpPlugin plugin;
    private final File file;
    private final YamlConfiguration data;

    public TeleportStyleManager(EcoTpPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "teleport-styles.yml");
        this.data = YamlIo.load(file);
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("teleport-styles.enabled", true);
    }

    public double price(TeleportStyle style) {
        if (style == TeleportStyle.DEFAULT) {
            return 0;
        }
        return Math.max(0, plugin.getConfig().getDouble("teleport-styles.prices." + style.key(), style.defaultPrice));
    }

    public boolean owns(UUID uuid, TeleportStyle style) {
        return style == TeleportStyle.DEFAULT || price(style) <= 0
                || data.getStringList("players." + uuid + ".owned").contains(style.key());
    }

    /** @return 選んでいる演出。機能が無効、または持っていない演出なら DEFAULT。 */
    public TeleportStyle getSelected(UUID uuid) {
        if (!isEnabled()) {
            return TeleportStyle.DEFAULT;
        }
        TeleportStyle style = TeleportStyle.parse(data.getString("players." + uuid + ".selected"));
        return style != null && owns(uuid, style) ? style : TeleportStyle.DEFAULT;
    }

    public void select(UUID uuid, TeleportStyle style) {
        data.set("players." + uuid + ".selected", style.key());
        save();
    }

    public BuyResult buy(Player player, TeleportStyle style) {
        if (!isEnabled()) {
            return BuyResult.DISABLED;
        }
        UUID uuid = player.getUniqueId();
        if (owns(uuid, style)) {
            return BuyResult.ALREADY_OWNED;
        }
        Economy economy = plugin.getEconomyHolder().get();
        if (economy == null) {
            return BuyResult.NO_ECONOMY;
        }
        double price = price(style);
        if (!economy.has(player, price) || !economy.withdrawPlayer(player, price).transactionSuccess()) {
            return BuyResult.NOT_ENOUGH_MONEY;
        }
        List<String> owned = new ArrayList<>(data.getStringList("players." + uuid + ".owned"));
        owned.add(style.key());
        data.set("players." + uuid + ".owned", owned);
        data.set("players." + uuid + ".selected", style.key()); // 買ったらそのまま使う
        save();
        return BuyResult.BOUGHT;
    }

    private void save() {
        try {
            YamlIo.save(data, file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save teleport-styles.yml", e);
        }
    }
}
