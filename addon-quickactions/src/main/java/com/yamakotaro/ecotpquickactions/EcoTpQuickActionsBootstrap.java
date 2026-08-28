package com.yamakotaro.ecotpquickactions;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.tag.DialogTagKeys;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

import java.util.List;

/**
 * サーバー起動の一番早い段階(全プラグインのロードより前)で、独自のDialogをレジストリに
 * 登録し、バニラのQuick Actions(Gキー)タグ(minecraft:quick_actions)に加える。
 * これによりプレイヤーがGキーを押すだけでこのダイアログが開くようになる
 * (コマンド実行やイベント購読は一切不要、クライアント側が直接タグを参照する)。
 */
public class EcoTpQuickActionsBootstrap implements PluginBootstrap {

    public static final Key DIALOG_KEY = Key.key("ecotpqa", "quick_actions");

    @Override
    public void bootstrap(BootstrapContext context) {
        LifecycleEventManager<BootstrapContext> manager = context.getLifecycleManager();
        manager.registerEventHandler(RegistryEvents.DIALOG.compose().newHandler(event -> {
            TypedKey<Dialog> key = TypedKey.create(RegistryKey.DIALOG, DIALOG_KEY);
            event.registry().register(key, builder -> builder
                    .base(DialogBase.builder(Component.text("EcoTP"))
                            .body(List.of(DialogBody.plainMessage(Component.text("Quick actions"))))
                            .canCloseWithEscape(true)
                            .build())
                    .type(DialogType.multiAction(List.of(
                            actionButton("Home", "home"),
                            actionButton("Set Home", "sethome"),
                            actionButton("Spawn", "spawn"),
                            actionButton("Balance", "balance"),
                            actionButton("Ranking", "baltop"),
                            actionButton("Menu (TPA / TPHere / Pay)", "menu"),
                            actionButton("Vote: Clear weather", "weathervote clear"),
                            actionButton("Vote: Rain", "weathervote rain")
                    ))));

            event.getOrCreateTag(DialogTagKeys.QUICK_ACTIONS).add(key);
        }));
    }

    private static ActionButton actionButton(String label, String commandTemplate) {
        return ActionButton.builder(Component.text(label))
                .action(DialogAction.commandTemplate(commandTemplate))
                .build();
    }
}
