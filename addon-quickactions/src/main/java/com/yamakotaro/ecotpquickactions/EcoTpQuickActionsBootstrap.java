package com.yamakotaro.ecotpquickactions;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * バニラの #minecraft:quick_actions タグ(Gキー)への追加は、データパックの
 * data/minecraft/tags/dialog/quick_actions.json 経由でしか確実に行えないため、
 * menu.yml の g-key 定義から起動時にデータパックを書き出し、DATAPACK_DISCOVERY で読み込ませる。
 * ブートストラップ段階ではプラグインの getConfig() がまだ無いので、config.yml / menu.yml は
 * YamlConfiguration を純粋なパーサーとして直接読む(未作成ならjar同梱のデフォルト)。
 */
public class EcoTpQuickActionsBootstrap implements PluginBootstrap {

    public static final Key DIALOG_KEY = Key.key("ecotpqa", "quick_actions");

    @Override
    public void bootstrap(BootstrapContext context) {
        Path dataDirectory = context.getDataDirectory();
        YamlConfiguration config = MenuDefinition.loadYaml(dataDirectory, "config.yml");
        String language = "ja".equalsIgnoreCase(config.getString("language", "en")) ? "ja" : "en";
        Set<String> disabled = new HashSet<>();
        if (!config.getBoolean("admin-shop.enabled", true)) {
            disabled.add("adminshop");
        }
        if (!config.getBoolean("player-shop.enabled", true)) {
            disabled.add("playershop");
        }
        if (!config.getBoolean("weather-vote.enabled", true)) {
            disabled.add("weathervote");
        }
        MenuDefinition menu = MenuDefinition.parse(MenuDefinition.loadYaml(dataDirectory, "menu.yml"));

        context.getLifecycleManager().registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY, event -> {
            try {
                Path pack = QuickActionsPackWriter.write(dataDirectory, menu, language, disabled);
                event.registrar().discoverPack(pack, "ecotpqa_quick_actions");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
