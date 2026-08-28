package com.yamakotaro.ecotp;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * このプラグイン自身が持つ経済システム本体。
 * Vault の Economy インターフェースを実装しているので、Vault が導入されていれば
 * 他のプラグイン (ショップ等) からもこの経済にアクセスできる。
 * 銀行 (bank) 機能は未対応。
 */
public class EcoTpEconomy implements Economy {

    private final EcoTpPlugin plugin;
    private final BalanceStorage balances;
    private final EssentialsImporter essentialsImporter;

    public EcoTpEconomy(EcoTpPlugin plugin, BalanceStorage balances, EssentialsImporter essentialsImporter) {
        this.plugin = plugin;
        this.balances = balances;
        this.essentialsImporter = essentialsImporter;
    }

    /**
     * このプレイヤーの口座がまだ無ければ作成する。
     * Essentials の旧データがあればその金額を、無ければ config の初期所持金を使う。
     */
    public void ensureAccount(UUID uuid, String name) {
        if (balances.hasAccount(uuid)) {
            return;
        }
        Double imported = essentialsImporter.tryImportBalance(uuid);
        double initial = imported != null ? imported : plugin.getConfig().getDouble("starting-balance", 0.0);
        balances.createAccount(uuid, name, initial);
        if (imported != null) {
            plugin.getLogger().info((name != null ? name : uuid) + " の所持金を Essentials から引き継ぎました (" + ChatUtil.formatMoney(initial) + ")");
        }
    }

    /**
     * /baltop 用。所持金が多い順に上位 limit 件を返す。
     */
    public List<BalanceEntry> getTopBalances(int limit) {
        return balances.getTopBalances(limit);
    }

    private UUID resolveUuid(String playerName) {
        return balances.findUuidByName(playerName)
                .orElseGet(() -> Bukkit.getOfflinePlayer(playerName).getUniqueId());
    }

    // ---- 基本情報 ----

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getName() {
        return "EcoTP";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return 0;
    }

    @Override
    public String format(double amount) {
        return ChatUtil.formatMoney(amount);
    }

    @Override
    public String currencyNamePlural() {
        return plugin.getMessages().currencyPlural();
    }

    @Override
    public String currencyNameSingular() {
        return plugin.getMessages().currencySingular();
    }

    // ---- 口座の存在確認 ----

    @Override
    public boolean hasAccount(String playerName) {
        return balances.findUuidByName(playerName).map(balances::hasAccount).orElse(false);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return balances.hasAccount(player.getUniqueId());
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    // ---- 残高照会 ----

    @Override
    public double getBalance(String playerName) {
        UUID uuid = resolveUuid(playerName);
        ensureAccount(uuid, playerName);
        return balances.getBalance(uuid);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        ensureAccount(player.getUniqueId(), player.getName());
        return balances.getBalance(player.getUniqueId());
    }

    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    // ---- 残高チェック ----

    @Override
    public boolean has(String playerName, double amount) {
        return getBalance(playerName) >= amount;
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    // ---- 引き落とし ----

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        UUID uuid = resolveUuid(playerName);
        ensureAccount(uuid, playerName);
        return withdraw(uuid, playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        ensureAccount(player.getUniqueId(), player.getName());
        return withdraw(player.getUniqueId(), player.getName(), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    private EconomyResponse withdraw(UUID uuid, String name, double amount) {
        if (amount < 0) {
            return new EconomyResponse(0, balances.getBalance(uuid), ResponseType.FAILURE, "マイナスの金額は指定できません。");
        }
        double balance = balances.getBalance(uuid);
        if (balance < amount) {
            return new EconomyResponse(0, balance, ResponseType.FAILURE, "所持金が不足しています。");
        }
        double newBalance = balance - amount;
        balances.setBalance(uuid, name, newBalance);
        return new EconomyResponse(amount, newBalance, ResponseType.SUCCESS, null);
    }

    // ---- 入金 ----

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        UUID uuid = resolveUuid(playerName);
        ensureAccount(uuid, playerName);
        return deposit(uuid, playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        ensureAccount(player.getUniqueId(), player.getName());
        return deposit(player.getUniqueId(), player.getName(), amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    private EconomyResponse deposit(UUID uuid, String name, double amount) {
        if (amount < 0) {
            return new EconomyResponse(0, balances.getBalance(uuid), ResponseType.FAILURE, "マイナスの金額は指定できません。");
        }
        double newBalance = balances.getBalance(uuid) + amount;
        balances.setBalance(uuid, name, newBalance);
        return new EconomyResponse(amount, newBalance, ResponseType.SUCCESS, null);
    }

    // ---- 口座作成 ----

    @Override
    public boolean createPlayerAccount(String playerName) {
        return createPlayerAccount(Bukkit.getOfflinePlayer(playerName));
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        if (balances.hasAccount(player.getUniqueId())) {
            return false;
        }
        ensureAccount(player.getUniqueId(), player.getName());
        return true;
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    // ---- 銀行機能 (未対応) ----

    @Override
    public EconomyResponse createBank(String name, String player) {
        return notImplemented();
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return notImplemented();
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return notImplemented();
    }

    @Override
    public List<String> getBanks() {
        return new ArrayList<>();
    }

    private EconomyResponse notImplemented() {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, "銀行機能はサポートしていません。");
    }
}
