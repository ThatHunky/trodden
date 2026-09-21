package dev.thathunky.trodden;

import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 * Settles autumn leaf litter ({@code LEAF_LITTER}, vanilla since 1.21.5, so Bedrock players see the
 * same block through Geyser) under tree canopies, and clears it away again in spring. Runs on the
 * region thread that already owns the block's chunk (see {@link Sched#atLocation}) and must never
 * schedule anything itself — it only ever looks at the one block it was given and a small
 * neighbourhood around it, never a chunk or world scan.
 *
 * <p>The block {@link Sampler} hands the probe is treated as the space litter would occupy — the
 * same convention {@link WearListener#groundUnder} uses the other way around (feet block, ground
 * below) — so the ground here is one block below what the probe was given.
 *
 * <p>Only registered when the server has the block ({@link Compat#LEAF_LITTER}); on 1.21.4 it is not.
 */
final class LitterProbe implements Sampler.Probe {

    /** Walk at most this many blocks upward looking for leaves before giving up. */
    private static final int LEAVES_SEARCH_HEIGHT = 6;
    /** Horizontal radius (same Y only) to count existing litter in — a flat square, not a cube. */
    private static final int NEARBY_RADIUS = 2;

    private final Trodden plugin;
    private final Seasons seasons;
    private final LitterRules rules;
    private final double chance;

    LitterProbe(Trodden plugin, Seasons seasons, LitterRules rules, double chance) {
        this.plugin = plugin;
        this.seasons = seasons;
        this.rules = rules;
        this.chance = chance;
    }

    @Override
    public void at(Block above) {
        Season season = seasons.at(LocalDate.now());
        if (rules.shouldClear(season)) {
            clear(above);
            return;
        }
        settle(above, season);
    }

    private void settle(Block above, Season season) {
        if (!rules.inSeason(season) || above.getType() != Material.AIR) {
            return; // cheap checks before touching any neighbour — the neighbour scans are the expensive part
        }
        Block ground = above.getRelative(BlockFace.DOWN);
        if (!rules.canLitter(ground.getType().name(), above.getType().name(), leavesAbove(above), nearbyLitter(above), season)) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() > chance || !plugin.wearAllowedAt(above.getLocation())) {
            return;
        }
        above.setType(Compat.LEAF_LITTER, true);
    }

    /** Spring: with the same configured chance, remove litter this probe lands on. */
    private void clear(Block above) {
        if (!Compat.is(above.getType(), Compat.LEAF_LITTER)) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() > chance || !plugin.wearAllowedAt(above.getLocation())) {
            return;
        }
        above.setType(Material.AIR, true);
    }

    /** Tag.LEAVES within {@link #LEAVES_SEARCH_HEIGHT} blocks upward; stops at the first solid block. */
    private static boolean leavesAbove(Block above) {
        Block cursor = above;
        for (int i = 0; i < LEAVES_SEARCH_HEIGHT; i++) {
            Material type = cursor.getType();
            if (Tag.LEAVES.isTagged(type)) {
                return true;
            }
            if (type.isSolid()) {
                return false; // a roof blocks litter from ever settling below it
            }
            cursor = cursor.getRelative(BlockFace.UP);
        }
        return false;
    }

    /** LEAF_LITTER count in a {@value #NEARBY_RADIUS}-block square around the block, same Y only, centre excluded. */
    private static int nearbyLitter(Block above) {
        int count = 0;
        for (int dx = -NEARBY_RADIUS; dx <= NEARBY_RADIUS; dx++) {
            for (int dz = -NEARBY_RADIUS; dz <= NEARBY_RADIUS; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                if (Compat.is(above.getRelative(dx, 0, dz).getType(), Compat.LEAF_LITTER)) {
                    count++;
                }
            }
        }
        return count;
    }
}
