package com.yamakotaro.ecojobs;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * Temporary, in-memory pay-rate events: a server-wide "double XP weekend" or a "2x money for
 * miners" promo, started/stopped by an admin (see /jobs booster and the admin GUI). Deliberately
 * not persisted - an event that happened to be running when the server restarted resuming
 * indefinitely would be far more surprising than it simply ending early.
 */
public class BoosterManager {

    public static final String GLOBAL_SCOPE = "all";

    public record ActiveBooster(String scope, double moneyMultiplier, double xpMultiplier, long expiresAtMillis, String activatedBy) {
    }

    private final Map<String, ActiveBooster> boosters = new HashMap<>();

    public void start(String scope, double moneyMultiplier, double xpMultiplier, long durationMillis, String activatedBy) {
        boosters.put(scope, new ActiveBooster(scope, moneyMultiplier, xpMultiplier,
                System.currentTimeMillis() + durationMillis, activatedBy));
    }

    public boolean stop(String scope) {
        return boosters.remove(scope) != null;
    }

    public int stopAll() {
        int count = active().size();
        boosters.clear();
        return count;
    }

    /**
     * The booster active for exactly this scope (not combined with the global one) - used by the
     * admin GUI to show a job's own booster separately from the global one.
     */
    public ActiveBooster getActiveBooster(String scope) {
        return active(scope);
    }

    public double moneyMultiplierFor(String jobId) {
        return combined(jobId, ActiveBooster::moneyMultiplier);
    }

    public double xpMultiplierFor(String jobId) {
        return combined(jobId, ActiveBooster::xpMultiplier);
    }

    /**
     * The global booster and a job-specific one stack multiplicatively, so a "2x XP weekend"
     * still layers on top of a "3x XP for miners" promo instead of one simply overriding the
     * other.
     */
    private double combined(String jobId, ToDoubleFunction<ActiveBooster> extractor) {
        double result = 1.0;
        ActiveBooster global = active(GLOBAL_SCOPE);
        if (global != null) {
            result *= extractor.applyAsDouble(global);
        }
        if (!jobId.equals(GLOBAL_SCOPE)) {
            ActiveBooster jobBooster = active(jobId);
            if (jobBooster != null) {
                result *= extractor.applyAsDouble(jobBooster);
            }
        }
        return result;
    }

    private ActiveBooster active(String scope) {
        ActiveBooster booster = boosters.get(scope);
        if (booster == null) {
            return null;
        }
        if (System.currentTimeMillis() >= booster.expiresAtMillis()) {
            boosters.remove(scope);
            return null;
        }
        return booster;
    }

    /**
     * Every still-active booster, purging any that expired since the last check - used by
     * /jobs booster list and the admin GUI.
     */
    public Collection<ActiveBooster> active() {
        boosters.entrySet().removeIf(entry -> System.currentTimeMillis() >= entry.getValue().expiresAtMillis());
        return boosters.values();
    }
}
