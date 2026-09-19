package dev.thathunky.trodden;

import java.util.Set;

/**
 * Trampling rules without a server: how many steps to each stage, and how to pack a block's
 * position in a chunk.
 *
 * The counter is cumulative: at {@code coarseAt} steps grass becomes coarse dirt, at {@code pathAt}
 * it becomes a path. Material names are plain strings, so the rules can be checked without Bukkit.
 */
final class WearRules {

    static final String COARSE = "COARSE_DIRT";
    static final String PATH = "DIRT_PATH";
    /** Autumn leaf litter: the one block a footstep scatters, whatever it is lying on. */
    static final String LITTER = "LEAF_LITTER";

    private final Set<String> grass;
    private final int coarseAt;
    private final int pathAt;

    WearRules(Set<String> grass, int coarseAt, int pathAt) {
        if (coarseAt < 1 || pathAt <= coarseAt) {
            throw new IllegalArgumentException("need 1 <= coarse-at < path-at, got " + coarseAt + "/" + pathAt);
        }
        this.grass = Set.copyOf(grass);
        this.coarseAt = coarseAt;
        this.pathAt = pathAt;
    }

    /**
     * Pure decision, no Bukkit: whether a step on the ground below scatters the block above it.
     * Deliberately independent of the ground material — leaf litter is scattered on every step,
     * including steps on a dirt path, which never wears any further and would otherwise keep its
     * litter forever.
     */
    static boolean scatters(String aboveMaterial) {
        return LITTER.equals(aboveMaterial);
    }

    /** Whether this block wears at all (and thus whether it is worth keeping a counter for). */
    boolean wears(String material) {
        return grass.contains(material) || COARSE.equals(material);
    }

    /**
     * The material the block should become after a step brings its counter to {@code count}
     * (already including that step), or null if the block stays as it is.
     */
    String next(String material, int count) {
        if (grass.contains(material)) {
            if (count >= pathAt) {
                return PATH;
            }
            return count >= coarseAt ? COARSE : null;
        }
        if (COARSE.equals(material)) {
            return count >= pathAt ? PATH : null;
        }
        return null;
    }

    /** Coarse dirt that didn't come from trampling (placed by a player) starts at the coarse-dirt threshold. */
    int startCount(String material) {
        return COARSE.equals(material) ? coarseAt : 0;
    }

    int pathAt() {
        return pathAt;
    }

    /** A local position within a chunk -> int: shifted y in the high bits, x and z in 4 low bits each. */
    static int pack(int x, int y, int z) {
        return ((y + 4096) << 8) | ((x & 15) << 4) | (z & 15);
    }

    static int unpackY(int packed) {
        return (packed >>> 8) - 4096;
    }

    static int unpackX(int packed) {
        return (packed >>> 4) & 15;
    }

    static int unpackZ(int packed) {
        return packed & 15;
    }
}
