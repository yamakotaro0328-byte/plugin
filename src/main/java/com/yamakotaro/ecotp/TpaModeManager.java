package com.yamakotaro.ecotp;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

/**
 * tpa / tphere を受けた時の動き (プレイヤーごと): 毎回聞く・常に許可・常に拒否。
 * tpa-modes.yml に保存する。常に許可でも、実際に移動する側には料金の確認が出る。
 */
public class TpaModeManager {

    public enum Mode {
        ASK, ACCEPT, DENY;

        public String key() {
            return name().toLowerCase();
        }

        public static Mode parse(String text) {
            if (text == null) {
                return null;
            }
            return switch (text.toLowerCase()) {
                case "ask" -> ASK;
                case "accept", "allow", "on" -> ACCEPT;
                case "deny", "off" -> DENY;
                default -> null;
            };
        }

        /** GUI で押すたびに 毎回聞く → 常に許可 → 常に拒否 → 毎回聞く と切り替える。 */
        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private final EcoTpPlugin plugin;
    private final File file;
    private final YamlConfiguration data;

    public TpaModeManager(EcoTpPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "tpa-modes.yml");
        this.data = YamlIo.load(file);
    }

    public Mode getMode(UUID uuid) {
        Mode mode = Mode.parse(data.getString("players." + uuid));
        return mode == null ? Mode.ASK : mode;
    }

    public void setMode(UUID uuid, Mode mode) {
        data.set("players." + uuid, mode == Mode.ASK ? null : mode.key());
        try {
            YamlIo.save(data, file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save tpa-modes.yml", e);
        }
    }
}
