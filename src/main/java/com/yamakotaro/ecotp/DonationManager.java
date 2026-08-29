package com.yamakotaro.ecotp;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * /donate の寄付通知をサーバー全体に流す。受け取り側のプレイヤーは /donatemessage で
 * 自分専用のお礼メッセージ({player}=寄付した人、{amount}=金額、{recipient}=自分)を
 * 設定でき、未設定なら messages.yml の donate.broadcast-default を使う。
 */
public class DonationManager {

    private final EcoTpPlugin plugin;
    private final Map<UUID, String> customMessages = new HashMap<>();
    private final File file;

    public DonationManager(EcoTpPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "donations.yml");
        load();
    }

    public void broadcastDonation(Player donor, Player recipient, double amount) {
        String custom = customMessages.get(recipient.getUniqueId());
        String message;
        if (custom != null) {
            message = ChatUtil.color(custom
                    .replace("{player}", donor.getName())
                    .replace("{recipient}", recipient.getName())
                    .replace("{amount}", ChatUtil.formatMoney(amount)));
        } else {
            message = plugin.getMessages().get("donate.broadcast-default",
                    "player", donor.getName(),
                    "recipient", recipient.getName(),
                    "amount", ChatUtil.formatMoney(amount));
        }
        Bukkit.broadcastMessage(message);
    }

    public void setMessageTemplate(UUID uuid, String template) {
        customMessages.put(uuid, template);
        save();
    }

    public void resetMessageTemplate(UUID uuid) {
        customMessages.remove(uuid);
        save();
    }

    private void load() {
        customMessages.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        var section = data.getConfigurationSection("messages");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                customMessages.put(UUID.fromString(key), section.getString(key));
            } catch (IllegalArgumentException ignored) {
                // Skip a malformed entry rather than failing the whole load.
            }
        }
    }

    private void save() {
        YamlConfiguration data = new YamlConfiguration();
        for (Map.Entry<UUID, String> entry : customMessages.entrySet()) {
            data.set("messages." + entry.getKey(), entry.getValue());
        }
        try {
            plugin.getDataFolder().mkdirs();
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save donations.yml", e);
        }
    }
}
