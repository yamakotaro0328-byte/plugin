package com.yamakotaro.ecocosmetics;

/**
 * コスメティックの種類。プレイヤーはカテゴリごとに最大1つまで同時装備できる。
 */
public enum Category {
    /** 常時ついてくるパーティクルトレイル。 */
    PARTICLE,
    /** サーバー参加時に1回だけ再生される演出。 */
    JOIN_EFFECT,
    /** チャットで名前の前につく色付きタグ。 */
    TITLE
}
