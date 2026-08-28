package com.yamakotaro.ecotp;

import org.bukkit.Location;

import java.util.List;
import java.util.UUID;

/**
 * ホームの永続化を担当するストレージ。config.yml の storage.type で
 * YAML (デフォルト) と MySQL (複数サーバーでホームを共有したい場合) を切り替えられる。
 */
public interface HomeStorage {

    boolean hasHome(UUID uuid, String name);

    Location getHome(UUID uuid, String name);

    void setHome(UUID uuid, String name, Location location);

    /**
     * @return 削除できた場合 true。そのホームが存在しなかった場合 false。
     */
    boolean deleteHome(UUID uuid, String name);

    List<String> getHomeNames(UUID uuid);

    int getSetHomeCount(UUID uuid);

    void incrementSetHomeCount(UUID uuid);

    void saveIfDirty();

    void close();
}
