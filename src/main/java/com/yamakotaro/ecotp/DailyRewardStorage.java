package com.yamakotaro.ecotp;

import java.util.UUID;

/**
 * /daily の請求履歴 (最終請求日時・連続日数) を保存するストレージ。config.yml の
 * storage.type で YAML (デフォルト) と MySQL (複数サーバーで共有したい場合) を切り替えられる。
 * 残高・ホームと同じ storage.type 設定を共有する。
 */
public interface DailyRewardStorage {

    /**
     * @return 最後に請求した時刻 (エポックミリ秒)。一度も請求していなければ 0。
     */
    long getLastClaimMillis(UUID uuid);

    /**
     * @return 現在の連続請求日数。一度も請求していなければ 0。
     */
    int getStreak(UUID uuid);

    void recordClaim(UUID uuid, long claimMillis, int newStreak);

    void close();
}
