package com.yamakotaro.serverkit;

import com.yamakotaro.serverkit.dragonarena.DragonArenaListener;
import com.yamakotaro.serverkit.dragonarena.DragonArenaManager;
import com.yamakotaro.serverkit.dragonarena.PartyManager;
import com.yamakotaro.serverkit.dragonarena.commands.DragonFightCommand;
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
 * Standalone plugin, independent of EcoTP: staff tools, an invite-referral reward system, and
 * solo/team Ender Dragon arena fights. Each module can be switched off in config.yml.
 */
public class ServerKitPlugin extends JavaPlugin {

    private ReferralManager referralManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Messages messages = new Messages(this);
        getCommand("serverkit").setExecutor(new ServerKitCommand(this, messages));

        if (getConfig().getBoolean("modules.staff", true)) {
            VanishManager vanishManager = new VanishManager(this);
            FreezeManager freezeManager = new FreezeManager();
            StaffChatManager staffChatManager = new StaffChatManager(this, messages);
            getServer().getPluginManager().registerEvents(
                    new StaffListener(vanishManager, freezeManager, staffChatManager, messages), this);
            getCommand("vanish").setExecutor(new VanishCommand(vanishManager, messages));
            getCommand("freeze").setExecutor(new FreezeCommand(freezeManager, messages));
            getCommand("staffchat").setExecutor(new StaffChatCommand(staffChatManager, messages));
        }

        if (getConfig().getBoolean("modules.referral", true)) {
            EconomyHolder economyHolder = new EconomyHolder(this);
            economyHolder.setup();
            this.referralManager = new ReferralManager(this, economyHolder);
            referralManager.load();
            getCommand("referral").setExecutor(new ReferralCommand(referralManager, messages));
        }

        if (getConfig().getBoolean("modules.dragonarena", true)) {
            PartyManager partyManager = new PartyManager();
            DragonArenaManager arenaManager = new DragonArenaManager(this, messages, partyManager);
            getServer().getPluginManager().registerEvents(new DragonArenaListener(arenaManager, messages), this);
            getCommand("dragonfight").setExecutor(new DragonFightCommand(this, arenaManager, partyManager, messages));
        }
    }

    @Override
    public void onDisable() {
        if (referralManager != null) {
            referralManager.save();
        }
    }
}
