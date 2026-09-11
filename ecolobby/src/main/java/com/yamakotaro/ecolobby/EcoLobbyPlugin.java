package com.yamakotaro.ecolobby;

import com.yamakotaro.ecolobby.commands.EcoLobbyCommand;
import com.yamakotaro.ecolobby.commands.HubCommand;
import com.yamakotaro.ecolobby.gui.GuiListener;
import com.yamakotaro.ecolobby.listeners.DoubleJumpListener;
import com.yamakotaro.ecolobby.listeners.HotbarItemListener;
import com.yamakotaro.ecolobby.listeners.JoinListener;
import com.yamakotaro.ecolobby.listeners.ProtectionListener;
import com.yamakotaro.ecolobby.listeners.VoidTeleportListener;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class EcoLobbyPlugin extends JavaPlugin {

    private Messages messages;
    private LobbySpawnManager spawnManager;
    private LobbyMenuConfig menuConfig;
    private NamespacedKey serverMenuItemKey;
    private NamespacedKey linksMenuItemKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.messages = new Messages(this);
        this.spawnManager = new LobbySpawnManager(this);
        this.menuConfig = new LobbyMenuConfig(this);
        this.serverMenuItemKey = new NamespacedKey(this, "server-menu-item");
        this.linksMenuItemKey = new NamespacedKey(this, "links-menu-item");

        BungeeConnector.register(this);

        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
        getServer().getPluginManager().registerEvents(new HotbarItemListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        if (isLobbyServer()) {
            getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
            getServer().getPluginManager().registerEvents(new DoubleJumpListener(this), this);
            getServer().getPluginManager().registerEvents(new VoidTeleportListener(this), this);
        }

        HubCommand hubCommand = new HubCommand(this);
        PluginCommand hub = getCommand("hub");
        hub.setExecutor(hubCommand);

        EcoLobbyCommand ecoLobbyCommand = new EcoLobbyCommand(this);
        PluginCommand ecolobby = getCommand("ecolobby");
        ecolobby.setExecutor(ecoLobbyCommand);
        ecolobby.setTabCompleter(ecoLobbyCommand);

        getLogger().info("EcoLobby enabled (is-lobby-server=" + isLobbyServer() + ").");
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
}
