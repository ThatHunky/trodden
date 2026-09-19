package dev.thathunky.trodden;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 * Whether a block's column is open to the sky, shared by every probe that cares whether rain can
 * reach a block — {@code getHighestBlockYAt}'s exact semantics (top solid block vs. first free
 * space above it) were disputed between two probes that used it with a one-block offset between
 * them, so this replaces both with a single unambiguous test.
 */
final class Sky {

    private Sky() {
    }

    /**
     * True if sky light reaches full strength (15) directly above {@code block}. Sky light is
     * independent of time of day and of any light-emitting blocks, so this is a pure "is the
     * column above this block open" test. It deliberately treats a leaf canopy as cover — leaves
     * block sky light — because rain should not wet the ground under a tree.
     */
    static boolean openAbove(Block block) {
        return block.getRelative(BlockFace.UP).getLightFromSky() == 15;
    }
}
