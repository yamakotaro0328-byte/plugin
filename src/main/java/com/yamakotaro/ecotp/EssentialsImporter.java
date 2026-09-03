package com.yamakotaro.ecotp;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * このプラグインを初めて使うプレイヤーの残高を、
 * (もし存在すれば) EssentialsX のユーザーデータから一度だけ引き継ぐ。
 * Essentials プラグイン自体が導入されていなくても、旧データフォルダが
 * 残っていれば移行できる。
 */
public class EssentialsImporter {

    private final EcoTpPlugin plugin;
    private final File userdataFolder;

    public EssentialsImporter(EcoTpPlugin plugin) {
        this.plugin = plugin;
        File pluginsFolder = plugin.getDataFolder().getParentFile();
        this.userdataFolder = new File(pluginsFolder, "Essentials" + File.separator + "userdata");
    }

    /**
     * @return Essentials のデータから読み取った残高。見つからない/読み取れない場合は null。
     */
    public Double tryImportBalance(UUID uuid) {
        if (!plugin.getConfig().getBoolean("essentials-import.enabled", true)) {
            return null;
        }

        File flat = new File(userdataFolder, uuid + ".yml");
        File sharded = new File(userdataFolder, uuid.toString().substring(0, 2) + File.separator + uuid + ".yml");
        File file = flat.isFile() ? flat : sharded;
        if (!file.isFile()) {
            return null;
        }

        YamlConfiguration yaml = YamlIo.load(file);
        String moneyStr = yaml.getString("money");
        if (moneyStr == null) {
            return null;
        }

        try {
            return new BigDecimal(moneyStr).doubleValue();
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Could not parse balance from Essentials data (" + uuid + "): " + moneyStr);
            return null;
        }
    }
}
