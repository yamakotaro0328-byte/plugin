package com.yamakotaro.ecotp;

import org.bukkit.Location;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * プレイヤーごとの複数ホーム (名前付き) と、/sethome を使った回数
 * (料金の上昇に使う。ホーム名に関わらずプレイヤー全体で共通) を管理する。
 * 実際の永続化は HomeStorage (YAML または MySQL) に委譲する。
 */
public class HomeManager {

    /** ホーム名は数字・日本語 (ひらがな/カタカナ/漢字/長音符)・英字のみ、最大16文字。 */
    public static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9\\u3040-\\u30FF\\u4E00-\\u9FFF]{1,16}$");
    public static final String DEFAULT_NAME = "home";

    private final EcoTpPlugin plugin;
    private final HomeStorage storage;

    public HomeManager(EcoTpPlugin plugin, HomeStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public boolean hasHome(UUID uuid, String name) {
        return storage.hasHome(uuid, name);
    }

    public Location getHome(UUID uuid, String name) {
        return storage.getHome(uuid, name);
    }

    /**
     * @return 上限に達していて新規のホームを作成できない場合 false。
     * 既存の名前を上書きする場合は上限に関わらず常に true。
     */
    public boolean canSetHome(UUID uuid, String name) {
        if (storage.hasHome(uuid, name)) {
            return true;
        }
        int max = plugin.getConfig().getInt("homes.max-per-player", 3);
        return storage.getHomeNames(uuid).size() < max;
    }

    public void setHome(UUID uuid, String name, Location location) {
        storage.setHome(uuid, name, location);
        storage.incrementSetHomeCount(uuid);
    }

    /**
     * @return 削除できた場合 true。そのホームが存在しなかった場合 false。
     */
    public boolean deleteHome(UUID uuid, String name) {
        return storage.deleteHome(uuid, name);
    }

    public List<String> getHomeNames(UUID uuid) {
        return storage.getHomeNames(uuid);
    }

    public int getSetHomeCount(UUID uuid) {
        return storage.getSetHomeCount(uuid);
    }

    /**
     * 次に /sethome を実行したときの料金。
     * 1回目: base, 2回目: base + increment, 3回目: base + increment*2 ...
     */
    public double getNextSetHomeCost(UUID uuid) {
        double base = plugin.getConfig().getDouble("costs.sethome-base", 1000.0);
        double increment = plugin.getConfig().getDouble("costs.sethome-increment", 1000.0);
        int count = getSetHomeCount(uuid);
        return base + increment * count;
    }

    public void save() {
        storage.saveIfDirty();
    }
}
