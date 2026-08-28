package com.yamakotaro.ecotp;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 残高の永続化を担当するストレージ。config.yml の storage.type で
 * YAML (デフォルト) と MySQL (複数サーバーで残高を共有したい場合) を切り替えられる。
 */
public interface BalanceStorage {

    boolean hasAccount(UUID uuid);

    double getBalance(UUID uuid);

    void setBalance(UUID uuid, String name, double balance);

    void createAccount(UUID uuid, String name, double initialBalance);

    Optional<UUID> findUuidByName(String name);

    /**
     * 所持金が多い順に上位 limit 件を返す。
     */
    List<BalanceEntry> getTopBalances(int limit);

    /**
     * 変更があれば永続化する。定期タスクと終了時に呼ばれる。
     */
    void saveIfDirty();

    /**
     * プラグイン無効化時に呼ばれる。DB接続のクローズ等はここで行う。
     */
    void close();
}
