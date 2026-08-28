package com.yamakotaro.ecotp;

import com.yamakotaro.ecotp.commands.AcceptCommand;
import com.yamakotaro.ecotp.commands.BalanceCommand;
import com.yamakotaro.ecotp.commands.BaltopCommand;
import com.yamakotaro.ecotp.commands.EcoAdminCommand;
import com.yamakotaro.ecotp.commands.HomeCommand;
import com.yamakotaro.ecotp.commands.MenuCommand;
import com.yamakotaro.ecotp.commands.PayCommand;
import com.yamakotaro.ecotp.commands.SetHomeCommand;
import com.yamakotaro.ecotp.commands.SetSpawnCommand;
import com.yamakotaro.ecotp.commands.SpawnCommand;
import com.yamakotaro.ecotp.commands.TpCommand;
import com.yamakotaro.ecotp.commands.TpaAcceptCommand;
import com.yamakotaro.ecotp.commands.TpaCommand;
import com.yamakotaro.ecotp.commands.TpaDenyCommand;
import com.yamakotaro.ecotp.gui.GuiListener;
import com.yamakotaro.ecotp.listeners.EconomyJoinListener;
import com.yamakotaro.ecotp.listeners.PlayerCleanupListener;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public class EcoTpPlugin extends JavaPlugin {

    private Messages messages;
    private EcoTpEconomy economy;
    private BalanceStorage balanceStorage;
    private ConfirmationManager confirmationManager;
    private HomeManager homeManager;
    private SpawnManager spawnManager;
    private TpaManager tpaManager;
    private WarmupManager warmupManager;
    private ChatInputManager chatInputManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.messages = new Messages(this);
        ChatUtil.init(messages);

        this.balanceStorage = createBalanceStorage();
        EssentialsImporter essentialsImporter = new EssentialsImporter(this);
        this.economy = new EcoTpEconomy(this, balanceStorage, essentialsImporter);

        this.confirmationManager = new ConfirmationManager(this);
        this.homeManager = new HomeManager(this);
        this.spawnManager = new SpawnManager(this);
        this.tpaManager = new TpaManager(this);
        this.warmupManager = new WarmupManager(this);
        this.chatInputManager = new ChatInputManager(this);

        getCommand("home").setExecutor(new HomeCommand(this));
        getCommand("sethome").setExecutor(new SetHomeCommand(this));
        getCommand("spawn").setExecutor(new SpawnCommand(this));
        getCommand("setspawn").setExecutor(new SetSpawnCommand(this));
        getCommand("tp").setExecutor(new TpCommand(this));
        getCommand("tpa").setExecutor(new TpaCommand(this));
        getCommand("tpaccept").setExecutor(new TpaAcceptCommand(this));
        getCommand("tpdeny").setExecutor(new TpaDenyCommand(this));
        getCommand("accept").setExecutor(new AcceptCommand(this));
        getCommand("balance").setExecutor(new BalanceCommand(this));
        getCommand("pay").setExecutor(new PayCommand(this));
        getCommand("eco").setExecutor(new EcoAdminCommand(this));
        getCommand("baltop").setExecutor(new BaltopCommand(this));
        getCommand("menu").setExecutor(new MenuCommand(this));

        getServer().getPluginManager().registerEvents(new PlayerCleanupListener(this), this);
        getServer().getPluginManager().registerEvents(new EconomyJoinListener(this), this);
        getServer().getPluginManager().registerEvents(warmupManager, this);
        getServer().getPluginManager().registerEvents(chatInputManager, this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);

        setupVault();
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new EcoTpPlaceholders(this).register();
            getLogger().info("PlaceholderAPI のプレースホルダーを登録しました。(%ecotp_balance% など)");
        }

        // 取引の度にディスクへ保存すると負荷になるため、変更があったときだけ定期的にまとめて保存する。
        getServer().getScheduler().runTaskTimer(this, () -> balanceStorage.saveIfDirty(), 20L * 60, 20L * 60);

        getLogger().info("EcoTP が有効になりました。(独自の経済システムで動作中、Essentials は不要です)");
    }

    @Override
    public void onDisable() {
        if (homeManager != null) {
            homeManager.save();
        }
        if (spawnManager != null) {
            spawnManager.save();
        }
        if (balanceStorage != null) {
            balanceStorage.close();
        }
    }

    private BalanceStorage createBalanceStorage() {
        String type = getConfig().getString("storage.type", "yaml");
        if (type.equalsIgnoreCase("mysql")) {
            getLogger().info("MySQL に接続して残高を管理します。");
            return new MySqlBalanceStorage(this);
        }
        return new YamlBalanceStorage(this);
    }

    /**
     * Vault が導入されていれば、この経済システムを他のプラグインにも公開する。
     * Vault が無くてもこのプラグイン自身の機能はすべて動作する。
     */
    private void setupVault() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().info("Vault が見つかりません。他のプラグインとの経済連携なしで動作します。");
            return;
        }
        getServer().getServicesManager().register(Economy.class, economy, this, ServicePriority.Highest);
        getLogger().info("Vault 経由でこの経済システムを他のプラグインに公開しました。");
    }

    public Economy getEconomy() {
        return economy;
    }

    public EcoTpEconomy getEcoTpEconomy() {
        return economy;
    }

    public ConfirmationManager getConfirmationManager() {
        return confirmationManager;
    }

    public HomeManager getHomeManager() {
        return homeManager;
    }

    public SpawnManager getSpawnManager() {
        return spawnManager;
    }

    public TpaManager getTpaManager() {
        return tpaManager;
    }

    public WarmupManager getWarmupManager() {
        return warmupManager;
    }

    public ChatInputManager getChatInputManager() {
        return chatInputManager;
    }

    public Messages getMessages() {
        return messages;
    }

    public String getPrefix() {
        return getConfig().getString("prefix", "&8[&6EcoTP&8]&r ");
    }

    /**
     * messages.yml の1メッセージに接頭辞を付けて色変換した文字列を返す簡易ヘルパー。
     */
    public String msg(String path, Object... replacements) {
        return ChatUtil.color(getPrefix()) + messages.get(path, replacements);
    }
}
