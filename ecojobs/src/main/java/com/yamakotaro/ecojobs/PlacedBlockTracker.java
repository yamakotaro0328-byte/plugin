package com.yamakotaro.ecojobs;

import org.bukkit.block.Block;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Remembers which block locations a player has placed a block at, so miner/digger/woodcutter
 * don't pay out for placing a block and immediately breaking it again (farming/replanting crops
 * is exempt from this check entirely - see config.yml's comment on the farmer job - since a crop
 * always needs real time to grow back to a harvestable state, so no such exploit exists there).
 * Deliberately in-memory only (not persisted): it only needs to catch someone placing and
 * re-breaking a block in the same server session, and a plain in-memory set avoids depending on
 * any way of tagging an individual non-tile-entity block, which plain blocks don't support.
 */
public class PlacedBlockTracker {

    private record BlockKey(UUID world, int x, int y, int z) {
        private static BlockKey of(Block block) {
            return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }

    private final Set<BlockKey> placed = new HashSet<>();
    /**
     * 建築士の報酬を既に払った座標。{@link #wasPlaced} と違い破壊では消さないのがポイントで、
     * 「置く→壊す(採掘側はトラッカーで無効)→また置く」を繰り返すだけで建築士の報酬だけが
     * 無限に入ってしまうのを防ぐ。{@link #clear} のタイマーでのみ解放される。
     */
    private final Set<BlockKey> buildPaid = new HashSet<>();

    public void markPlaced(Block block) {
        placed.add(BlockKey.of(block));
    }

    /** @return この座標で建築士の報酬を出すのが初めてなら true (2回目以降は false)。 */
    public boolean markBuildPaid(Block block) {
        return buildPaid.add(BlockKey.of(block));
    }

    /**
     * @return true if this exact location was marked as player-placed, clearing the mark either
     * way (the block is being broken, so its "placed" state is moot afterwards regardless).
     */
    public boolean wasPlaced(Block block) {
        return placed.remove(BlockKey.of(block));
    }

    /**
     * A placed block only ever leaves this set via {@link #wasPlaced}, i.e. an actual
     * BlockBreakEvent - a block removed any other way (explosion, piston, water/lava flow, world
     * edit, ...) leaves a stale entry behind forever. Since the tracker only needs to catch a
     * place-then-immediately-rebreak within roughly the same play session anyway, EcoJobsPlugin
     * calls this on a timer to bound memory growth on long-uptime servers instead of tracking
     * every possible block-removal event.
     */
    public void clear() {
        placed.clear();
        buildPaid.clear();
    }
}
