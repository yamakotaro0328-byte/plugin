package com.yamakotaro.ecojobs.listeners;

import com.yamakotaro.ecojobs.EcoJobsPlugin;
import com.yamakotaro.ecojobs.PlayerJobManager;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * EvenMoreFish(導入されていれば)で釣れたカスタム魚に、レア度別の報酬を出す。
 *
 * EMFはコンパイル時の依存を持たない(未導入のサーバーでNoClassDefFoundErrorを起こさないため、
 * ServerKitのTerritoryGuardによるTowny/Lands検出や、EcoTPのNuVotifier検出と同じ理由で
 * リフレクションのみを使う)。EMFのイベントクラスはバージョンによって場所が変わるため候補を
 * 順に探し、どれも見つからなければ何もしない。API形状が想定と違って呼び出しに失敗した場合も、
 * この連携だけを諦める(fail-open) — EcoJobs本体の釣り報酬は通常どおり動く。
 */
public class EvenMoreFishBridge implements Listener {

    /** EMFのバージョン差を吸収するための候補。先に見つかったものを使う。 */
    private static final String[] EVENT_CLASS_CANDIDATES = {
            "com.oheers.fish.api.EMFFishEvent",
            "com.oheers.fish.api.event.EMFFishEvent",
            "com.oheers.fish.api.event.FishCatchEvent",
    };
    private static final String[] PLAYER_ACCESSORS = {"getPlayer", "getUser"};
    private static final String[] FISH_ACCESSORS = {"getFish", "getCaughtFish"};
    private static final String[] RARITY_NAME_ACCESSORS = {"getValue", "getName", "getIdentifier"};
    /** EMFが釣り上げたアイテムに付ける名前空間。バニラ側の二重支払いを避けるために見る。 */
    private static final String EMF_NAMESPACE = "evenmorefish";
    /** EMF経由で支払った直後は、バニラのPlayerFishEvent側をこの時間だけ無視する。 */
    private static final long RECENT_CATCH_MILLIS = 1_000L;

    private final EcoJobsPlugin plugin;
    private final PlayerJobManager jobs;
    private final Map<UUID, Long> recentCatches = new HashMap<>();
    private boolean warned;

    public EvenMoreFishBridge(EcoJobsPlugin plugin, PlayerJobManager jobs) {
        this.plugin = plugin;
        this.jobs = jobs;
    }

    /** EMFが入っていればイベントを購読する。入っていなければ何もしない。 */
    public void register() {
        if (plugin.getServer().getPluginManager().getPlugin("EvenMoreFish") == null) {
            return;
        }
        Class<? extends Event> eventClass = findEventClass();
        if (eventClass == null) {
            plugin.getLogger().warning("EvenMoreFish is installed, but none of its known event classes were found - "
                    + "custom fish will fall back to the normal fisherman/treasurehunter rewards.");
            return;
        }
        plugin.getServer().getPluginManager().registerEvent(eventClass, this, EventPriority.NORMAL,
                (listener, event) -> handleCatch(event), plugin, true);
        plugin.getLogger().info("EvenMoreFish detected: custom fish now pay the fisherman job by rarity ("
                + eventClass.getName() + ").");
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Event> findEventClass() {
        for (String candidate : EVENT_CLASS_CANDIDATES) {
            try {
                Class<?> found = Class.forName(candidate);
                if (Event.class.isAssignableFrom(found)) {
                    return (Class<? extends Event>) found;
                }
            } catch (ClassNotFoundException ignored) {
                // 次の候補へ
            }
        }
        return null;
    }

    private void handleCatch(Event event) {
        try {
            Object rawPlayer = invokeFirst(event, PLAYER_ACCESSORS);
            Object fish = invokeFirst(event, FISH_ACCESSORS);
            if (!(rawPlayer instanceof Player player) || fish == null) {
                warnOnce("EvenMoreFish's event did not expose a player and a fish where expected", null);
                return;
            }
            String rarity = rarityNameOf(fish);
            recentCatches.put(player.getUniqueId(), System.currentTimeMillis());
            jobs.reward(player, "fisherman", "catch-emf-fish", rarity == null ? "default" : rarity, 1);
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce("EvenMoreFish integration failed while handling a catch", e);
        }
    }

    /** レア度名(例: "common"/"rare"/"legendary")。取れなければ null。 */
    private String rarityNameOf(Object fish) throws ReflectiveOperationException {
        Object rarity = invokeFirst(fish, "getRarity");
        if (rarity == null) {
            return null;
        }
        Object name = invokeFirst(rarity, RARITY_NAME_ACCESSORS);
        String value = name instanceof String text ? text : rarity.toString();
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    /** 引数なしメソッドを名前の候補順に呼び、最初に成功したものの戻り値を返す。 */
    private Object invokeFirst(Object target, String... methodNames) throws ReflectiveOperationException {
        for (String methodName : methodNames) {
            try {
                Method method = target.getClass().getMethod(methodName);
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
                // 次の候補へ
            }
        }
        return null;
    }

    /**
     * @return このプレイヤーの釣果を直前にEMF側で処理済みなら true。バニラの PlayerFishEvent 側は
     * これを見て支払いを飛ばし、1回の釣りで二重に支払われないようにする。
     */
    public boolean handledRecently(Player player) {
        Long at = recentCatches.get(player.getUniqueId());
        if (at == null) {
            return false;
        }
        if (System.currentTimeMillis() - at >= RECENT_CATCH_MILLIS) {
            recentCatches.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    /**
     * @return EMFが付けたタグを持つアイテムなら true。イベントの発火順に依存しない保険として、
     * バニラ側の支払いを飛ばす判断に使う。
     */
    public boolean isEvenMoreFishItem(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        for (NamespacedKey key : meta.getPersistentDataContainer().getKeys()) {
            if (EMF_NAMESPACE.equalsIgnoreCase(key.getNamespace())) {
                return true;
            }
        }
        return false;
    }

    private void warnOnce(String message, Throwable error) {
        if (warned) {
            return;
        }
        warned = true;
        if (error != null) {
            plugin.getLogger().log(Level.WARNING, message + " - giving up on the integration for this session.", error);
        } else {
            plugin.getLogger().warning(message + " - giving up on the integration for this session.");
        }
    }
}
