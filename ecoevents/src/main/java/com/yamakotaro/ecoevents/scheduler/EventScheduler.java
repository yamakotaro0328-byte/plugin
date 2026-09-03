package com.yamakotaro.ecoevents.scheduler;

import com.yamakotaro.ecoevents.event.EventManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;

/** Fires a random enabled event after a random delay between min/max interval, then reschedules. */
public class EventScheduler {

    private final JavaPlugin plugin;
    private final EventManager eventManager;
    private final Random random = new Random();
    private long nextFireAtMillis;

    public EventScheduler(JavaPlugin plugin, EventManager eventManager) {
        this.plugin = plugin;
        this.eventManager = eventManager;
        scheduleNext();
    }

    private void scheduleNext() {
        int minMinutes = Math.max(1, plugin.getConfig().getInt("schedule.min-interval-minutes", 8));
        int maxMinutes = Math.max(minMinutes, plugin.getConfig().getInt("schedule.max-interval-minutes", 20));
        int delayMinutes = minMinutes + random.nextInt(maxMinutes - minMinutes + 1);
        nextFireAtMillis = System.currentTimeMillis() + delayMinutes * 60_000L;
    }

    public void tick() {
        if (System.currentTimeMillis() < nextFireAtMillis) {
            return;
        }
        eventManager.fireRandomEvent();
        scheduleNext();
    }
}
