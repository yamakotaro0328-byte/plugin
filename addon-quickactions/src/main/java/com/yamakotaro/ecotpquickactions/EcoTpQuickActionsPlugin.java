package com.yamakotaro.ecotpquickactions;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * このプラグイン自体はEcoTPのJavaクラスを一切参照しない。/quickmenu ダイアログの各ボタンは
 * 実際のコマンド("home"・"menu"等)を実行するだけなので、EcoTPが入っていなくても
 * (ボタンを押した結果コマンドが見つからないだけで)エラーにはならない。
 */
public class EcoTpQuickActionsPlugin extends JavaPlugin {

    private Messages messages;
    private WeatherVoteManager weatherVoteManager;
    private EconomyHolder economyHolder;
    private AdminShopManager adminShopManager;
    private PlayerShopManager playerShopManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.messages = new Messages(this);
        this.weatherVoteManager = new WeatherVoteManager(this, messages);
        this.economyHolder = new EconomyHolder(this);
        economyHolder.setup();
        this.adminShopManager = new AdminShopManager(this, economyHolder, messages);
        this.playerShopManager = new PlayerShopManager(this, economyHolder, messages);
        getServer().getPluginManager().registerEvents(new NumberInputListener(), this);
        getServer().getPluginManager().registerEvents(
                new AdminShopListener(this, adminShopManager, messages), this);
        getServer().getPluginManager().registerEvents(
                new PlayerShopListener(this, playerShopManager, messages), this);

        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var registrar = event.registrar();

            registrar.register(
                    Commands.literal("weathervote")
                            .then(Commands.literal("clear")
                                    .executes(ctx -> runWeatherVote(ctx.getSource(), WeatherVoteManager.Weather.CLEAR)))
                            .then(Commands.literal("rain")
                                    .executes(ctx -> runWeatherVote(ctx.getSource(), WeatherVoteManager.Weather.RAIN)))
                            .executes(ctx -> {
                                ctx.getSource().getSender().sendMessage(messages.get("usage", Map.of()));
                                return Command.SINGLE_SUCCESS;
                            })
                            .build(),
                    "Vote to change the weather",
                    List.of("wv"));

            registrar.register(
                    Commands.literal("quickmenu")
                            .executes(this::runQuickMenu)
                            .build(),
                    "Open the EcoTP quick actions dialog");

            registrar.register(
                    Commands.literal("adminshop")
                            .then(Commands.literal("admin")
                                    .executes(this::runAdminShopAdmin))
                            .executes(this::runAdminShop)
                            .build(),
                    "Open the admin shop");

            registrar.register(
                    Commands.literal("pshop")
                            .then(Commands.literal("browse").executes(this::runPlayerShopBrowse))
                            .then(Commands.literal("my").executes(this::runPlayerShopMy))
                            .then(Commands.literal("history").executes(this::runPlayerShopHistory))
                            .executes(this::runPlayerShopBrowse)
                            .build(),
                    "Player-to-player shop",
                    List.of("ps"));
        });
    }

    private int runWeatherVote(CommandSourceStack source, WeatherVoteManager.Weather weather) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(messages.get("players-only", Map.of()));
            return Command.SINGLE_SUCCESS;
        }
        if (!player.hasPermission("ecotpqa.weathervote")) {
            player.sendMessage(messages.get("no-permission", Map.of()));
            return Command.SINGLE_SUCCESS;
        }
        weatherVoteManager.startOrJoin(player, weather);
        return Command.SINGLE_SUCCESS;
    }

    private int runQuickMenu(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(messages.get("players-only", Map.of()));
            return Command.SINGLE_SUCCESS;
        }
        if (!player.hasPermission("ecotpqa.quickmenu")) {
            player.sendMessage(messages.get("no-permission", Map.of()));
            return Command.SINGLE_SUCCESS;
        }
        var dialog = RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.DIALOG)
                .get(EcoTpQuickActionsBootstrap.DIALOG_KEY);
        if (dialog != null) {
            player.showDialog(dialog);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int runAdminShop(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(messages.get("players-only", Map.of()));
            return Command.SINGLE_SUCCESS;
        }
        if (!player.hasPermission("ecotpqa.adminshop")) {
            player.sendMessage(messages.get("no-permission", Map.of()));
            return Command.SINGLE_SUCCESS;
        }
        if (!adminShopManager.isEnabled()) {
            player.sendMessage(messages.get("adminshop.feature-disabled", Map.of()));
            return Command.SINGLE_SUCCESS;
        }
        player.openInventory(new AdminShopHolder(AdminShopHolder.Mode.SHOP, adminShopManager, messages,
                messages.get("adminshop.title", Map.of())).getInventory());
        return Command.SINGLE_SUCCESS;
    }

    private int runAdminShopAdmin(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(messages.get("players-only", Map.of()));
            return Command.SINGLE_SUCCESS;
        }
        if (!player.hasPermission("ecotpqa.adminshop.admin")) {
            player.sendMessage(messages.get("no-permission", Map.of()));
            return Command.SINGLE_SUCCESS;
        }
        if (!adminShopManager.isEnabled()) {
            player.sendMessage(messages.get("adminshop.feature-disabled", Map.of()));
            return Command.SINGLE_SUCCESS;
        }
        player.openInventory(new AdminShopHolder(AdminShopHolder.Mode.ADMIN, adminShopManager, messages,
                messages.get("adminshop.admin-title", Map.of())).getInventory());
        return Command.SINGLE_SUCCESS;
    }

    private int runPlayerShopBrowse(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(messages.get("players-only", Map.of()));
            return Command.SINGLE_SUCCESS;
        }
        if (!player.hasPermission("ecotpqa.playershop")) {
            player.sendMessage(messages.get("no-permission", Map.of()));
            return Command.SINGLE_SUCCESS;
        }
        if (!playerShopManager.isEnabled()) {
            player.sendMessage(messages.get("playershop.feature-disabled", Map.of()));
            return Command.SINGLE_SUCCESS;
        }
        player.openInventory(new PlayerShopHolder(PlayerShopHolder.Mode.BROWSE, playerShopManager, messages,
                messages.get("playershop.browse-title", Map.of()), player.getUniqueId()).getInventory());
        return Command.SINGLE_SUCCESS;
    }

    private int runPlayerShopMy(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(messages.get("players-only", Map.of()));
            return Command.SINGLE_SUCCESS;
        }
        if (!player.hasPermission("ecotpqa.playershop")) {
            player.sendMessage(messages.get("no-permission", Map.of()));
            return Command.SINGLE_SUCCESS;
        }
        if (!playerShopManager.isEnabled()) {
            player.sendMessage(messages.get("playershop.feature-disabled", Map.of()));
            return Command.SINGLE_SUCCESS;
        }
        player.openInventory(new PlayerShopHolder(PlayerShopHolder.Mode.MY, playerShopManager, messages,
                messages.get("playershop.my-title", Map.of()), player.getUniqueId()).getInventory());
        return Command.SINGLE_SUCCESS;
    }

    private int runPlayerShopHistory(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(messages.get("players-only", Map.of()));
            return Command.SINGLE_SUCCESS;
        }
        if (!player.hasPermission("ecotpqa.playershop")) {
            player.sendMessage(messages.get("no-permission", Map.of()));
            return Command.SINGLE_SUCCESS;
        }
        List<PlayerShopManager.HistoryEntry> history = playerShopManager.historyFor(player.getUniqueId());
        if (history.isEmpty()) {
            player.sendMessage(messages.get("playershop.history-empty", Map.of()));
            return Command.SINGLE_SUCCESS;
        }
        player.sendMessage(messages.get("playershop.history-header", Map.of()));
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        for (PlayerShopManager.HistoryEntry entry : history) {
            String key = entry.bought() ? "playershop.history-entry-bought" : "playershop.history-entry-sold";
            player.sendMessage(messages.get(key, Map.of(
                    "time", format.format(new Date(entry.timestamp())),
                    "amount", String.valueOf(entry.amount()),
                    "material", entry.item().getType().name(),
                    "price", PlayerShopManager.formatMoney(entry.total()),
                    "player", entry.counterpartyName())));
        }
        return Command.SINGLE_SUCCESS;
    }
}
