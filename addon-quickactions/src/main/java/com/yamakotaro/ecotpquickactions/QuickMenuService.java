package com.yamakotaro.ecotpquickactions;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * /quickmenu の動的ダイアログ。静的なGキーダイアログと違い、プレイヤーごとに
 * 権限・機能ON/OFFでボタンを絞り込み、所持金や現在地などのステータスを表示し、
 * プレイヤー選択→TPA/呼び寄せ/送金(金額入力付き)といった対話的な操作を提供する。
 */
public class QuickMenuService {

    private static final int PLAYERS_PER_PAGE = 16;
    private static final int BUTTON_WIDTH = 150;
    private static final ClickCallback.Options CALLBACK_OPTIONS = ClickCallback.Options.builder()
            .uses(ClickCallback.UNLIMITED_USES)
            .lifetime(Duration.ofMinutes(10))
            .build();

    private final EcoTpQuickActionsPlugin plugin;
    private final Messages messages;
    private final EconomyHolder economy;
    private final AdminShopManager adminShop;
    private final PlayerShopManager playerShop;
    private MenuDefinition menu;

    public QuickMenuService(EcoTpQuickActionsPlugin plugin, Messages messages, EconomyHolder economy,
                            AdminShopManager adminShop, PlayerShopManager playerShop) {
        this.plugin = plugin;
        this.messages = messages;
        this.economy = economy;
        this.adminShop = adminShop;
        this.playerShop = playerShop;
        reload();
    }

    public void reload() {
        this.menu = MenuDefinition.parse(MenuDefinition.loadYaml(plugin.getDataFolder().toPath(), "menu.yml"));
    }

    public MenuDefinition menu() {
        return menu;
    }

    private String language() {
        return "ja".equalsIgnoreCase(plugin.getConfig().getString("language", "en")) ? "ja" : "en";
    }

    private boolean featureEnabled(String feature) {
        return switch (feature) {
            case "adminshop" -> adminShop.isEnabled();
            case "playershop" -> playerShop.isEnabled();
            case "weathervote" -> plugin.getConfig().getBoolean("weather-vote.enabled", true);
            case "economy" -> economy.available();
            default -> true;
        };
    }

    private boolean visible(Player player, MenuDefinition.Button button) {
        if (button.permission() != null && !player.hasPermission(button.permission())) {
            return false;
        }
        if (button.requires() != null && !featureEnabled(button.requires())) {
            return false;
        }
        return button.type() != MenuDefinition.ActionType.MENU || menu.categories().containsKey(button.value());
    }

    /** ボタンを実行する。Gキーからは "/quickmenu run <id>" 経由、動的ダイアログからは直接呼ばれる。 */
    public void runButton(Player player, String id, String target) {
        MenuDefinition.Button button = menu.buttons().get(id);
        if (button == null) {
            player.sendMessage(messages.get("quickmenu.unknown", Map.of("id", id)));
            return;
        }
        if (!visible(player, button)) {
            player.sendMessage(messages.get("quickmenu.unavailable", Map.of()));
            return;
        }
        switch (button.type()) {
            case COMMAND -> {
                String command = button.value().replace("{player}", player.getName());
                if (command.contains("{target}")) {
                    if (target == null) {
                        openPlayerList(player, 0);
                        return;
                    }
                    command = command.replace("{target}", target);
                }
                player.performCommand(command);
            }
            case MENU -> openCategory(player, button.value());
            case BUILTIN -> {
                switch (button.value()) {
                    case "hub" -> openHub(player);
                    case "players" -> openPlayerList(player, 0);
                    case "pay" -> {
                        if (target == null) {
                            openPlayerList(player, 0);
                        } else {
                            openPay(player, target);
                        }
                    }
                    default -> player.sendMessage(messages.get("quickmenu.unknown", Map.of("id", id)));
                }
            }
        }
    }

    public void openHub(Player player) {
        List<ActionButton> buttons = new ArrayList<>();
        for (String categoryId : menu.hubCategories()) {
            MenuDefinition.Category category = menu.categories().get(categoryId);
            if (category == null || category.buttons().stream().noneMatch(id -> isVisible(player, id))) {
                continue;
            }
            buttons.add(button(text(label(category.label(), categoryId)), null, p -> openCategory(p, categoryId)));
        }
        Location loc = player.getLocation();
        String status = messages.raw("quickmenu.status", Map.of(
                "player", player.getName(),
                "balance", economy.formattedBalance(player),
                "world", loc.getWorld().getName(),
                "x", String.valueOf(loc.getBlockX()),
                "y", String.valueOf(loc.getBlockY()),
                "z", String.valueOf(loc.getBlockZ()),
                "online", String.valueOf(plugin.getServer().getOnlinePlayers().size())));
        player.showDialog(dialog(title(null), List.of(DialogBody.plainMessage(text(status))), List.of(),
                buttons, menu.hubColumns(), button(text(messages.raw("quickmenu.close", Map.of())), null, p -> { })));
    }

    public void openCategory(Player player, String categoryId) {
        MenuDefinition.Category category = menu.categories().get(categoryId);
        if (category == null) {
            openHub(player);
            return;
        }
        List<ActionButton> buttons = new ArrayList<>();
        for (String id : category.buttons()) {
            MenuDefinition.Button b = menu.buttons().get(id);
            if (b != null && visible(player, b)) {
                buttons.add(button(text(label(b.label(), id)), tooltip(b), p -> runButton(p, id, null)));
            }
        }
        player.showDialog(dialog(title(label(category.label(), categoryId)), List.of(), List.of(), buttons,
                category.columns(), backButton(this::openHub)));
    }

    public void openPlayerList(Player player, int page) {
        List<Player> others = plugin.getServer().getOnlinePlayers().stream()
                .filter(o -> !o.equals(player) && player.canSee(o))
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .map(o -> (Player) o)
                .toList();
        int pages = Math.max(1, (others.size() + PLAYERS_PER_PAGE - 1) / PLAYERS_PER_PAGE);
        int current = Math.max(0, Math.min(page, pages - 1));
        List<ActionButton> buttons = new ArrayList<>();
        for (Player other : others.subList(current * PLAYERS_PER_PAGE,
                Math.min(others.size(), (current + 1) * PLAYERS_PER_PAGE))) {
            String name = other.getName();
            buttons.add(button(Component.text(name), null, p -> openPlayerActions(p, name)));
        }
        if (current > 0) {
            buttons.add(button(text(messages.raw("quickmenu.prev", Map.of())), null, p -> openPlayerList(p, current - 1)));
        }
        if (current < pages - 1) {
            buttons.add(button(text(messages.raw("quickmenu.next", Map.of())), null, p -> openPlayerList(p, current + 1)));
        }
        String bodyKey = others.isEmpty() ? "quickmenu.no-players" : "quickmenu.players-page";
        player.showDialog(dialog(title(messages.raw("quickmenu.players-title", Map.of())),
                List.of(DialogBody.plainMessage(text(messages.raw(bodyKey, Map.of(
                        "page", String.valueOf(current + 1), "pages", String.valueOf(pages)))))),
                List.of(), buttons, 2, backButton(this::openHub)));
    }

    public void openPlayerActions(Player player, String targetName) {
        Player target = plugin.getServer().getPlayerExact(targetName);
        if (target == null || !player.canSee(target)) {
            player.sendMessage(messages.get("quickmenu.player-offline", Map.of("target", targetName)));
            openPlayerList(player, 0);
            return;
        }
        List<ActionButton> buttons = new ArrayList<>();
        for (String id : menu.playerActions()) {
            MenuDefinition.Button b = menu.buttons().get(id);
            if (b != null && visible(player, b)) {
                buttons.add(button(text(label(b.label(), id)), tooltip(b), p -> runButton(p, id, targetName)));
            }
        }
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(target);
            head.setItemMeta(meta);
        }
        String info = messages.raw("quickmenu.player-info", Map.of(
                "target", target.getName(), "world", target.getWorld().getName()));
        player.showDialog(dialog(title(target.getName()),
                List.of(DialogBody.item(head, DialogBody.plainMessage(text(info)), false, false, 16, 16)),
                List.of(), buttons, 2, backButton(p -> openPlayerList(p, 0))));
    }

    public void openPay(Player player, String targetName) {
        if (!economy.available()) {
            player.sendMessage(messages.get("quickmenu.unavailable", Map.of()));
            return;
        }
        List<ActionButton> buttons = new ArrayList<>();
        for (double preset : menu.payPresets()) {
            buttons.add(button(text(messages.raw("quickmenu.pay-preset", Map.of("amount", economy.format(preset)))),
                    null, p -> pay(p, targetName, preset)));
        }
        buttons.add(responseButton(text(messages.raw("quickmenu.pay-custom", Map.of())), (p, response) -> {
            String raw = response.getText("amount");
            double amount;
            try {
                amount = raw == null ? -1 : Double.parseDouble(raw.replace(",", "").strip());
            } catch (NumberFormatException e) {
                amount = -1;
            }
            pay(p, targetName, amount);
        }));
        String body = messages.raw("quickmenu.pay-body", Map.of(
                "target", targetName, "balance", economy.formattedBalance(player)));
        DialogInput amountInput = DialogInput.text("amount", text(messages.raw("quickmenu.pay-amount-label", Map.of())))
                .maxLength(16)
                .build();
        player.showDialog(dialog(title(messages.raw("quickmenu.pay-title", Map.of("target", targetName))),
                List.of(DialogBody.plainMessage(text(body))), List.of(amountInput), buttons, 2,
                backButton(p -> openPlayerActions(p, targetName))));
    }

    private void pay(Player player, String target, double amount) {
        if (!(amount > 0) || Double.isInfinite(amount)) {
            player.sendMessage(messages.get("quickmenu.invalid-amount", Map.of()));
            openPay(player, target);
            return;
        }
        player.performCommand("pay " + target + " " + BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString());
    }

    private boolean isVisible(Player player, String buttonId) {
        MenuDefinition.Button b = menu.buttons().get(buttonId);
        return b != null && visible(player, b);
    }

    private Dialog dialog(Component title, List<DialogBody> body, List<DialogInput> inputs,
                          List<ActionButton> buttons, int columns, ActionButton exit) {
        List<ActionButton> actions = buttons.isEmpty()
                ? List.of(button(text(messages.raw("quickmenu.empty", Map.of())), null, p -> { }))
                : buttons;
        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title).canCloseWithEscape(true).body(body).inputs(inputs).build())
                .type(DialogType.multiAction(actions).columns(columns).exitAction(exit).build()));
    }

    private ActionButton backButton(Consumer<Player> onClick) {
        return button(text(messages.raw("quickmenu.back", Map.of())), null, onClick);
    }

    private ActionButton button(Component label, Component tooltip, Consumer<Player> onClick) {
        return responseButton(label, tooltip, (player, response) -> onClick.accept(player));
    }

    private ActionButton responseButton(Component label, BiConsumer<Player, DialogResponseView> onClick) {
        return responseButton(label, null, onClick);
    }

    /** クリック処理はコマンド実行やワールド参照を伴うため、必ずメインスレッドで実行する。 */
    private ActionButton responseButton(Component label, Component tooltip,
                                        BiConsumer<Player, DialogResponseView> onClick) {
        ActionButton.Builder builder = ActionButton.builder(label).width(BUTTON_WIDTH)
                .action(DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player player) {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (player.isOnline()) {
                                onClick.accept(player, response);
                            }
                        });
                    }
                }, CALLBACK_OPTIONS));
        if (tooltip != null) {
            builder.tooltip(tooltip);
        }
        return builder.build();
    }

    private Component title(String suffix) {
        String base = MenuDefinition.localized(menu.title(), language(), "EcoTP");
        return text(suffix == null ? base : base + " - " + suffix);
    }

    private Component tooltip(MenuDefinition.Button button) {
        String tip = MenuDefinition.localized(button.tooltip(), language(), null);
        return tip == null || tip.isBlank() ? null : text(tip);
    }

    private String label(Map<String, String> texts, String fallback) {
        return MenuDefinition.localized(texts, language(), fallback);
    }

    private static Component text(String legacy) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(legacy);
    }

    public List<String> categoryIds() {
        return new ArrayList<>(menu.categories().keySet());
    }

    public List<String> buttonIds() {
        return new ArrayList<>(menu.buttons().keySet());
    }
}
