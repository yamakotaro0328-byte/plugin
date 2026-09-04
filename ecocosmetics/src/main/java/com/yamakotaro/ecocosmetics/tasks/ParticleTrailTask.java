package com.yamakotaro.ecocosmetics.tasks;

import com.yamakotaro.ecocosmetics.CosmeticDefinition;
import com.yamakotaro.ecocosmetics.CosmeticManager;
import com.yamakotaro.ecocosmetics.EcoCosmeticsPlugin;
import org.bukkit.entity.Player;

/** オンラインの全プレイヤーについて、装備中のPARTICLEトレイルを一定間隔で足元に再生する。 */
public class ParticleTrailTask implements Runnable {

    private final EcoCosmeticsPlugin plugin;

    public ParticleTrailTask(EcoCosmeticsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        CosmeticManager manager = plugin.getCosmeticManager();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            CosmeticDefinition particleCosmetic = manager.getEquippedParticle(player.getUniqueId());
            if (particleCosmetic == null || particleCosmetic.particle() == null) {
                continue;
            }
            player.getWorld().spawnParticle(particleCosmetic.particle(), player.getLocation().add(0, 0.1, 0), 4, 0.2, 0.05, 0.2, 0.01);
        }
    }
}
