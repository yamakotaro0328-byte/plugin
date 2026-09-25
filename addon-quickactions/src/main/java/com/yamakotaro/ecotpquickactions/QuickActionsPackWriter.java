package com.yamakotaro.ecotpquickactions;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Stream;

/**
 * menu.yml の g-key 定義から、バニラの #minecraft:quick_actions タグに入るダイアログを持つデータパックを
 * プラグインフォルダ内に書き出す(タグへの追加はデータパック経由でしか確実に行えないため)。
 * 各ボタンは "quickmenu run &lt;id&gt;" を実行し、権限・機能ON/OFF・プレースホルダーの処理は
 * クリック時にサーバー側で行う。
 */
final class QuickActionsPackWriter {

    private QuickActionsPackWriter() {
    }

    static Path write(Path dataDirectory, MenuDefinition menu, String language, Set<String> disabledFeatures)
            throws IOException {
        Path root = dataDirectory.resolve(".generated").resolve("quick_actions_pack");
        deleteRecursively(root);
        Path dialogDir = root.resolve("data/ecotpqa/dialog");
        Path tagDir = root.resolve("data/minecraft/tags/dialog");
        Files.createDirectories(dialogDir);
        Files.createDirectories(tagDir);

        JsonObject mcmeta = new JsonObject();
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", 1);
        JsonObject formats = new JsonObject();
        formats.addProperty("min_inclusive", 1);
        formats.addProperty("max_inclusive", Integer.MAX_VALUE);
        pack.add("supported_formats", formats);
        pack.addProperty("description", "EcoTP-QuickActions: generated Quick Actions (G key) dialog");
        mcmeta.add("pack", pack);
        Files.writeString(root.resolve("pack.mcmeta"), mcmeta.toString(), StandardCharsets.UTF_8);

        JsonObject tag = new JsonObject();
        JsonArray values = new JsonArray();
        values.add(EcoTpQuickActionsBootstrap.DIALOG_KEY.asString());
        tag.add("values", values);
        Files.writeString(tagDir.resolve("quick_actions.json"), tag.toString(), StandardCharsets.UTF_8);

        JsonObject dialog = new JsonObject();
        dialog.addProperty("type", "minecraft:multi_action");
        dialog.add("title", component(MenuDefinition.localized(menu.title(), language, "EcoTP")));
        JsonArray body = new JsonArray();
        JsonObject message = new JsonObject();
        message.addProperty("type", "minecraft:plain_message");
        message.add("contents", component(language.equals("ja") ? "クイックアクション" : "Quick actions"));
        body.add(message);
        dialog.add("body", body);
        dialog.addProperty("columns", menu.gKeyColumns());

        JsonArray actions = new JsonArray();
        for (String id : menu.gKeyButtons()) {
            MenuDefinition.Button button = menu.buttons().get(id);
            if (button == null || (button.requires() != null && disabledFeatures.contains(button.requires()))) {
                continue;
            }
            JsonObject entry = new JsonObject();
            entry.add("label", component(MenuDefinition.localized(button.label(), language, id)));
            String tooltip = MenuDefinition.localized(button.tooltip(), language, null);
            if (tooltip != null && !tooltip.isBlank()) {
                entry.add("tooltip", component(tooltip));
            }
            JsonObject action = new JsonObject();
            action.addProperty("type", "minecraft:run_command");
            action.addProperty("command", "quickmenu run " + id);
            entry.add("action", action);
            actions.add(entry);
        }
        dialog.add("actions", actions);
        Files.writeString(dialogDir.resolve(EcoTpQuickActionsBootstrap.DIALOG_KEY.value() + ".json"),
                dialog.toString(), StandardCharsets.UTF_8);
        return root;
    }

    private static JsonElement component(String legacyText) {
        return JsonParser.parseString(GsonComponentSerializer.gson()
                .serialize(LegacyComponentSerializer.legacyAmpersand().deserialize(legacyText)));
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }
}
