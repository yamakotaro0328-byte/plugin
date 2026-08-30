package com.yamakotaro.serverkit.claims;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Towny/Lands(導入されていれば)の領地内でServerKitのクレームを作れないようにする。
 * どちらもコンパイル時の依存を持たない(未導入のサーバーでNoClassDefFoundErrorを
 * 起こさないため、NuVotifier検出と同じ理由でリフレクションのみを使う)。
 * 各プラグインのAPIが変わっていて呼び出しに失敗した場合は、その連携だけを
 * 諦める(fail-open) — ServerKitのクレーム機能自体は壊さない。
 */
public class TerritoryGuard {

    private final Plugin plugin;

    private boolean townyChecked;
    private Object townyApi;
    private Method townyIsWildernessMethod;

    private boolean landsChecked;
    private Object landsIntegration;
    private Method landsGetAreaMethod;

    public TerritoryGuard(Plugin plugin) {
        this.plugin = plugin;
    }

    /** @return 既にTowny/Landsの領地になっている場合は true (=クレーム不可)。それぞれ個別に有効/無効にできる。 */
    public boolean isExternallyClaimed(Location location, boolean checkTowny, boolean checkLands) {
        if (checkTowny && isTownyClaimed(location)) {
            return true;
        }
        return checkLands && isLandsClaimed(location);
    }

    public boolean isExternallyClaimed(World world, int x, int z, boolean checkTowny, boolean checkLands) {
        return isExternallyClaimed(new Location(world, x, 64, z), checkTowny, checkLands);
    }

    private boolean isTownyClaimed(Location location) {
        if (!townyChecked) {
            townyChecked = true;
            try {
                Class<?> apiClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
                Method getInstance = apiClass.getMethod("getInstance");
                this.townyApi = getInstance.invoke(null);
                this.townyIsWildernessMethod = apiClass.getMethod("isWilderness", Location.class);
                plugin.getLogger().info("Towny detected: ServerKit claims will avoid Towny territory.");
            } catch (ReflectiveOperationException e) {
                this.townyApi = null;
            }
        }
        if (townyApi == null || townyIsWildernessMethod == null) {
            return false;
        }
        try {
            boolean wilderness = (boolean) townyIsWildernessMethod.invoke(townyApi, location);
            return !wilderness;
        } catch (ReflectiveOperationException | ClassCastException e) {
            plugin.getLogger().log(Level.WARNING, "Towny integration broke while checking a location; treating it as unclaimed for this check.", e);
            return false;
        }
    }

    private boolean isLandsClaimed(Location location) {
        if (!landsChecked) {
            landsChecked = true;
            try {
                Class<?> integrationClass = Class.forName("me.angeschossen.lands.api.LandsIntegration");
                Constructor<?> constructor = integrationClass.getConstructor(Plugin.class, boolean.class);
                this.landsIntegration = constructor.newInstance(plugin, false);
                this.landsGetAreaMethod = integrationClass.getMethod("getArea", Location.class);
                plugin.getLogger().info("Lands detected: ServerKit claims will avoid Lands territory.");
            } catch (ReflectiveOperationException e) {
                this.landsIntegration = null;
            }
        }
        if (landsIntegration == null || landsGetAreaMethod == null) {
            return false;
        }
        try {
            Object area = landsGetAreaMethod.invoke(landsIntegration, location);
            return area != null;
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().log(Level.WARNING, "Lands integration broke while checking a location; treating it as unclaimed for this check.", e);
            return false;
        }
    }
}
