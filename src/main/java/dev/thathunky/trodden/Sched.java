package dev.thathunky.trodden;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/**
 * Scheduling helpers. Paper exposes the Folia scheduler API even when not running Folia, so one
 * code path works on both: block and chunk PDC work always runs on the region owning that chunk.
 */
final class Sched {

    private Sched() {
    }

    static void atChunk(Plugin plugin, World world, int cx, int cz, Runnable task) {
        Bukkit.getRegionScheduler().execute(plugin, world, cx, cz, task);
    }

    static void atLocation(Plugin plugin, Location loc, Runnable task) {
        Bukkit.getRegionScheduler().execute(plugin, loc, task);
    }

    /** Repeating work that only schedules region tasks; it must not touch blocks itself. */
    static void everyTicks(Plugin plugin, long ticks, Runnable task) {
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> task.run(), ticks, ticks);
    }
}
