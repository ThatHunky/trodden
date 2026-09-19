package dev.thathunky.trodden;

import java.util.Map;
import java.util.Set;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 * Walks worn blocks back to grass. It only ever visits positions this plugin recorded, in chunks
 * that are already loaded — never the world at large.
 */
final class RegrowthTask {

    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    private final Trodden plugin;
    private final RegrowthRules rules;
    private final Seasons seasons;
    private final WearStore store;
    private final Set<String> worlds;

    RegrowthTask(Trodden plugin, RegrowthRules rules, Seasons seasons, WearStore store, Set<String> worlds) {
        this.plugin = plugin;
        this.rules = rules;
        this.seasons = seasons;
        this.store = store;
        this.worlds = worlds;
    }

    /** Scheduled globally: hands every tracked loaded chunk to its own region. */
    void sweepLoaded() {
        for (World world : plugin.getServer().getWorlds()) {
            if (!worlds.contains(world.getName())) {
                continue;
            }
            for (long chunkKey : store.trackedChunks(world)) {
                int cx = (int) chunkKey;
                int cz = (int) (chunkKey >> 32);
                if (world.isChunkLoaded(cx, cz)) {
                    // The chunk can unload between scheduling and execution, so re-check inside
                    // the task: getChunkAt would otherwise force it back in. Same pattern as
                    // WearStore.flushAll.
                    Sched.atChunk(plugin, world, cx, cz, () -> {
                        if (world.isChunkLoaded(cx, cz)) {
                            regrow(world.getChunkAt(cx, cz));
                        }
                    });
                }
            }
        }
    }

    /** A chunk came back: apply everything that should have happened while it slept. */
    void catchUp(Chunk chunk) {
        if (worlds.contains(chunk.getWorld().getName())) {
            Sched.atChunk(plugin, chunk.getWorld(), chunk.getX(), chunk.getZ(), () -> regrow(chunk));
        }
    }

    private void regrow(Chunk chunk) {
        Season season = seasons.at(java.time.LocalDate.now());
        int today = Days.today();
        for (Map.Entry<Integer, WearStore.Entry> e : Map.copyOf(store.entries(chunk)).entrySet()) {
            int packed = e.getKey();
            WearStore.Entry entry = e.getValue();
            Block block = chunk.getBlock(WearRules.unpackX(packed), WearRules.unpackY(packed), WearRules.unpackZ(packed));
            if (entry.count() == WearStore.MUD_MARK) {
                // Same reasoning as WearListener's step guard: only skip while the block there is
                // still mud. If something else took its place (broken, replaced, built over) the
                // mark is stale — drop it instead of skipping it forever, so it neither sits dead
                // in the chunk's PDC nor gets mistaken for ours if mud ever appears there again.
                if (MudProbe.keepsMark(block.getType().name(), entry.count())) {
                    continue; // mud dries by weather, not by time
                }
                store.remove(chunk, packed);
                continue;
            }
            int stages = rules.stagesBack(today - entry.day(), season);
            if (stages <= 0) {
                continue;
            }
            if (!plugin.wearAllowedAt(block.getLocation())) {
                continue; // owner turned wear off for this claim: the ground stays as they left it
            }
            applyStages(chunk, packed, block, stages, today);
        }
    }

    private void applyStages(Chunk chunk, int packed, Block block, int stages, int today) {
        for (int i = 0; i < stages; i++) {
            String previous = rules.previous(block.getType().name());
            if (previous == null) {
                // Most often a grass block that never reached coarse dirt: its partial counter is
                // simply dropped after one idle threshold. It also covers a block somebody has
                // since replaced with something that does not regrow at all.
                store.remove(chunk, packed);
                return;
            }
            if (RegrowthRules.GRASS_MARKER.equals(previous)) {
                Material grass = neighbourGrass(block);
                if (grass == null || block.getRelative(BlockFace.UP).getLightFromSky() < 4) {
                    return; // nothing to spread from: it stays coarse dirt, and the entry keeps its old day
                }
                block.setType(grass, true);
                store.remove(chunk, packed);
                return;
            }
            block.setType(Material.valueOf(previous), true);
            store.put(chunk, packed, 0, today);
        }
    }

    /** Grass-like block next to this one, so podzol restores podzol and grass restores grass. */
    private Material neighbourGrass(Block block) {
        for (BlockFace face : SIDES) {
            Material type = block.getRelative(face).getType();
            if (type == Material.GRASS_BLOCK || type == Material.PODZOL || type == Material.MYCELIUM) {
                return type;
            }
        }
        return null;
    }
}
