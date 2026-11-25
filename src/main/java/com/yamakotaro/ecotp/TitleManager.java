package com.yamakotaro.ecotp;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 称号: 季節イベントのショップなどで手に入れた称号を持ち、/titles で1つ選ぶと名前の前に付く。
 * 表示名 (displayName) に付けるので、表示名を使うチャットプラグインや EcoTP のチャット整形で出る。
 * 称号は & の色コード付きの文字列のまま titles.yml に保存する。
 */
public class TitleManager implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final EcoTpPlugin plugin;
    private final File file;
    private final YamlConfiguration data;

    public TitleManager(EcoTpPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "titles.yml");
        this.data = YamlIo.load(file);
    }

    public List<String> getOwned(UUID uuid) {
        return data.getStringList("players." + uuid + ".owned");
    }

    public boolean owns(UUID uuid, String title) {
        return getOwned(uuid).contains(title);
    }

    /** @return 選んでいる称号 (& の色コード付き)。無ければ空文字。 */
    public String getSelected(UUID uuid) {
        String selected = data.getString("players." + uuid + ".selected", "");
        return owns(uuid, selected) ? selected : "";
    }

    public void give(UUID uuid, String title) {
        List<String> owned = new ArrayList<>(getOwned(uuid));
        if (!owned.contains(title)) {
            owned.add(title);
            data.set("players." + uuid + ".owned", owned);
            save();
        }
    }

    /** @param title null か空文字なら称号を外す。 */
    public void select(Player player, String title) {
        data.set("players." + player.getUniqueId() + ".selected", title == null ? "" : title);
        save();
        apply(player);
    }

    /** 表示名に称号を付け直す。 */
    public void apply(Player player) {
        String title = getSelected(player.getUniqueId());
        player.displayName(title.isEmpty() ? null : LEGACY.deserialize(title + "&r " + player.getName()));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                apply(player);
            }
        });
    }

    private void save() {
        try {
            YamlIo.save(data, file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save titles.yml", e);
        }
    }
}
