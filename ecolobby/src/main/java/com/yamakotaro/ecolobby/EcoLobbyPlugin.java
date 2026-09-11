package com.yamakotaro.ecolobby;

import com.yamakotaro.ecolobby.commands.EcoLobbyCommand;
import com.yamakotaro.ecolobby.commands.HubCommand;
import com.yamakotaro.ecolobby.gui.GuiListener;
import com.yamakotaro.ecolobby.listeners.ChatSyncListener;
import com.yamakotaro.ecolobby.listeners.DoubleJumpListener;
import com.yamakotaro.ecolobby.listeners.EnderArrowListener;
import com.yamakotaro.ecolobby.listeners.FireworkWandListener;
import com.yamakotaro.ecolobby.listeners.HotbarItemListener;
import com.yamakotaro.ecolobby.listeners.InventorySyncListener;
import com.yamakotaro.ecolobby.listeners.JoinListener;
import com.yamakotaro.ecolobby.listeners.JumpPadListener;
import com.yamakotaro.ecolobby.listeners.ProtectionListener;
import com.yamakotaro.ecolobby.listeners.RocketBoostListener;
import com.yamakotaro.ecolobby.listeners.VoidTeleportListener;
import com.yamakotaro.ecolobby.tasks.ParticleTrailTask;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class EcoLobbyPlugin extends JavaPlugin {

    private Messages messages;
    private LobbySpawnManager spawnManager;
    private LobbyMenuConfig menuConfig;
    private PlayerStateManager playerStateManager;
    private NamespacedKey serverMenuItemKey;
    private NamespacedKey linksMenuItemKey;
    private NamespacedKey lobbyMenuItemKey;
    private NamespacedKey visibilityToggleItemKey;
    private NamespacedKey quickReturnItemKey;
    private NamespacedKey adminPanelItemKey;
    private NamespacedKey enderArrowBowKey;
    private NamespacedKey enderArrowProjectileKey;
    private NamespacedKey rocketBoostItemKey;
    private NamespacedKey fireworkWandItemKey;
    private BukkitTask particleTrailTask;
    private long enabledAtMillis;

    @Override
    public void onEnable() {
        this.enabledAtMillis = System.currentTimeMillis();
        saveDefaultConfig();
        this.messages = new Messages(this);
        this.spawnManager = new LobbySpawnManager(this);
        this.menuConfig = new LobbyMenuConfig(this);
        this.playerStateManager = new PlayerStateManager(this);
        this.serverMenuItemKey = new NamespacedKey(this, "server-menu-item");
        this.linksMenuItemKey = new NamespacedKey(this, "links-menu-item");
        this.lobbyMenuItemKey = new NamespacedKey(this, "lobby-menu-item");
        this.visibilityToggleItemKey = new NamespacedKey(this, "visibility-toggle-item");
        this.quickReturnItemKey = new NamespacedKey(this, "quick-return-item");
        this.adminPanelItemKey = new NamespacedKey(this, "admin-panel-item");
        this.enderArrowBowKey = new NamespacedKey(this, "ender-arrow-bow");
        this.enderArrowProjectileKey = new NamespacedKey(this, "ender-arrow-projectile");
        this.rocketBoostItemKey = new NamespacedKey(this, "rocket-boost-item");
        this.fireworkWandItemKey = new NamespacedKey(this, "firework-wand-item");

        BungeeConnector.register(this);

        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
        getServer().getPluginManager().registerEvents(new HotbarItemListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new InventorySyncListener(this), this);

        ChatSyncListener chatSyncListener = new ChatSyncListener(this);
        getServer().getPluginManager().registerEvents(chatSyncListener, this);
        getServer().getMessenger().registerIncomingPluginChannel(this, ChatSyncListener.channel(), chatSyncListener);

        if (isLobbyServer()) {
            getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
            getServer().getPluginManager().registerEvents(new DoubleJumpListener(this), this);
            getServer().getPluginManager().registerEvents(new VoidTeleportListener(this), this);
            getServer().getPluginManager().registerEvents(new JumpPadListener(this), this);
            getServer().getPluginManager().registerEvents(new EnderArrowListener(this), this);
            getServer().getPluginManager().registerEvents(new RocketBoostListener(this), this);
            getServer().getPluginManager().registerEvents(new FireworkWandListener(this), this);
        }

        this.particleTrailTask = new ParticleTrailTask(this).runTaskTimer(this, 5L, 5L);

        HubCommand hubCommand = new HubCommand(this);
        PluginCommand hub = getCommand("hub");
        hub.setExecutor(hubCommand);

        EcoLobbyCommand ecoLobbyCommand = new EcoLobbyCommand(this);
        PluginCommand ecolobby = getCommand("ecolobby");
        ecolobby.setExecutor(ecoLobbyCommand);
        ecolobby.setTabCompleter(ecoLobbyCommand);

        getLogger().info("EcoLobby enabled (is-lobby-server=" + isLobbyServer() + ").");
    }

    @Override
    public void onDisable() {
        if (particleTrailTask != null) {
            particleTrailTask.cancel();
        }
    }

    public boolean isLobbyServer() {
        return getConfig().getBoolean("is-lobby-server", true);
    }

    public boolean isFeatureEnabled(String key) {
        return getConfig().getBoolean("features." + key, true);
    }

    public String lobbyServerName() {
        return getConfig().getString("lobby-server-name", "lobby");
    }

    public void reload() {
        reloadConfig();
    }

    public Messages getMessages() {
        return messages;
    }

    public LobbySpawnManager getSpawnManager() {
        return spawnManager;
    }

    public LobbyMenuConfig getMenuConfig() {
        return menuConfig;
    }

    public NamespacedKey getServerMenuItemKey() {
        return serverMenuItemKey;
    }

    public NamespacedKey getLinksMenuItemKey() {
        return linksMenuItemKey;
    }

    public NamespacedKey getLobbyMenuItemKey() {
        return lobbyMenuItemKey;
    }

    public NamespacedKey getVisibilityToggleItemKey() {
        return visibilityToggleItemKey;
    }

    public NamespacedKey getQuickReturnItemKey() {
        return quickReturnItemKey;
    }

    public NamespacedKey getAdminPanelItemKey() {
        return adminPanelItemKey;
    }

    public NamespacedKey getEnderArrowBowKey() {
        return enderArrowBowKey;
    }

    public NamespacedKey getEnderArrowProjectileKey() {
        return enderArrowProjectileKey;
    }

    public NamespacedKey getRocketBoostItemKey() {
        return rocketBoostItemKey;
    }

    public NamespacedKey getFireworkWandItemKey() {
        return fireworkWandItemKey;
    }

    public PlayerStateManager getPlayerStateManager() {
        return playerStateManager;
    }

    public long getEnabledAtMillis() {
        return enabledAtMillis;
    }
}
