package com.yamakotaro.serverkit;

import com.yamakotaro.serverkit.claims.ClaimListener;
import com.yamakotaro.serverkit.claims.ClaimManager;
import com.yamakotaro.serverkit.claims.ClaimSelectionManager;
import com.yamakotaro.serverkit.claims.TerritoryGuard;
import com.yamakotaro.serverkit.claims.commands.ClaimCommand;
import com.yamakotaro.serverkit.dragonarena.DragonArenaListener;
import com.yamakotaro.serverkit.dragonarena.DragonArenaManager;
import com.yamakotaro.serverkit.dragonarena.PartyManager;
import com.yamakotaro.serverkit.dragonarena.commands.DragonFightCommand;
import com.yamakotaro.serverkit.menu.MenuListener;
import com.yamakotaro.serverkit.referral.EconomyHolder;
import com.yamakotaro.serverkit.referral.ReferralManager;
import com.yamakotaro.serverkit.referral.commands.ReferralCommand;
import com.yamakotaro.serverkit.staff.FreezeManager;
import com.yamakotaro.serverkit.staff.StaffChatManager;
import com.yamakotaro.serverkit.staff.StaffListener;
import com.yamakotaro.serverkit.staff.VanishManager;
import com.yamakotaro.serverkit.staff.commands.FreezeCommand;
import com.yamakotaro.serverkit.staff.commands.StaffChatCommand;
import com.yamakotaro.serverkit.staff.commands.VanishCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Standalone plugin, independent of EcoTP: staff tools, an invite-referral reward system,
 * solo/team Ender Dragon arena fights, and land claims. Each module can be switched off in
 * config.yml. Module managers are kept as (nullable, when their module is off) fields here so
 * the cross-module /serverkit menu can reach all of them from one place.
 */
public class ServerKitPlugin extends JavaPlugin {

    private ReferralManager referralManager;
    private VanishManager vanishManager;
    private FreezeManager freezeManager;
    private StaffChatManager staffChatManager;
    private DragonArenaManager dragonArenaManager;
    private PartyManager partyManager;
    private ClaimManager claimManager;
    private ClaimSelectionManager claimSelectionManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Messages messages = new Messages(this);
        ServerKitCommand serverKitCommand = new ServerKitCommand(this, messages);
        getCommand("serverkit").setExecutor(serverKitCommand);
        getCommand("serverkit").setTabCompleter(serverKitCommand);

        if (getConfig().getBoolean("modules.staff", true)) {
            this.vanishManager = new VanishManager(this);
            this.freezeManager = new FreezeManager();
            this.staffChatManager = new StaffChatManager(this, messages);
            getServer().getPluginManager().registerEvents(
                    new StaffListener(vanishManager, freezeManager, staffChatManager, messages), this);
            getCommand("vanish").setExecutor(new VanishCommand(vanishManager, messages));
            FreezeCommand freezeCommand = new FreezeCommand(freezeManager, messages);
            getCommand("freeze").setExecutor(freezeCommand);
            getCommand("freeze").setTabCompleter(freezeCommand);
            getCommand("staffchat").setExecutor(new StaffChatCommand(staffChatManager, messages));
        }

        if (getConfig().getBoolean("modules.referral", true)) {
            EconomyHolder economyHolder = new EconomyHolder(this);
            economyHolder.setup();
            this.referralManager = new ReferralManager(this, economyHolder);
            referralManager.load();
            ReferralCommand referralCommand = new ReferralCommand(referralManager, messages);
            getCommand("referral").setExecutor(referralCommand);
            getCommand("referral").setTabCompleter(referralCommand);
        }

        if (getConfig().getBoolean("modules.dragonarena", true)) {
            this.partyManager = new PartyManager();
            this.dragonArenaManager = new DragonArenaManager(this, messages, partyManager);
            getServer().getPluginManager().registerEvents(new DragonArenaListener(dragonArenaManager, messages), this);
            DragonFightCommand dragonFightCommand = new DragonFightCommand(this, dragonArenaManager, partyManager, messages);
            getCommand("dragonfight").setExecutor(dragonFightCommand);
            getCommand("dragonfight").setTabCompleter(dragonFightCommand);
        }

        if (getConfig().getBoolean("modules.claims", true)) {
            TerritoryGuard territoryGuard = new TerritoryGuard(this);
            this.claimManager = new ClaimManager(this, territoryGuard);
            this.claimSelectionManager = new ClaimSelectionManager(this, messages);
            getServer().getPluginManager().registerEvents(
                    new ClaimListener(this, claimManager, claimSelectionManager, messages), this);
            ClaimCommand claimCommand = new ClaimCommand(claimManager, claimSelectionManager, messages);
            getCommand("claim").setExecutor(claimCommand);
            getCommand("claim").setTabCompleter(claimCommand);

            long accrualAmount = claimManager.accrualAmount();
            long intervalTicks = claimManager.accrualIntervalMinutes() * 60L * 20L;
            getServer().getScheduler().runTaskTimer(this, () -> {
                for (var player : getServer().getOnlinePlayers()) {
                    claimManager.addBlocks(player.getUniqueId(), accrualAmount);
                }
            }, intervalTicks, intervalTicks);
        }

        getServer().getPluginManager().registerEvents(new MenuListener(this, messages), this);
    }

    @Override
    public void onDisable() {
        if (referralManager != null) {
            referralManager.save();
        }
    }

    public VanishManager getVanishManager() {
        return vanishManager;
    }

    public FreezeManager getFreezeManager() {
        return freezeManager;
    }

    public StaffChatManager getStaffChatManager() {
        return staffChatManager;
    }

    public DragonArenaManager getDragonArenaManager() {
        return dragonArenaManager;
    }

    public PartyManager getPartyManager() {
        return partyManager;
    }

    public ClaimManager getClaimManager() {
        return claimManager;
    }

    public ClaimSelectionManager getClaimSelectionManager() {
        return claimSelectionManager;
    }
}
