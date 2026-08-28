package com.yamakotaro.ecotp;

import com.yamakotaro.ecotp.commands.AcceptCommand;
import com.yamakotaro.ecotp.commands.BalanceCommand;
import com.yamakotaro.ecotp.commands.BaltopCommand;
import com.yamakotaro.ecotp.commands.DelHomeCommand;
import com.yamakotaro.ecotp.commands.EcoAdminCommand;
import com.yamakotaro.ecotp.commands.EcoTpCommand;
import com.yamakotaro.ecotp.commands.HomeCommand;
import com.yamakotaro.ecotp.commands.HomesCommand;
import com.yamakotaro.ecotp.commands.MenuCommand;
import com.yamakotaro.ecotp.commands.PayCommand;
import com.yamakotaro.ecotp.commands.SetHomeCommand;
import com.yamakotaro.ecotp.commands.SetSpawnCommand;
import com.yamakotaro.ecotp.commands.SpawnCommand;
import com.yamakotaro.ecotp.commands.TpaAcceptCommand;
import com.yamakotaro.ecotp.commands.TpaCancelCommand;
import com.yamakotaro.ecotp.commands.TpaCommand;
import com.yamakotaro.ecotp.commands.TpaDenyCommand;
import com.yamakotaro.ecotp.commands.TphereCommand;
import com.yamakotaro.ecotp.gui.GuiListener;
import com.yamakotaro.ecotp.listeners.EconomyJoinListener;
import com.yamakotaro.ecotp.listeners.PlayerCleanupListener;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public class EcoTpPlugin extends JavaPlugin {

    private Messages messages;
    private Economy economy;
    private EcoTpEconomy ecoTpEconomy;
    private BalanceStorage balanceStorage;
    private HomeStorage homeStorage;
    private MySqlConnectionProvider mySqlConnectionProvider;
    private ConfirmationManager confirmationManager;
    private HomeManager homeManager;
    private SpawnManager spawnManager;
    private TpaManager tpaManager;
    private CombatTracker combatTracker;
    private TeleportSafetyManager teleportSafetyManager;
    private ChatInputManager chatInputManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.messages = new Messages(this);
        ChatUtil.init(messages);

        boolean useMysql = getConfig().getString("storage.type", "yaml").equalsIgnoreCase("mysql");
        if (useMysql) {
            getLogger().info("Connecting to MySQL to manage homes (and balances, if enabled).");
            this.mySqlConnectionProvider = new MySqlConnectionProvider(this);
        }
        this.homeStorage = useMysql
                ? new MySqlHomeStorage(this, mySqlConnectionProvider)
                : new YamlHomeStorage(this);

        boolean economyEnabled = getConfig().getBoolean("economy.enabled", true);
        if (economyEnabled) {
            this.balanceStorage = useMysql
                    ? new MySqlBalanceStorage(this, mySqlConnectionProvider)
                    : new YamlBalanceStorage(this);
            EssentialsImporter essentialsImporter = new EssentialsImporter(this);
            this.ecoTpEconomy = new EcoTpEconomy(this, balanceStorage, essentialsImporter);
            this.economy = ecoTpEconomy;
        } else {
            getLogger().info("economy.enabled is false: using an external economy via Vault instead of the built-in one.");
            if (!setupExternalEconomy()) {
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        }

        this.confirmationManager = new ConfirmationManager(this);
        this.homeManager = new HomeManager(this, homeStorage);
        this.spawnManager = new SpawnManager(this);
        this.tpaManager = new TpaManager(this);
        this.combatTracker = new CombatTracker(this);
        this.teleportSafetyManager = new TeleportSafetyManager(this, combatTracker);
        this.chatInputManager = new ChatInputManager(this);

        HomeCommand homeCommand = new HomeCommand(this);
        getCommand("home").setExecutor(homeCommand);
        getCommand("home").setTabCompleter(homeCommand);
        SetHomeCommand setHomeCommand = new SetHomeCommand(this);
        getCommand("sethome").setExecutor(setHomeCommand);
        getCommand("sethome").setTabCompleter(setHomeCommand);
        DelHomeCommand delHomeCommand = new DelHomeCommand(this);
        getCommand("delhome").setExecutor(delHomeCommand);
        getCommand("delhome").setTabCompleter(delHomeCommand);
        getCommand("homes").setExecutor(new HomesCommand(this));
        getCommand("spawn").setExecutor(new SpawnCommand(this));
        getCommand("setspawn").setExecutor(new SetSpawnCommand(this));
        TpaCommand tpaCommand = new TpaCommand(this);
        getCommand("tpa").setExecutor(tpaCommand);
        getCommand("tpa").setTabCompleter(tpaCommand);
        TphereCommand tphereCommand = new TphereCommand(this);
        getCommand("tphere").setExecutor(tphereCommand);
        getCommand("tphere").setTabCompleter(tphereCommand);
        getCommand("tpaccept").setExecutor(new TpaAcceptCommand(this));
        getCommand("tpdeny").setExecutor(new TpaDenyCommand(this));
        getCommand("tpacancel").setExecutor(new TpaCancelCommand(this));
        getCommand("accept").setExecutor(new AcceptCommand(this));
        getCommand("balance").setExecutor(new BalanceCommand(this));
        PayCommand payCommand = new PayCommand(this);
        getCommand("pay").setExecutor(payCommand);
        getCommand("pay").setTabCompleter(payCommand);
        EcoAdminCommand ecoAdminCommand = new EcoAdminCommand(this);
        getCommand("eco").setExecutor(ecoAdminCommand);
        getCommand("eco").setTabCompleter(ecoAdminCommand);
        getCommand("baltop").setExecutor(new BaltopCommand(this));
        getCommand("menu").setExecutor(new MenuCommand(this));
        EcoTpCommand ecoTpCommand = new EcoTpCommand(this);
        getCommand("ecotp").setExecutor(ecoTpCommand);
        getCommand("ecotp").setTabCompleter(ecoTpCommand);

        getServer().getPluginManager().registerEvents(new PlayerCleanupListener(this), this);
        getServer().getPluginManager().registerEvents(new EconomyJoinListener(this), this);
        getServer().getPluginManager().registerEvents(combatTracker, this);
        getServer().getPluginManager().registerEvents(teleportSafetyManager, this);
        getServer().getPluginManager().registerEvents(chatInputManager, this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);

        if (ecoTpEconomy != null) {
            // 独自の経済を使っている場合のみ、Vault に登録して他のプラグインへ公開する。
            // 外部の経済を使っている場合は、既に登録されているものをそのまま使うので上書きしない。
            setupVault();
        }
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new EcoTpPlaceholders(this).register();
            getLogger().info("Registered PlaceholderAPI placeholders (%ecotp_balance% and others).");
        }

        if (balanceStorage != null) {
            // 取引の度にディスクへ保存すると負荷になるため、変更があったときだけ定期的にまとめて保存する。
            getServer().getScheduler().runTaskTimer(this, () -> balanceStorage.saveIfDirty(), 20L * 60, 20L * 60);
        }

        getLogger().info("EcoTP has been enabled. (economy.enabled=" + economyEnabled + ")");
    }

    @Override
    public void onDisable() {
        if (spawnManager != null) {
            spawnManager.save();
        }
        if (homeStorage != null) {
            homeStorage.close();
        }
        if (balanceStorage != null) {
            balanceStorage.close();
        }
        if (mySqlConnectionProvider != null) {
            mySqlConnectionProvider.close();
        }
    }

    /**
     * Vault が導入されていれば、この経済システムを他のプラグインにも公開する。
     * Vault が無くてもこのプラグイン自身の機能はすべて動作する。
     */
    private void setupVault() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().info("Vault not found; running without economy integration for other plugins.");
            return;
        }
        getServer().getServicesManager().register(Economy.class, economy, this, ServicePriority.Highest);
        getLogger().info("Published this economy to other plugins via Vault.");
    }

    /**
     * economy.enabled が false のとき、Vault 経由で他の経済プラグイン (Essentials 等) の
     * Economy を取得して使う。見つからない場合は false を返す (呼び出し側でプラグインを無効化する)。
     */
    private boolean setupExternalEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().severe("economy.enabled is false but Vault was not found. Install Vault and an economy plugin (e.g. EssentialsX), or set economy.enabled to true.");
            return false;
        }
        var registration = getServer().getServicesManager().getRegistration(Economy.class);
        if (registration == null) {
            getLogger().severe("economy.enabled is false but no economy plugin is registered with Vault.");
            return false;
        }
        this.economy = registration.getProvider();
        return true;
    }

    /**
     * config.yml の features.* で個別に無効化できる機能かどうか。未設定ならデフォルトで有効。
     */
    public boolean isFeatureEnabled(String key) {
        return getConfig().getBoolean("features." + key, true);
    }

    public Economy getEconomy() {
        return economy;
    }

    /**
     * @return 独自の経済 (economy.enabled: true) を使っている場合はそのインスタンス。
     * economy.enabled: false で外部の経済プラグインに任せている場合は null。
     */
    public EcoTpEconomy getEcoTpEconomy() {
        return ecoTpEconomy;
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

    public CombatTracker getCombatTracker() {
        return combatTracker;
    }

    public TeleportSafetyManager getTeleportSafetyManager() {
        return teleportSafetyManager;
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
