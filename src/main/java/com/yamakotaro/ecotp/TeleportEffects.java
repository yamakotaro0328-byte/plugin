package com.yamakotaro.ecotp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * テレポートの演出。待機中は足元から渦巻くパーティクルと上がっていく効果音・タイトルの
 * カウントダウン、出発地点と到着地点ではパーティクルの爆発と広がるリング。
 * config.yml の teleport-safety.play-effects (全部) / fancy-effects (待機中の演出) で無効化できる。
 */
final class TeleportEffects {

    private final EcoTpPlugin plugin;

    TeleportEffects(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("teleport-safety.play-effects", true);
    }

    private boolean fancy() {
        return enabled() && plugin.getConfig().getBoolean("teleport-safety.fancy-effects", true);
    }

    /** 待機中の演出を開始する。返したタスクはカウントダウン終了/キャンセル時に止めること。null なら演出なし。 */
    BukkitTask startCountdown(Player player, String description, int totalTicks) {
        if (!fancy()) {
            return null;
        }
        showCountdownTitle(player, description, totalTicks / 20);
        return Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int elapsed;

            @Override
            public void run() {
                elapsed += 2;
                if (!player.isOnline() || elapsed > totalTicks) {
                    return;
                }
                double progress = (double) elapsed / totalTicks;
                Location base = player.getLocation();
                World world = base.getWorld();
                // 2本の螺旋。待ち時間が進むほど速く回り、粒も増える。
                double angle = elapsed * (0.25 + progress * 0.5);
                double height = (elapsed % 40) / 20.0;
                for (int strand = 0; strand < 2; strand++) {
                    double a = angle + strand * Math.PI;
                    Location point = base.clone().add(Math.cos(a) * 0.8, height, Math.sin(a) * 0.8);
                    world.spawnParticle(Particle.ENCHANT, point, 3, 0.02, 0.02, 0.02, 0.0);
                    world.spawnParticle(Particle.PORTAL, point, 2, 0.05, 0.05, 0.05, 0.05);
                    if (progress > 0.6) {
                        world.spawnParticle(Particle.END_ROD, point, 1, 0.0, 0.0, 0.0, 0.0);
                    }
                }
                if (elapsed % 20 == 0) {
                    int remaining = (totalTicks - elapsed) / 20;
                    // 1秒ごとに音程が上がっていく。
                    player.playSound(base, Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, (float) (0.6 + progress * 1.2));
                    if (remaining > 0) {
                        showCountdownTitle(player, description, remaining);
                    }
                }
            }
        }, 2L, 2L);
    }

    private void showCountdownTitle(Player player, String description, int seconds) {
        player.sendTitle(plugin.getMessages().get("teleport-effects.countdown-title", "seconds", String.valueOf(seconds)),
                plugin.getMessages().get("teleport-effects.countdown-subtitle", "description", description), 0, 25, 5);
    }

    /** キャンセル時の演出 (煙と消える音)。 */
    void cancelled(Player player) {
        if (!fancy()) {
            return;
        }
        Location location = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(Particle.SMOKE, location, 30, 0.4, 0.6, 0.4, 0.02);
        player.playSound(location, Sound.BLOCK_FIRE_EXTINGUISH, 0.6f, 1.2f);
        player.resetTitle();
    }

    /** 出発地点の演出。周りの人にも見える/聞こえる。 */
    void departure(Location from) {
        if (!enabled() || from.getWorld() == null) {
            return;
        }
        World world = from.getWorld();
        Location center = from.clone().add(0, 1, 0);
        world.spawnParticle(Particle.REVERSE_PORTAL, center, 80, 0.3, 0.8, 0.3, 0.2);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, center, 30, 0.3, 0.5, 0.3, 0.3);
        world.playSound(center, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);
    }

    /** 到着地点の演出。実際に teleport した直後に呼ぶ。 */
    void arrival(Player player) {
        if (!enabled()) {
            return;
        }
        Location base = player.getLocation();
        World world = base.getWorld();
        Location center = base.clone().add(0, 1, 0);
        world.playSound(center, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
        world.spawnParticle(Particle.PORTAL, center, 60, 0.5, 1.0, 0.5, 0.3);
        if (!fancy()) {
            return;
        }
        player.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
        player.playSound(center, Sound.ENTITY_PLAYER_LEVELUP, 0.4f, 1.6f);
        world.spawnParticle(Particle.FIREWORK, center, 40, 0.2, 0.4, 0.2, 0.15);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, center, 30, 0.3, 0.5, 0.3, 0.3);
        player.sendTitle(plugin.getMessages().get("teleport-effects.arrived-title"),
                plugin.getMessages().get("teleport-effects.arrived-subtitle"), 0, 30, 10);
        // 足元から広がる光のリング。
        Bukkit.getScheduler().runTaskTimer(plugin, new java.util.function.Consumer<BukkitTask>() {
            int step;

            @Override
            public void accept(BukkitTask task) {
                if (++step > 8) {
                    task.cancel();
                    return;
                }
                double radius = step * 0.35;
                int points = 12 + step * 4;
                for (int i = 0; i < points; i++) {
                    double a = 2 * Math.PI * i / points;
                    world.spawnParticle(Particle.END_ROD, base.clone().add(Math.cos(a) * radius, 0.1, Math.sin(a) * radius),
                            1, 0, 0, 0, 0);
                }
            }
        }, 0L, 2L);
    }
}
