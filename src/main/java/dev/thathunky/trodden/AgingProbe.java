package dev.thathunky.trodden;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Ages stone blocks that {@link Sampler} hands it: moss near water or under rain, cracks near
 * lava. Runs on the region thread that already owns the block's chunk (see {@link Sched#atLocation})
 * and must never schedule anything itself — it only ever looks at the one block it was given and a
 * small neighbourhood around it, never a chunk or world scan.
 */
final class AgingProbe implements Sampler.Probe {

    private final Trodden plugin;
    private final AgingRules rules;
    private final double chance;

    AgingProbe(Trodden plugin, AgingRules rules, double chance) {
        this.plugin = plugin;
        this.rules = rules;
        this.chance = chance;
    }

    @Override
    public void at(Block block) {
        String type = block.getType().name();
        if (!rules.mayAge(type)) {
            return; // cheap check before touching any neighbour — neighbour search is the expensive part
        }
        Set<AgingRules.Condition> present = EnumSet.noneOf(AgingRules.Condition.class);
        if (near(block, Material.WATER, 2)) {
            present.add(AgingRules.Condition.WATER);
        }
        World world = block.getWorld();
        if (world.hasStorm() && Sky.openAbove(block)) {
            present.add(AgingRules.Condition.RAIN); // hasStorm() is always false in the Nether and the End
        }
        // The environment test is free; the cube scan is not, so it goes second.
        if (world.getEnvironment() == World.Environment.NETHER || near(block, Material.LAVA, 3)) {
            present.add(AgingRules.Condition.LAVA);
        }
        if (present.isEmpty() || ThreadLocalRandom.current().nextDouble() > chance) {
            return;
        }
        String next = rules.aged(type, present);
        if (next != null && plugin.wearAllowedAt(block.getLocation())) {
            block.setType(Material.valueOf(next), true);
        }
    }

    /** Cube scan of radius {@code radius} around one block only — never a chunk or world scan. */
    private static boolean near(Block block, Material material, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (block.getRelative(dx, dy, dz).getType() == material) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
