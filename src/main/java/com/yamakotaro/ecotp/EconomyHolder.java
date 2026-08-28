package com.yamakotaro.ecotp;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.ServicePriority;

/**
 * net.milkbowl.vault.economy.Economy への参照をこのクラスに閉じ込める。
 * EcoTpPlugin (plugin.yml の main クラス) 自体のフィールドやメソッドが Economy 型を
 * 直接使っていると、Bukkit がサーバー起動時にこのプラグインの main クラスを検証する
 * 瞬間、Vault (softdepend の一つ) がまだファイルスキャンで読み込まれていない場合に
 * NoClassDefFoundError でロード自体に失敗することがある (これは Bukkit プラグイン
 * 開発でよく知られた問題で、ロードされる順序は plugin.yml の load/depend/softdepend/
 * loadbefore の設定とは無関係に、OS のファイルスキャン順に左右される)。
 * このクラスは EcoTpPlugin の onEnable() の中で初めて new されるので、その時点では
 * 全プラグインの初期ロードが完了しておりこの問題は起きない。
 */
public class EconomyHolder {

    private final EcoTpPlugin plugin;
    private Economy economy;

    public EconomyHolder(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    public Economy get() {
        return economy;
    }

    public void set(Economy economy) {
        this.economy = economy;
    }

    /**
     * EcoTpPlugin から呼ぶための専用オーバーロード。引数を EcoTpEconomy 型のままにする
     * ことで、EcoTpEconomy→Economy のアップキャストがこのクラス自身のバイトコード内で
     * 行われるようにし、呼び出し側 (EcoTpPlugin) のバイトコードに Economy 型が
     * 一切現れないようにする。
     */
    public void setEcoTpEconomy(EcoTpEconomy ecoTpEconomy) {
        this.economy = ecoTpEconomy;
    }

    /**
     * Vault が導入されていれば、この経済システムを他のプラグインにも公開する。
     * Vault が無くてもこのプラグイン自身の機能はすべて動作する。
     */
    public void publishToVault() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().info("Vault not found; running without economy integration for other plugins.");
            return;
        }
        plugin.getServer().getServicesManager().register(Economy.class, economy, plugin, ServicePriority.Highest);
        plugin.getLogger().info("Published this economy to other plugins via Vault.");
    }

    /**
     * economy.enabled が false のとき、Vault 経由で既に登録されている外部の経済プラグイン
     * (Essentials 等) を取得する。ここではまだ見つからなくても (相手がまだ有効化されて
     * いないだけかもしれず) 何もログを出さず false を返すだけにする。
     * 「本当に無い」のか「まだ見つかっていないだけ」なのかは呼び出し側が判断する。
     */
    public boolean tryLinkExternal() {
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
