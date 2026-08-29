package com.yamakotaro.serverkit.referral;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.Plugin;

/**
 * Isolates the Vault Economy type away from classes that don't need it, so the plugin still
 * loads cleanly (no NoClassDefFoundError) on servers without Vault installed.
 */
public class EconomyHolder {

    private final Plugin plugin;
    private Economy economy;

    public EconomyHolder(Plugin plugin) {
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
