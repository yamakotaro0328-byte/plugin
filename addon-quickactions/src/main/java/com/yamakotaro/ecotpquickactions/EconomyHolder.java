package com.yamakotaro.ecotpquickactions;

import net.milkbowl.vault.economy.Economy;

/**
 * Vaultの Economy への参照をこのクラスに閉じ込める。EcoTpQuickActionsPlugin (main クラス)
 * 自体が Economy 型を直接使わないようにするための分離 — EcoTP本体で実際に遭遇した
 * NoClassDefFoundError (mainクラスのロード時検証で、まだ読み込まれていない依存プラグインの
 * 型を直接参照しているとロードに失敗する) と同じ問題を避けるため。
 */
public class EconomyHolder {

    private final EcoTpQuickActionsPlugin plugin;
    private Economy economy;

    public EconomyHolder(EcoTpQuickActionsPlugin plugin) {
        this.plugin = plugin;
    }

    public Economy get() {
        return economy;
    }

    /**
     * @return Vault経由で経済プラグインが見つかり利用可能になった場合はtrue。
     */
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
}
