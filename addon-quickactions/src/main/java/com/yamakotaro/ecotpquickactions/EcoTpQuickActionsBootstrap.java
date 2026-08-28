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

/**
 * このアドオンのQuick Actionsダイアログは、Javaコードで組み立てるのではなく、jarに同梱した
 * データパック(src/main/resources/pack/以下)として定義している。理由:
 * - RegistryEvents.DIALOG で新しいDialogを登録すること自体は可能だが、そのDialogを
 *   バニラの #minecraft:quick_actions タグに追加する(=Gキーで自動的に開くようにする)
 *   Java APIが見当たらなかった (event.getOrCreateTag(...) は他のレジストリの項目から
 *   既存タグを「参照」するための読み取り専用ハンドルであり、タグへの追加には使えない)。
 * - タグへの追加はバニラのデータパック的な仕組み(data/minecraft/tags/dialog/quick_actions.json
 *   にmerge)でしか行えないため、ダイアログ自体もデータパックのJSONとして定義し、
 *   LifecycleEvents.DATAPACK_DISCOVERY でjar内のデータパックをサーバーに検出させている。
 */
public class EcoTpQuickActionsBootstrap implements PluginBootstrap {

    public static final Key DIALOG_KEY = Key.key("ecotpqa", "quick_actions");

    @Override
    public void bootstrap(BootstrapContext context) {
        LifecycleEventManager<BootstrapContext> manager = context.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY, event -> {
            URI packUri;
            try {
                packUri = getClass().getResource("/pack").toURI();
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
}
