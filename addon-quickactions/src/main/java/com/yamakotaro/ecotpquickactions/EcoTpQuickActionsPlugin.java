package com.yamakotaro.ecotpquickactions;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

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

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.messages = new Messages(this);
        this.weatherVoteManager = new WeatherVoteManager(this, messages);

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

    private int runQuickMenu(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(messages.get("players-only", Map.of()));
            return Command.SINGLE_SUCCESS;
        }
        if (!player.hasPermission("ecotpqa.quickmenu")) {
            player.sendMessage(messages.get("no-permission", Map.of()));
            return Command.SINGLE_SUCCESS;
        }
        RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.DIALOG)
                .get(EcoTpQuickActionsBootstrap.DIALOG_KEY)
                .ifPresent(player::showDialog);
        return Command.SINGLE_SUCCESS;
    }
}
