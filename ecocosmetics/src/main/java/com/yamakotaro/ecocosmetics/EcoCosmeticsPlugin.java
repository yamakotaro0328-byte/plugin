package com.yamakotaro.ecocosmetics;

import com.yamakotaro.ecocosmetics.commands.CosmeticsCommand;
import com.yamakotaro.ecocosmetics.gui.GuiListener;
import com.yamakotaro.ecocosmetics.listeners.CosmeticEffectListener;
import com.yamakotaro.ecocosmetics.tasks.ParticleTrailTask;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class EcoCosmeticsPlugin extends JavaPlugin {

    private YamlConfiguration config;
    private Messages messages;
    private EconomyHolder economyHolder;
    private CosmeticManager cosmeticManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadPluginConfig();

        this.messages = new Messages(this);
        this.economyHolder = new EconomyHolder(this);
        economyHolder.setup();

        this.cosmeticManager = new CosmeticManager(this);
        cosmeticManager.load();

        getServer().getPluginManager().registerEvents(new CosmeticEffectListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);

        getServer().getScheduler().runTaskTimer(this, new ParticleTrailTask(this), 20L, 10L);

        CosmeticsCommand commandExecutor = new CosmeticsCommand(this);
        PluginCommand command = getCommand("cosmetics");
        if (command != null) {
            command.setExecutor(commandExecutor);
            command.setTabCompleter(commandExecutor);
        }
    }

    @Override
    public void onDisable() {
        if (cosmeticManager != null) {
            cosmeticManager.save();
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

    public Messages messages() {
        return messages;
    }

    public EconomyHolder getEconomyHolder() {
        return economyHolder;
    }

    public CosmeticManager getCosmeticManager() {
        return cosmeticManager;
    }
}
