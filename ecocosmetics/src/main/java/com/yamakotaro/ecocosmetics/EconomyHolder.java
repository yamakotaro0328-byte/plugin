package com.yamakotaro.ecocosmetics;

import net.milkbowl.vault.economy.Economy;

/**
 * net.milkbowl.vault.economy.Economy への参照をこのクラスに閉じ込める。
 * メインクラス (EcoCosmeticsPlugin) 自体のフィールド・メソッドの引数/戻り値・キャストの
 * どこにも Economy 型を直接書かないこと。Bukkit はサーバー起動時にメインクラスをロードした
 * 瞬間にクラス全体を検証するため、Vault (softdepend、まだファイルスキャンで読み込まれていない
 * 可能性がある) の型をそこで直接参照すると、Vaultより先にこのjarが処理された場合に
 * NoClassDefFoundError でロードそのものに失敗することがある。
 */
public class EconomyHolder {

    private final EcoCosmeticsPlugin plugin;
    private Economy economy;

    public EconomyHolder(EcoCosmeticsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean setup() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        var registration = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (registration == null) {
            return false;
        }
        this.economy = registration.getProvider();
        return true;
    }

    public Economy get() {
        return economy;
    }
}
