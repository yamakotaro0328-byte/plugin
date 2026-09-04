package com.yamakotaro.ecopotions;

import com.yamakotaro.ecopotions.commands.PotionShopCommand;
import com.yamakotaro.ecopotions.gui.GuiListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class EcoPotionsPlugin extends JavaPlugin {

    private YamlConfiguration config;
    private Messages messages;
    private EconomyHolder economyHolder;
    private PotionManager potionManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadPluginConfig();

        this.messages = new Messages(this);
        this.economyHolder = new EconomyHolder(this);
        economyHolder.setup();

        this.potionManager = new PotionManager(this);
        potionManager.load();

        getServer().getPluginManager().registerEvents(new GuiListener(this), this);

        PotionShopCommand commandExecutor = new PotionShopCommand(this);
        PluginCommand command = getCommand("potionshop");
        if (command != null) {
            command.setExecutor(commandExecutor);
            command.setTabCompleter(commandExecutor);
        }
    }

    /** config.yml をこのプラグイン自身のUTF-8ローダーで再読み込みする (Bukkitのconfig()は使わない)。 */
    public void reloadPluginConfig() {
        this.config = YamlIo.load(new File(getDataFolder(), "config.yml"));
    }

    /** Bukkit標準の getConfig() の代わりに使う、UTF-8で読み込んだ設定。 */
    public YamlConfiguration config() {
        return config;
    }

    public Messages getMessages() {
        return messages;
    }

    public EconomyHolder getEconomyHolder() {
        return economyHolder;
    }

    public PotionManager getPotionManager() {
        return potionManager;
    }
}
