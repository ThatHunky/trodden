package dev.thathunky.trodden;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Vanilla random ticks only run near players, and so does this: every pass picks a few random
 * positions around each online player and offers them to the probes. No chunk is ever scanned.
 */
final class Sampler {

    interface Probe {
        void at(Block block);
    }

    private final Trodden plugin;
    private final Set<String> worlds;
    private final int radius;
    private final int perPlayer;
    private final List<Probe> probes = new ArrayList<>();

    Sampler(Trodden plugin, Set<String> worlds, int radius, int perPlayer) {
        this.plugin = plugin;
        this.worlds = worlds;
        this.radius = radius;
        this.perPlayer = perPlayer;
    }

    void add(Probe probe) {
        probes.add(probe);
    }

    void run() {
        if (probes.isEmpty()) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Location origin = player.getLocation();
            if (!worlds.contains(origin.getWorld().getName())) {
                continue;
            }
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            int verticalSpread = Math.max(1, radius / 2);
            for (int i = 0; i < perPlayer; i++) {
                Location at = origin.clone().add(rnd.nextInt(-radius, radius + 1),
                        rnd.nextInt(-verticalSpread, verticalSpread + 1), rnd.nextInt(-radius, radius + 1));
                if (!at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
                    continue;
                }
                Sched.atLocation(plugin, at, () -> {
                    if (!at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
                        return; // unloaded between scheduling and execution — getBlock would force it back in
                    }
                    Block block = at.getBlock();
                    for (Probe probe : probes) {
                        probe.at(block);
                    }
                });
            }
        }
    }
}
