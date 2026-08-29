package com.yamakotaro.ecotpquickactions;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.key.Key;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * このアドオンのQuick Actionsダイアログは、Javaコードで組み立てるのではなく、jarに同梱した
 * データパック(src/main/resources/pack_en, pack_ja 以下)として定義している。理由:
 * - RegistryEvents.DIALOG で新しいDialogを登録すること自体は可能だが、そのDialogを
 *   バニラの #minecraft:quick_actions タグに追加する(=Gキーで自動的に開くようにする)
 *   Java APIが見当たらなかった (event.getOrCreateTag(...) は他のレジストリの項目から
 *   既存タグを「参照」するための読み取り専用ハンドルであり、タグへの追加には使えない)。
 * - タグへの追加はバニラのデータパック的な仕組み(data/minecraft/tags/dialog/quick_actions.json
 *   にmerge)でしか行えないため、ダイアログ自体もデータパックのJSONとして定義し、
 *   LifecycleEvents.DATAPACK_DISCOVERY でjar内のデータパックをサーバーに検出させている。
 *
 * このダイアログのボタン文言はチャットメッセージ(config.yml の messages.<language>)とは別物で、
 * config.yml の language 設定に応じて en/ja 2種類のデータパックのどちらかをブートストラップ時に
 * 選んで登録することでローカライズしている(チャットメッセージのような実行時の切り替えではなく、
 * サーバー再起動時に choose される)。
 */
public class EcoTpQuickActionsBootstrap implements PluginBootstrap {

    public static final Key DIALOG_KEY = Key.key("ecotpqa", "quick_actions");

    @Override
    public void bootstrap(BootstrapContext context) {
        String language = detectLanguage(context);
        LifecycleEventManager<BootstrapContext> manager = context.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY, event -> {
            URI packUri;
            try {
                packUri = getClass().getResource("/pack_" + language).toURI();
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
            try {
                event.registrar().discoverPack(packUri, "ecotpqa_quick_actions");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /**
     * The bootstrap phase runs before onEnable/saveDefaultConfig, and before Bukkit's own
     * config-loading API exists for this plugin, so config.yml is read here as a plain text
     * file (no YAML library dependency needed for one "language: xx" line). A missing file
     * (fresh install) or anything other than "ja" defaults to English, matching config.yml's
     * own bundled default.
     */
    private String detectLanguage(BootstrapContext context) {
        Path configFile = context.getDataDirectory().resolve("config.yml");
        if (!Files.exists(configFile)) {
            return "en";
        }
        try {
            List<String> lines = Files.readAllLines(configFile, StandardCharsets.UTF_8);
            for (String line : lines) {
                String trimmed = line.strip();
                if (!trimmed.startsWith("language:")) {
                    continue;
                }
                String value = trimmed.substring("language:".length()).strip();
                value = value.replaceAll("^['\"]|['\"]$", "");
                return value.equalsIgnoreCase("ja") ? "ja" : "en";
            }
        } catch (IOException ignored) {
            // Fall through to the default below.
        }
        return "en";
    }
}
