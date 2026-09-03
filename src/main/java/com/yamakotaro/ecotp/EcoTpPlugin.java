package com.yamakotaro.ecotp;

import com.yamakotaro.ecotp.commands.AcceptCommand;
import com.yamakotaro.ecotp.commands.BalanceCommand;
import com.yamakotaro.ecotp.commands.BaltopCommand;
import com.yamakotaro.ecotp.commands.DailyCommand;
import com.yamakotaro.ecotp.commands.DelHomeCommand;
import com.yamakotaro.ecotp.commands.DonateCommand;
import com.yamakotaro.ecotp.commands.DonateMessageCommand;
import com.yamakotaro.ecotp.commands.EcoAdminCommand;
import com.yamakotaro.ecotp.commands.EcoItemCommand;
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
import com.yamakotaro.ecotp.listeners.EcoItemListener;
import com.yamakotaro.ecotp.listeners.EconomyJoinListener;
import com.yamakotaro.ecotp.listeners.PlayerCleanupListener;
import com.yamakotaro.ecotp.listeners.VoteRewardJoinListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * このクラス (plugin.yml の main) は、net.milkbowl.vault.economy.Economy 型を
 * フィールド・メソッドの引数/戻り値・キャストのどこにも直接書かないこと。
 * Bukkit はサーバー起動時にこのクラスをロードした瞬間にクラス全体を検証するため、
 * Vault (softdepend、まだファイルスキャンで読み込まれていない可能性がある) の型を
 * ここで直接参照すると、Vaultより先にこのjarが処理された場合に
 * NoClassDefFoundError でロードそのものに失敗する。実際にこれで起動できなかった
 * ことがあるため、Economy に触れる処理はすべて EconomyHolder (onEnable() の中で
 * 初めて new される、完全に独立したクラス) に閉じ込めてある。
 */
public class EcoTpPlugin extends JavaPlugin {

    private Messages messages;
    private EconomyHolder economyHolder;
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
    private DonationManager donationManager;
    private VoteRewardManager voteRewardManager;
    private VotifierServer votifierServer;
    private DailyRewardStorage dailyRewardStorage;
    private DailyRewardManager dailyRewardManager;
    private EcoItemManager ecoItemManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.economyHolder = new EconomyHolder(this);
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
            economyHolder.setEcoTpEconomy(ecoTpEconomy);
        } else {
            getLogger().info("economy.enabled is false: using an external economy via Vault instead of the built-in one.");
            if (!economyHolder.tryLinkExternal()) {
                // Vault is guaranteed enabled by now (hard depend), but the actual economy
                // plugin backing it (e.g. EssentialsX) isn't a declared dependency of EcoTP,
                // so it may not have registered its Economy service yet. Don't give up yet —
                // try again once every plugin has finished enabling, right before the server
                // starts accepting connections.
                getServer().getPluginManager().registerEvents(new Listener() {
                    @EventHandler
                    public void onServerLoad(ServerLoadEvent event) {
                        HandlerList.unregisterAll(this);
                        if (!economyHolder.tryLinkExternal()) {
                            getLogger().severe("economy.enabled is false but no economy plugin is registered with Vault. Install Vault and an economy plugin (e.g. EssentialsX), or set economy.enabled to true.");
                            getServer().getPluginManager().disablePlugin(EcoTpPlugin.this);
                        }
                    }
                }, this);
            }
        }

        this.confirmationManager = new ConfirmationManager(this);
        this.homeManager = new HomeManager(this, homeStorage);
        this.spawnManager = new SpawnManager(this);
        this.tpaManager = new TpaManager(this);
        this.combatTracker = new CombatTracker(this);
        this.teleportSafetyManager = new TeleportSafetyManager(this, combatTracker);
        this.chatInputManager = new ChatInputManager(this);
        this.donationManager = new DonationManager(this);
        this.voteRewardManager = new VoteRewardManager(this);
        this.votifierServer = new VotifierServer(this);
        this.dailyRewardStorage = useMysql
                ? new MySqlDailyRewardStorage(this, mySqlConnectionProvider)
                : new YamlDailyRewardStorage(this);
        this.dailyRewardManager = new DailyRewardManager(this, dailyRewardStorage);
        this.ecoItemManager = new EcoItemManager(this);

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
        BalanceCommand balanceCommand = new BalanceCommand(this);
        getCommand("balance").setExecutor(balanceCommand);
        getCommand("balance").setTabCompleter(balanceCommand);
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
        DonateCommand donateCommand = new DonateCommand(this);
        getCommand("donate").setExecutor(donateCommand);
        getCommand("donate").setTabCompleter(donateCommand);
        getCommand("donatemessage").setExecutor(new DonateMessageCommand(this));
        getCommand("daily").setExecutor(new DailyCommand(this));
        EcoItemCommand ecoItemCommand = new EcoItemCommand(this);
        getCommand("ecoitem").setExecutor(ecoItemCommand);
        getCommand("ecoitem").setTabCompleter(ecoItemCommand);

        getServer().getPluginManager().registerEvents(new PlayerCleanupListener(this), this);
        getServer().getPluginManager().registerEvents(new EconomyJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new VoteRewardJoinListener(this), this);
        getServer().getPluginManager().registerEvents(combatTracker, this);
        getServer().getPluginManager().registerEvents(teleportSafetyManager, this);
        getServer().getPluginManager().registerEvents(chatInputManager, this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new EcoItemListener(this), this);
        new VoteRewardListener(this).register();
        votifierServer.start();

        if (ecoTpEconomy != null) {
            // 独自の経済を使っている場合のみ、Vault に登録して他のプラグインへ公開する。
            // 外部の経済を使っている場合は、既に登録されているものをそのまま使うので上書きしない。
            economyHolder.publishToVault();
        }
        var placeholderApi = getServer().getPluginManager().getPlugin("PlaceholderAPI");
        if (placeholderApi != null && placeholderApi.isEnabled()) {
            registerPlaceholders();
        } else if (placeholderApi != null) {
            // softdepend normally guarantees PlaceholderAPI enables before EcoTP, but it's
            // still possible to reach this if PlaceholderAPI is present yet not enabled
            // (e.g. it errored during its own onEnable) — defer until it actually enables,
            // if it ever does, rather than never registering placeholders at all.
            getServer().getPluginManager().registerEvents(new Listener() {
                @EventHandler
                public void onPluginEnable(PluginEnableEvent event) {
                    if (event.getPlugin().getName().equals("PlaceholderAPI")) {
                        HandlerList.unregisterAll(this);
                        registerPlaceholders();
                    }
                }
            }, this);
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
        if (dailyRewardStorage != null) {
            dailyRewardStorage.close();
        }
        if (mySqlConnectionProvider != null) {
            mySqlConnectionProvider.close();
        }
        if (votifierServer != null) {
            votifierServer.stop();
        }
    }

    private void registerPlaceholders() {
        new EcoTpPlaceholders(this).register();
        getLogger().info("Registered PlaceholderAPI placeholders (%ecotp_balance% and others).");
    }

    /**
     * config.yml の features.* で個別に無効化できる機能かどうか。未設定ならデフォルトで有効。
     */
    public boolean isFeatureEnabled(String key) {
        return getConfig().getBoolean("features." + key, true);
    }

    public EconomyHolder getEconomyHolder() {
        return economyHolder;
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

    public DonationManager getDonationManager() {
        return donationManager;
    }

    public VoteRewardManager getVoteRewardManager() {
        return voteRewardManager;
    }

    public DailyRewardManager getDailyRewardManager() {
        return dailyRewardManager;
    }

    public EcoItemManager getEcoItemManager() {
        return ecoItemManager;
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
